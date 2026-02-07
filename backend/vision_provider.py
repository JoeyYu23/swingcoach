"""Vision provider routing layer.

Dispatches to Gemini (cloud) or LM Studio (local) based on VISION_PROVIDER env var.
Default is "local" for on-device inference via LM Studio + Qwen2.5-VL.
"""

import os
import logging

logger = logging.getLogger(__name__)


def get_provider_name() -> str:
    """Return the configured vision provider name ('local' or 'gemini')."""
    return os.environ.get("VISION_PROVIDER", "local").lower()


async def analyze_video(video_bytes: bytes, mime_type: str = "video/mp4"):
    """Route video analysis to the configured provider."""
    provider = get_provider_name()

    if provider == "gemini":
        from gemini_service import analyze_video_with_gemini
        return await analyze_video_with_gemini(video_bytes, mime_type)
    else:
        from lmstudio_service import analyze_video_with_lmstudio
        return await analyze_video_with_lmstudio(video_bytes, mime_type)


async def generate_tts(text: str) -> str | None:
    """Generate TTS audio. Only available when provider is gemini."""
    provider = get_provider_name()

    if provider == "gemini":
        from gemini_service import generate_tts_audio
        return await generate_tts_audio(text)

    # Local provider has no TTS
    return None
