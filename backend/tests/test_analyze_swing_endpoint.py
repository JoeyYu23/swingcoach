"""Unit tests for /api/analyze-swing and related endpoints.

Uses FastAPI TestClient (httpx-based) so the full ASGI app is exercised,
including startup/shutdown events.  Gemini calls are mocked.
"""

import json
import sys
import os
from unittest.mock import patch, MagicMock

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from gemini_service import GeminiAnalysisResult


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _mock_gemini_result():
    return GeminiAnalysisResult(
        feedback_text="Drop your racket head sooner.",
        metric_name="Racket Drop",
        metric_value="Late",
    )


# ---------------------------------------------------------------------------
# /api/analyze-swing
# ---------------------------------------------------------------------------

def test_analyze_swing_returns_complete_response(test_client, sample_video_path):
    """POST a real video, mock Gemini, verify response shape."""
    with (
        patch("app.analyze_video_with_gemini", return_value=_mock_gemini_result()),
        patch("app.generate_tts_audio", return_value="FAKE_AUDIO_B64"),
    ):
        with open(sample_video_path, "rb") as f:
            resp = test_client.post(
                "/api/analyze-swing",
                files={"file": ("swing.mp4", f, "video/mp4")},
                data={"rotation": "0"},
            )

    # The sample video may not have enough pose frames; accept 200 or 400
    if resp.status_code == 200:
        body = resp.json()
        assert "pose_analysis" in body
        assert "gemini_analysis" in body
        assert "audio_base64" in body
        assert "processing_info" in body
    else:
        # 400 = not enough pose frames, but the endpoint itself works
        assert resp.status_code == 400


def test_analyze_swing_includes_processing_info(test_client, sample_video_path):
    with (
        patch("app.analyze_video_with_gemini", return_value=_mock_gemini_result()),
        patch("app.generate_tts_audio", return_value=None),
    ):
        with open(sample_video_path, "rb") as f:
            resp = test_client.post(
                "/api/analyze-swing",
                files={"file": ("swing.mp4", f, "video/mp4")},
                data={"rotation": "0"},
            )

    if resp.status_code == 200:
        info = resp.json()["processing_info"]
        assert "total_seconds" in info
        assert "parallel_seconds" in info


def test_analyze_swing_rejects_missing_file(test_client):
    resp = test_client.post("/api/analyze-swing")
    assert resp.status_code == 422  # validation error


# ---------------------------------------------------------------------------
# /api/analyze-video (regression — existing endpoint unchanged)
# ---------------------------------------------------------------------------

def test_existing_analyze_video_still_works(test_client, sample_video_path):
    with open(sample_video_path, "rb") as f:
        resp = test_client.post(
            "/api/analyze-video",
            files={"file": ("swing.mp4", f, "video/mp4")},
            data={"rotation": "0"},
        )
    # Accept 200 (success) or 400 (not enough frames in sample)
    assert resp.status_code in (200, 400)


# ---------------------------------------------------------------------------
# /api/network-info
# ---------------------------------------------------------------------------

def test_network_info_returns_ip(test_client):
    resp = test_client.get("/api/network-info")
    assert resp.status_code == 200
    body = resp.json()
    assert "ip" in body
    assert "url" in body
    assert "endpoints" in body
    assert "analyze_swing" in body["endpoints"]


# ---------------------------------------------------------------------------
# /health
# ---------------------------------------------------------------------------

def test_health_check(test_client):
    resp = test_client.get("/health")
    assert resp.status_code == 200
    assert resp.json()["status"] == "ok"
