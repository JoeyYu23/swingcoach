"""LM Studio vision service — local on-device inference via Qwen2.5-VL.

Extracts frames from video, sends them as base64 images to
LM Studio's OpenAI-compatible /v1/chat/completions endpoint.
Returns the same GeminiAnalysisResult type used by gemini_service.py.
"""

import asyncio
import base64
import io
import json
import logging
import os
from typing import List

import cv2
import httpx
import numpy as np

from gemini_service import GeminiAnalysisResult, VISION_PROMPT

logger = logging.getLogger(__name__)

# Configuration via environment variables
LMSTUDIO_URL = os.environ.get("LMSTUDIO_URL", "http://localhost:1234")
LMSTUDIO_MODEL = os.environ.get("LMSTUDIO_MODEL", "qwen2.5-vl-7b-instruct")
NUM_FRAMES = int(os.environ.get("NUM_FRAMES", "6"))
MAX_FRAME_DIM = int(os.environ.get("MAX_FRAME_DIM", "512"))


def _strip_code_fences(text: str) -> str:
    """Remove markdown code fences (```json ... ```) wrapping from LLM output."""
    stripped = text.strip()
    if stripped.startswith("```"):
        # Remove opening fence (```json or ```)
        first_newline = stripped.find("\n")
        if first_newline != -1:
            stripped = stripped[first_newline + 1:]
        # Remove closing fence
        if stripped.rstrip().endswith("```"):
            stripped = stripped.rstrip()[:-3].rstrip()
    return stripped


def extract_frames(
    video_bytes: bytes,
    num_frames: int = NUM_FRAMES,
    max_dim: int = MAX_FRAME_DIM,
) -> List[str]:
    """Extract evenly-spaced frames from video, resize, and encode as base64 JPEG.

    CPU-bound — should be called via run_in_executor.

    Args:
        video_bytes: Raw video file bytes.
        num_frames: Number of frames to extract.
        max_dim: Maximum dimension (width or height) for resized frames.

    Returns:
        List of base64-encoded JPEG strings.
    """
    # Decode video from bytes buffer
    arr = np.frombuffer(video_bytes, dtype=np.uint8)
    # OpenCV can't read from memory directly; write to a temp buffer via VideoCapture
    import tempfile
    with tempfile.NamedTemporaryFile(suffix=".mp4", delete=False) as tmp:
        tmp.write(video_bytes)
        tmp_path = tmp.name

    try:
        cap = cv2.VideoCapture(tmp_path)
        if not cap.isOpened():
            raise RuntimeError("Could not open video for frame extraction")

        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        if total_frames <= 0:
            raise RuntimeError("Video has no frames")

        # Pick evenly-spaced frame indices
        actual_count = min(num_frames, total_frames)
        if actual_count == 1:
            indices = [0]
        else:
            indices = [
                int(i * (total_frames - 1) / (actual_count - 1))
                for i in range(actual_count)
            ]

        frames_b64: List[str] = []
        for idx in indices:
            cap.set(cv2.CAP_PROP_POS_FRAMES, idx)
            ret, frame = cap.read()
            if not ret:
                continue

            # Resize keeping aspect ratio
            h, w = frame.shape[:2]
            if max(h, w) > max_dim:
                scale = max_dim / max(h, w)
                new_w = int(w * scale)
                new_h = int(h * scale)
                frame = cv2.resize(frame, (new_w, new_h), interpolation=cv2.INTER_AREA)

            # Encode to JPEG
            success, buf = cv2.imencode(".jpg", frame, [cv2.IMWRITE_JPEG_QUALITY, 85])
            if success:
                frames_b64.append(base64.b64encode(buf.tobytes()).decode("ascii"))

        cap.release()
        return frames_b64
    finally:
        os.unlink(tmp_path)


async def analyze_video_with_lmstudio(
    video_bytes: bytes, mime_type: str = "video/mp4"
) -> GeminiAnalysisResult:
    """Send video frames to LM Studio for vision analysis.

    Extracts frames in a thread pool, then POSTs to the OpenAI-compatible endpoint.
    """
    loop = asyncio.get_event_loop()
    frames_b64 = await loop.run_in_executor(None, extract_frames, video_bytes)

    if not frames_b64:
        raise RuntimeError("No frames could be extracted from video")

    # Build multi-image message for OpenAI-compatible API
    content: list = []
    for i, b64 in enumerate(frames_b64):
        content.append({
            "type": "image_url",
            "image_url": {"url": f"data:image/jpeg;base64,{b64}"},
        })
    content.append({"type": "text", "text": VISION_PROMPT})

    payload = {
        "model": LMSTUDIO_MODEL,
        "messages": [{"role": "user", "content": content}],
        "temperature": 0.3,
        "max_tokens": 300,
    }

    url = f"{LMSTUDIO_URL}/v1/chat/completions"
    logger.info("Sending %d frames to LM Studio at %s", len(frames_b64), url)

    async with httpx.AsyncClient(timeout=120.0) as client:
        try:
            resp = await client.post(url, json=payload)
            resp.raise_for_status()
        except httpx.ConnectError:
            raise RuntimeError(
                f"Cannot connect to LM Studio at {LMSTUDIO_URL}. "
                "Is LM Studio running with a model loaded?"
            )

    data = resp.json()
    raw_text = data["choices"][0]["message"]["content"]

    # Strip code fences if present
    clean = _strip_code_fences(raw_text)

    try:
        parsed = json.loads(clean)
    except json.JSONDecodeError:
        raise RuntimeError(f"LM Studio returned non-JSON response: {clean[:200]}")

    return GeminiAnalysisResult(
        feedback_text=parsed.get("feedback_text", ""),
        metric_name=parsed.get("metric_name", ""),
        metric_value=parsed.get("metric_value", ""),
    )
