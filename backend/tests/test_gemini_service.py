"""Unit tests for gemini_service module — mocked, no real API calls."""

import json
import sys
import os
from unittest.mock import patch, MagicMock, AsyncMock

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from gemini_service import (
    analyze_video_with_gemini,
    generate_tts_audio,
    GeminiAnalysisResult,
    _get_client,
)


# ---------------------------------------------------------------------------
# analyze_video_with_gemini
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_analyze_video_parses_valid_response():
    """Mock the genai client so no real HTTP call is made."""
    fake_json = json.dumps({
        "feedback_text": "Extend your arm through contact.",
        "metric_name": "Contact Point",
        "metric_value": "Too Late",
    })

    mock_response = MagicMock()
    mock_response.text = fake_json

    mock_client = MagicMock()
    mock_client.models.generate_content.return_value = mock_response

    with patch("gemini_service._get_client", return_value=mock_client):
        result = await analyze_video_with_gemini(b"\x00\x00\x00", "video/mp4")

    assert isinstance(result, GeminiAnalysisResult)
    assert result.feedback_text == "Extend your arm through contact."
    assert result.metric_name == "Contact Point"
    assert result.metric_value == "Too Late"


@pytest.mark.asyncio
async def test_analyze_video_handles_non_json():
    mock_response = MagicMock()
    mock_response.text = "NOT JSON"

    mock_client = MagicMock()
    mock_client.models.generate_content.return_value = mock_response

    with patch("gemini_service._get_client", return_value=mock_client):
        with pytest.raises(RuntimeError, match="non-JSON"):
            await analyze_video_with_gemini(b"\x00", "video/mp4")


@pytest.mark.asyncio
async def test_analyze_video_handles_api_error():
    mock_client = MagicMock()
    mock_client.models.generate_content.side_effect = Exception("API error 500")

    with patch("gemini_service._get_client", return_value=mock_client):
        with pytest.raises(Exception, match="API error 500"):
            await analyze_video_with_gemini(b"\x00", "video/mp4")


# ---------------------------------------------------------------------------
# generate_tts_audio
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_generate_tts_returns_base64_audio():
    mock_part = MagicMock()
    mock_part.inline_data.data = "AAAA_base64_audio"

    mock_content = MagicMock()
    mock_content.parts = [mock_part]

    mock_candidate = MagicMock()
    mock_candidate.content = mock_content

    mock_response = MagicMock()
    mock_response.candidates = [mock_candidate]

    mock_client = MagicMock()
    mock_client.models.generate_content.return_value = mock_response

    with patch("gemini_service._get_client", return_value=mock_client):
        audio = await generate_tts_audio("Test coaching feedback")

    assert audio == "AAAA_base64_audio"


@pytest.mark.asyncio
async def test_generate_tts_returns_none_on_failure():
    mock_client = MagicMock()
    mock_client.models.generate_content.side_effect = Exception("TTS down")

    with patch("gemini_service._get_client", return_value=mock_client):
        audio = await generate_tts_audio("Test")

    assert audio is None


# ---------------------------------------------------------------------------
# GeminiAnalysisResult
# ---------------------------------------------------------------------------

def test_to_dict():
    r = GeminiAnalysisResult("Fix elbow", "Elbow Angle", "Too Bent")
    d = r.to_dict()
    assert d == {
        "feedback_text": "Fix elbow",
        "metric_name": "Elbow Angle",
        "metric_value": "Too Bent",
    }
