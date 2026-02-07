"""Shared fixtures for backend tests."""

import os
import pathlib

import pytest
from fastapi.testclient import TestClient


FIXTURES_DIR = pathlib.Path(__file__).parent / "fixtures"
SAMPLE_VIDEO = FIXTURES_DIR / "sample_swing.mp4"


@pytest.fixture
def sample_video_bytes() -> bytes:
    """Return the raw bytes of the sample swing video."""
    return SAMPLE_VIDEO.read_bytes()


@pytest.fixture
def sample_video_path() -> pathlib.Path:
    """Return the Path to the sample swing video."""
    return SAMPLE_VIDEO


@pytest.fixture
def test_client():
    """Create a FastAPI TestClient with the app.

    Triggers startup/shutdown lifespan events so pose_backend is initialised.
    """
    # Import here to avoid import-time side-effects
    from app import app
    with TestClient(app) as client:
        yield client


@pytest.fixture
def has_gemini_key() -> bool:
    """Return True if a Gemini API key is available."""
    return bool(os.environ.get("GEMINI_API_KEY"))
