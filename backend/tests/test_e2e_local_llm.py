"""End-to-end tests for local LLM (LM Studio) vision pipeline.

These tests require LM Studio to be running with a model loaded.
They are automatically skipped if LM Studio is not reachable.
"""

import base64
import os
import sys

import httpx
import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

LMSTUDIO_URL = os.environ.get("LMSTUDIO_URL", "http://localhost:1234")
FIXTURES_DIR = os.path.join(os.path.dirname(__file__), "fixtures")
SAMPLE_VIDEO = os.path.join(FIXTURES_DIR, "sample_swing.mp4")


def _lmstudio_is_running() -> bool:
    try:
        resp = httpx.get(f"{LMSTUDIO_URL}/v1/models", timeout=3.0)
        return resp.status_code == 200
    except Exception:
        return False


pytestmark = pytest.mark.skipif(
    not _lmstudio_is_running(),
    reason=f"LM Studio not reachable at {LMSTUDIO_URL}",
)


class TestLmStudioE2E:
    def test_lmstudio_health(self):
        """Verify /v1/models returns loaded models."""
        resp = httpx.get(f"{LMSTUDIO_URL}/v1/models", timeout=5.0)
        assert resp.status_code == 200
        data = resp.json()
        assert "data" in data
        assert len(data["data"]) > 0, "No models loaded in LM Studio"

    def test_frame_extraction_produces_valid_images(self):
        """Extract frames and verify JPEG magic bytes."""
        from lmstudio_service import extract_frames

        video_bytes = open(SAMPLE_VIDEO, "rb").read()
        frames = extract_frames(video_bytes, num_frames=3, max_dim=512)
        assert len(frames) > 0

        for b64 in frames:
            raw = base64.b64decode(b64)
            assert raw[:2] == b'\xff\xd8', "Frame is not valid JPEG"
            assert len(raw) > 100, "Frame suspiciously small"

    @pytest.mark.asyncio
    async def test_real_lmstudio_vision_with_sample_video(self):
        """Real inference: send sample video frames to LM Studio."""
        from lmstudio_service import analyze_video_with_lmstudio

        video_bytes = open(SAMPLE_VIDEO, "rb").read()
        result = await analyze_video_with_lmstudio(video_bytes)

        assert result.feedback_text, "feedback_text should not be empty"
        assert result.metric_name, "metric_name should not be empty"
        assert result.metric_value, "metric_value should not be empty"

    @pytest.mark.asyncio
    async def test_real_full_pipeline_local_provider(self):
        """Full pipeline: analyze_video via local provider."""
        os.environ["VISION_PROVIDER"] = "local"
        from vision_provider import analyze_video

        video_bytes = open(SAMPLE_VIDEO, "rb").read()
        result = await analyze_video(video_bytes, "video/mp4")

        assert result.feedback_text
        assert result.metric_name
        assert result.to_dict()["feedback_text"] == result.feedback_text
