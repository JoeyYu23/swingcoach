"""LM Studio vision service — local on-device inference via Qwen2.5-VL.

Extracts frames from video using ffmpeg + Pillow (no cv2 needed),
sends them as base64 images to LM Studio's OpenAI-compatible
/v1/chat/completions endpoint.
"""

import asyncio
import base64
import io
import json
import logging
import os
import shutil
import subprocess
import tempfile
from typing import List

import httpx
from PIL import Image

from prompts import SYSTEM_PROMPT, build_vision_prompt

logger = logging.getLogger(__name__)

# Configuration via environment variables
LMSTUDIO_URL = os.environ.get("LMSTUDIO_URL", "http://localhost:1234")
LMSTUDIO_MODEL = os.environ.get("LMSTUDIO_MODEL", "qwen2.5-vl-7b-instruct")
NUM_FRAMES = int(os.environ.get("NUM_FRAMES", "6"))
MAX_FRAME_DIM = int(os.environ.get("MAX_FRAME_DIM", "512"))


class LmStudioAnalysisResult:
    """Result from LM Studio vision analysis.

    Same interface as gemini_service.GeminiAnalysisResult.
    """

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


def _find_ffmpeg() -> str:
    """Locate the ffmpeg binary. Returns path or raises RuntimeError."""
    path = shutil.which("ffmpeg")
    if path:
        return path
    # Winget installs to a well-known location
    winget_path = os.path.expandvars(
        r"%LOCALAPPDATA%\Microsoft\WinGet\Links\ffmpeg.exe"
    )
    if os.path.isfile(winget_path):
        return winget_path
    # Scan winget Packages directory for ffmpeg
    packages_dir = os.path.expandvars(
        r"%LOCALAPPDATA%\Microsoft\WinGet\Packages"
    )
    if os.path.isdir(packages_dir):
        import glob as _glob
        matches = _glob.glob(
            os.path.join(packages_dir, "Gyan.FFmpeg*", "**", "ffmpeg.exe"),
            recursive=True,
        )
        if matches:
            return matches[0]
    raise RuntimeError(
        "ffmpeg not found. Install it with: winget install Gyan.FFmpeg"
    )


