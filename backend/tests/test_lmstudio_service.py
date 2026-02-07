"""Unit tests for lmstudio_service.py.

Frame extraction uses ffmpeg + Pillow (no cv2 dependency).
Tests that need a sample video file are skipped when the fixture is missing.
Mock-based async tests mock extract_frames to avoid external dependencies.
"""

import base64
import io
import json
import os
import sys
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from lmstudio_service import _strip_code_fences, analyze_video_with_lmstudio, LmStudioAnalysisResult

FIXTURES_DIR = os.path.join(os.path.dirname(__file__), "fixtures")
SAMPLE_VIDEO = os.path.join(FIXTURES_DIR, "sample_swing.mp4")

needs_sample_video = pytest.mark.skipif(
    not os.path.isfile(SAMPLE_VIDEO), reason="sample_swing.mp4 fixture missing"
)

# Fake frames for mocking extract_frames
FAKE_FRAMES_B64 = [base64.b64encode(b"\xff\xd8\xff\xe0fake_jpeg_data").decode("ascii")]


# ---------------------------------------------------------------------------
# _strip_code_fences
# ---------------------------------------------------------------------------

class TestStripCodeFences:
    def test_plain_json(self):
        raw = '{"feedback_text": "hello"}'
        assert _strip_code_fences(raw) == raw

    def test_json_fence(self):
        raw = '```json\n{"feedback_text": "hello"}\n```'
        assert _strip_code_fences(raw) == '{"feedback_text": "hello"}'

    def test_plain_fence(self):
        raw = '```\n{"key": "val"}\n```'
        assert _strip_code_fences(raw) == '{"key": "val"}'

    def test_fence_with_trailing_whitespace(self):
        raw = '```json\n{"a": 1}\n```  \n'
        assert _strip_code_fences(raw) == '{"a": 1}'

    def test_no_fence(self):
        raw = "  just some text  "
        assert _strip_code_fences(raw) == "just some text"


# ---------------------------------------------------------------------------
# extract_frames (uses ffmpeg + Pillow)
# ---------------------------------------------------------------------------

@needs_sample_video
class TestExtractFrames:
    def test_returns_correct_count(self):
        from lmstudio_service import extract_frames
        video_bytes = open(SAMPLE_VIDEO, "rb").read()
        frames = extract_frames(video_bytes, num_frames=4, max_dim=256)
        assert len(frames) > 0
        assert len(frames) <= 4

    def test_handles_short_video(self):
        """When requesting more frames than exist, should return what's available."""
        from lmstudio_service import extract_frames
        video_bytes = open(SAMPLE_VIDEO, "rb").read()
        frames = extract_frames(video_bytes, num_frames=10000, max_dim=256)
        assert len(frames) > 0
        assert len(frames) <= 10000

    def test_resizes_to_max_dim(self):
        """Verify frames respect max dimension constraint."""
        from PIL import Image
        from lmstudio_service import extract_frames

        video_bytes = open(SAMPLE_VIDEO, "rb").read()
        frames = extract_frames(video_bytes, num_frames=1, max_dim=128)
        assert len(frames) == 1

        img_bytes = base64.b64decode(frames[0])
        img = Image.open(io.BytesIO(img_bytes))
        w, h = img.size
        assert max(h, w) <= 128

    def test_frames_are_valid_jpeg(self):
        """Each extracted frame should be valid JPEG base64."""
        from lmstudio_service import extract_frames
        video_bytes = open(SAMPLE_VIDEO, "rb").read()
        frames = extract_frames(video_bytes, num_frames=2, max_dim=256)
        for b64 in frames:
            raw = base64.b64decode(b64)
            assert raw[:2] == b'\xff\xd8', "Frame is not valid JPEG"


# ---------------------------------------------------------------------------
# analyze_video_with_lmstudio (mocked — no cv2 or LM Studio needed)
# ---------------------------------------------------------------------------

