"""Gemini Vision + TTS service — Python port of frontend/services/geminiService.ts."""

import asyncio
import base64
import json
import os

from google import genai
from google.genai import types


_client: genai.Client | None = None


def _get_client() -> genai.Client:
    global _client
    if _client is None:
        api_key = os.environ.get("GEMINI_API_KEY", "")
        if not api_key:
            raise RuntimeError("GEMINI_API_KEY environment variable is not set")
        _client = genai.Client(api_key=api_key)
    return _client


class GeminiAnalysisResult:
    """Result from Gemini vision analysis."""

    def __init__(self, feedback_text: str, metric_name: str, metric_value: str):
        self.feedback_text = feedback_text
        self.metric_name = metric_name
        self.metric_value = metric_value

    def to_dict(self) -> dict:
        return {
            "feedback_text": self.feedback_text,
            "metric_name": self.metric_name,
            "metric_value": self.metric_value,
        }


VISION_PROMPT = """You are a professional, direct, and elite Tennis Coach.
Analyze this short video clip of a tennis swing.

Identify the ONE single biggest flaw that the player needs to fix immediately.
Do not give a list. Give one specific correction.

Return the result in JSON format with these fields:
- feedback_text: A direct, spoken-style command to the player (max 15 words). e.g., "You are muscling it. Drop the racket head sooner."
- metric_name: A technical term for the issue. e.g., "Contact Point", "Racket Drop", "Follow Through".
- metric_value: The specific observation. e.g., "Too Close to Body", "Late", "Abbreviated"."""


async def analyze_video_with_gemini(
    video_bytes: bytes, mime_type: str = "video/mp4"
) -> GeminiAnalysisResult:
    """Send video to Gemini Vision and get coaching feedback.

    Runs the blocking SDK call in a thread to stay async-friendly.
    """
    client = _get_client()
    video_b64 = base64.standard_b64encode(video_bytes).decode("ascii")

    def _call() -> str:
        response = client.models.generate_content(
            model="gemini-3-flash-preview",
            contents=types.Content(
                parts=[
                    types.Part(
                        inline_data=types.Blob(
                            mime_type=mime_type,
                            data=video_b64,
                        )
                    ),
                    types.Part(text=VISION_PROMPT),
                ]
            ),
            config=types.GenerateContentConfig(
                response_mime_type="application/json",
            ),
        )
        return response.text or ""

    raw = await asyncio.to_thread(_call)

    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        raise RuntimeError(f"Gemini returned non-JSON response: {raw[:200]}")

    return GeminiAnalysisResult(
        feedback_text=data.get("feedback_text", ""),
        metric_name=data.get("metric_name", ""),
        metric_value=data.get("metric_value", ""),
    )


async def generate_tts_audio(text: str) -> str | None:
    """Generate TTS audio via Gemini and return base64-encoded audio, or None on failure."""
    try:
        client = _get_client()

        def _call() -> str | None:
            response = client.models.generate_content(
                model="gemini-2.5-flash-preview-tts",
                contents=types.Content(
                    parts=[types.Part(text=text)]
                ),
                config=types.GenerateContentConfig(
                    response_modalities=["AUDIO"],
                    speech_config=types.SpeechConfig(
                        voice_config=types.VoiceConfig(
                            prebuilt_voice_config=types.PrebuiltVoiceConfig(
                                voice_name="Puck"
                            )
                        )
                    ),
                ),
            )
            if (
                response.candidates
                and response.candidates[0].content
                and response.candidates[0].content.parts
            ):
                part = response.candidates[0].content.parts[0]
                if part.inline_data and part.inline_data.data:
                    return part.inline_data.data
            return None

        return await asyncio.to_thread(_call)
    except Exception:
        # Non-blocking — TTS failure shouldn't break the pipeline
        return None