def extract_frames(
    video_bytes: bytes,
    num_frames: int = NUM_FRAMES,
    max_dim: int = MAX_FRAME_DIM,
) -> List[str]:
    """Extract evenly-spaced frames from video, resize, and encode as base64 JPEG.

    Uses ffmpeg + Pillow (no cv2 dependency).
    CPU-bound — should be called via run_in_executor.

    Args:
        video_bytes: Raw video file bytes.
        num_frames: Number of frames to extract.
        max_dim: Maximum dimension (width or height) for resized frames.

    Returns:
        List of base64-encoded JPEG strings.
    """
    ffmpeg = _find_ffmpeg()
    tmp_dir = tempfile.mkdtemp(prefix="swingcoach_")

    try:
        # Write video to temp file
        video_path = os.path.join(tmp_dir, "input.mp4")
        with open(video_path, "wb") as f:
            f.write(video_bytes)

        # Probe total frame count — only replace the filename, not directory parts
        ffmpeg_dir = os.path.dirname(ffmpeg)
        ffprobe = os.path.join(ffmpeg_dir, "ffprobe.exe" if os.name == "nt" else "ffprobe")
        probe_cmd = [
            ffprobe, "-v", "error",
            "-select_streams", "v:0",
            "-count_frames",
            "-show_entries", "stream=nb_read_frames",
            "-of", "csv=p=0",
            video_path,
        ]
        probe = subprocess.run(probe_cmd, capture_output=True, timeout=30)
        try:
            total_frames = int(probe.stdout.decode().strip())
        except (ValueError, AttributeError):
            total_frames = 100  # fallback estimate

        # Calculate step to get evenly-spaced frames
        actual_count = min(num_frames, max(total_frames, 1))
        step = max(1, total_frames // actual_count)

        # Extract frames with ffmpeg
        output_pattern = os.path.join(tmp_dir, "frame_%03d.jpg")
        select_expr = f"not(mod(n\\,{step}))"
        cmd = [
            ffmpeg, "-i", video_path,
            "-vf", f"select='{select_expr}'",
            "-vsync", "vfr",
            "-frames:v", str(num_frames),
            "-q:v", "5",
            output_pattern,
            "-y", "-loglevel", "error",
        ]

        result = subprocess.run(cmd, capture_output=True, timeout=30)
        if result.returncode != 0:
            stderr = result.stderr.decode(errors="replace")
            logger.warning("ffmpeg error: %s", stderr)
            raise RuntimeError(f"ffmpeg frame extraction failed: {stderr[:200]}")

        # Read extracted JPEG files, resize with Pillow, encode as base64
        frames_b64: List[str] = []
        frame_files = sorted(
            f for f in os.listdir(tmp_dir) if f.startswith("frame_")
        )

        for fname in frame_files:
            fpath = os.path.join(tmp_dir, fname)
            img = Image.open(fpath)

            # Resize keeping aspect ratio if needed
            w, h = img.size
            if max(w, h) > max_dim:
                scale = max_dim / max(w, h)
                img = img.resize(
                    (int(w * scale), int(h * scale)), Image.LANCZOS
                )

            # Encode to JPEG base64
            buf = io.BytesIO()
            img.save(buf, format="JPEG", quality=85)
            frames_b64.append(base64.b64encode(buf.getvalue()).decode("ascii"))

        return frames_b64
    finally:
        shutil.rmtree(tmp_dir, ignore_errors=True)


async def analyze_video_with_lmstudio(
    video_bytes: bytes, mime_type: str = "video/mp4", *, imu_data: dict | None = None
) -> LmStudioAnalysisResult:
    """Send video frames to LM Studio for vision analysis.

    Extracts frames in a thread pool, then POSTs to the OpenAI-compatible endpoint.
    """
    import time as _time

    t0 = _time.time()
    loop = asyncio.get_event_loop()
    frames_b64 = await loop.run_in_executor(None, extract_frames, video_bytes)
    t_extract = _time.time()
    logger.info("[TIMING] Frame extraction: %.2fs (%d frames, %d KB total)",
                t_extract - t0, len(frames_b64),
                sum(len(f) for f in frames_b64) // 1024 if frames_b64 else 0)

    if not frames_b64:
        raise RuntimeError("No frames could be extracted from video")

    prompt = build_vision_prompt(imu_data)

    # Build multi-image message for OpenAI-compatible API
    content: list = []
    for i, b64 in enumerate(frames_b64):
        content.append({
            "type": "image_url",
            "image_url": {"url": f"data:image/jpeg;base64,{b64}"},
        })
    content.append({"type": "text", "text": prompt})

    messages = []
    if SYSTEM_PROMPT:
        messages.append({"role": "system", "content": SYSTEM_PROMPT})
    messages.append({"role": "user", "content": content})

    payload = {
        "model": LMSTUDIO_MODEL,
        "messages": messages,
        "temperature": 0.3,
        "max_tokens": 300,
    }

    url = f"{LMSTUDIO_URL}/v1/chat/completions"
    logger.info("Sending %d frames to LM Studio at %s", len(frames_b64), url)

    t_pre_llm = _time.time()
    async with httpx.AsyncClient(timeout=120.0) as client:
        try:
            resp = await client.post(url, json=payload)
            resp.raise_for_status()
        except httpx.ConnectError:
            raise RuntimeError(
                f"Cannot connect to LM Studio at {LMSTUDIO_URL}. "
                "Is LM Studio running with a model loaded?"
            )
    t_llm = _time.time()
    logger.info("[TIMING] LLM inference: %.2fs", t_llm - t_pre_llm)
    logger.info("[TIMING] Total pipeline: %.2fs (extract=%.2fs + llm=%.2fs)",
                t_llm - t0, t_extract - t0, t_llm - t_pre_llm)

    data = resp.json()
    raw_text = data["choices"][0]["message"]["content"]

    # Strip code fences if present
    clean = _strip_code_fences(raw_text)

    try:
        parsed = json.loads(clean)
    except json.JSONDecodeError:
        raise RuntimeError(f"LM Studio returned non-JSON response: {clean[:200]}")

    return LmStudioAnalysisResult(
        feedback_text=parsed.get("feedback_text", ""),
        metric_name=parsed.get("metric_name", ""),
        metric_value=parsed.get("metric_value", ""),
    )