class TestAnalyzeVideoWithLmstudio:
    @pytest.mark.asyncio
    async def test_returns_valid_result(self):
        """Mock httpx and extract_frames — verify LmStudioAnalysisResult returned."""
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.raise_for_status = MagicMock()
        mock_response.json.return_value = {
            "choices": [{
                "message": {
                    "content": json.dumps({
                        "feedback_text": "Drop racket head sooner.",
                        "metric_name": "Racket Drop",
                        "metric_value": "Late",
                    })
                }
            }]
        }

        with (
            patch("lmstudio_service.extract_frames", return_value=FAKE_FRAMES_B64),
            patch("lmstudio_service.httpx.AsyncClient") as MockClient,
        ):
            mock_client = AsyncMock()
            mock_client.post.return_value = mock_response
            mock_client.__aenter__ = AsyncMock(return_value=mock_client)
            mock_client.__aexit__ = AsyncMock(return_value=False)
            MockClient.return_value = mock_client

            result = await analyze_video_with_lmstudio(b"fake_video_bytes")

            assert result.feedback_text == "Drop racket head sooner."
            assert result.metric_name == "Racket Drop"
            assert result.metric_value == "Late"
            assert isinstance(result, LmStudioAnalysisResult)

    @pytest.mark.asyncio
    async def test_handles_json_in_code_fence(self):
        """LLM wraps JSON in code fences — should be handled."""
        fenced = '```json\n{"feedback_text":"Fix it.","metric_name":"Grip","metric_value":"Too Tight"}\n```'
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.raise_for_status = MagicMock()
        mock_response.json.return_value = {
            "choices": [{"message": {"content": fenced}}]
        }

        with (
            patch("lmstudio_service.extract_frames", return_value=FAKE_FRAMES_B64),
            patch("lmstudio_service.httpx.AsyncClient") as MockClient,
        ):
            mock_client = AsyncMock()
            mock_client.post.return_value = mock_response
            mock_client.__aenter__ = AsyncMock(return_value=mock_client)
            mock_client.__aexit__ = AsyncMock(return_value=False)
            MockClient.return_value = mock_client

            result = await analyze_video_with_lmstudio(b"fake_video_bytes")
            assert result.feedback_text == "Fix it."

    @pytest.mark.asyncio
    async def test_handles_non_json_response(self):
        """Plain text response should raise RuntimeError."""
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.raise_for_status = MagicMock()
        mock_response.json.return_value = {
            "choices": [{"message": {"content": "I cannot analyze this video."}}]
        }

        with (
            patch("lmstudio_service.extract_frames", return_value=FAKE_FRAMES_B64),
            patch("lmstudio_service.httpx.AsyncClient") as MockClient,
        ):
            mock_client = AsyncMock()
            mock_client.post.return_value = mock_response
            mock_client.__aenter__ = AsyncMock(return_value=mock_client)
            mock_client.__aexit__ = AsyncMock(return_value=False)
            MockClient.return_value = mock_client

            with pytest.raises(RuntimeError, match="non-JSON"):
                await analyze_video_with_lmstudio(b"fake_video_bytes")

    @pytest.mark.asyncio
    async def test_handles_connection_error(self):
        """LM Studio not running should give a clear error."""
        import httpx as _httpx

        with (
            patch("lmstudio_service.extract_frames", return_value=FAKE_FRAMES_B64),
            patch("lmstudio_service.httpx.AsyncClient") as MockClient,
        ):
            mock_client = AsyncMock()
            mock_client.post.side_effect = _httpx.ConnectError("Connection refused")
            mock_client.__aenter__ = AsyncMock(return_value=mock_client)
            mock_client.__aexit__ = AsyncMock(return_value=False)
            MockClient.return_value = mock_client

            with pytest.raises(RuntimeError, match="Cannot connect to LM Studio"):
                await analyze_video_with_lmstudio(b"fake_video_bytes")
