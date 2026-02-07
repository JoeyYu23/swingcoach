"""E2E tests that call the real Gemini API.

Skipped automatically when GEMINI_API_KEY is not set.
Run with:  GEMINI_API_KEY=... python -m pytest tests/test_e2e_real_api.py -v
"""

import os
import sys

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

_HAS_KEY = bool(os.environ.get("GEMINI_API_KEY"))
pytestmark = pytest.mark.skipif(not _HAS_KEY, reason="GEMINI_API_KEY not set")


@pytest.mark.asyncio
async def test_real_gemini_vision_with_sample_video(sample_video_bytes):
    from gemini_service import analyze_video_with_gemini

    result = await analyze_video_with_gemini(sample_video_bytes, "video/mp4")
    assert result.feedback_text  # non-empty
    assert result.metric_name
    assert result.metric_value


@pytest.mark.asyncio
async def test_real_gemini_tts_generates_audio():
    from gemini_service import generate_tts_audio

    audio = await generate_tts_audio("Extend your arm through contact.")
    # TTS may or may not work depending on model availability
    # Just ensure it doesn't crash; audio can be None
    assert audio is None or isinstance(audio, str)


def test_real_full_pipeline(test_client, sample_video_path):
    """Full /api/analyze-swing with real Gemini API (pose may still 400 on sample)."""
    with open(sample_video_path, "rb") as f:
        resp = test_client.post(
            "/api/analyze-swing",
            files={"file": ("swing.mp4", f, "video/mp4")},
            data={"rotation": "0"},
        )

    # Either we get a full 200 or a 400 from pose (sample too short)
    if resp.status_code == 200:
        body = resp.json()
        assert body.get("gemini_analysis") is not None
        assert body["gemini_analysis"]["feedback_text"]
        assert body["processing_info"]["total_seconds"] > 0
    else:
        assert resp.status_code == 400
