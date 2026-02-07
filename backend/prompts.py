"""Shared prompts for VLM services.

Loads the biomechanical reference system prompt from llm_prompt_context.md
and provides the common VISION_PROMPT used by all vision providers.
"""

import os
from pathlib import Path

# Load the biomechanical reference context as the system prompt.
# The file lives at the repo root, one level above backend/.
_CONTEXT_PATH = Path(__file__).resolve().parent.parent / "llm_prompt_context.md"

if _CONTEXT_PATH.is_file():
    SYSTEM_PROMPT = _CONTEXT_PATH.read_text(encoding="utf-8").strip()
else:
    SYSTEM_PROMPT = ""

_VISION_PROMPT_BASE = """Analyze this short video clip of a tennis swing.

Identify the ONE single biggest flaw that the player needs to fix immediately.
Do not give a list. Give one specific correction.

Return the result in JSON format with these fields:
- feedback_text: A direct, spoken-style command to the player (max 15 words). e.g., "You are muscling it. Drop the racket head sooner."
- metric_name: A technical term for the issue. e.g., "Contact Point", "Racket Drop", "Follow Through".
- metric_value: The specific observation. e.g., "Too Close to Body", "Late", "Abbreviated"."""


def format_imu_data(imu_result: dict) -> str:
    """Format an IMU swing analysis result dict into a text block for the VLM prompt."""
    lines = []
    phases = imu_result.get("phases")
    if phases:
        p = phases
        lines.append(f"- Preparation:    {p['preparation']['start_ms']:.0f} – {p['preparation']['end_ms']:.0f} ms")
        lines.append(f"- Acceleration:   {p['acceleration']['start_ms']:.0f} – {p['acceleration']['end_ms']:.0f} ms")
        lines.append(f"- Peak:           {p['peak']['t_ms']:.0f} ms | {p['peak']['gyro_mag_deg_s']:.0f} deg/s | {p['peak'].get('accel_mag_m_s2', 0):.1f} m/s²")
        lines.append(f"- Deceleration:   {p['deceleration']['start_ms']:.0f} – {p['deceleration']['end_ms']:.0f} ms")
        lines.append(f"- Follow-through: {p['follow_through']['start_ms']:.0f} – {p['follow_through']['end_ms']:.0f} ms")
    lines.append(f"- Peak angular velocity: {imu_result.get('peak_gyro_deg_s', 0):.0f} deg/s ({imu_result.get('peak_gyro_rad_s', 0):.1f} rad/s)")
    if "peak_accel_m_s2" in imu_result:
        lines.append(f"- Peak linear acceleration: {imu_result['peak_accel_m_s2']:.1f} m/s²")
    if "swing_duration_ms" in imu_result:
        lines.append(f"- Swing duration: {imu_result['swing_duration_ms']:.0f} ms")
    return "\n".join(lines)


def build_vision_prompt(imu_result: dict | None = None) -> str:
    """Build the full vision prompt, optionally including IMU sensor data."""
    if imu_result is None:
        return _VISION_PROMPT_BASE

    imu_text = format_imu_data(imu_result)
    return f"""{_VISION_PROMPT_BASE}

The following IMU sensor data was captured from a racquet-mounted sensor during this swing.
Use it to corroborate or refine your visual analysis:

{imu_text}"""


# Default prompt for backwards compatibility (no IMU data)
VISION_PROMPT = _VISION_PROMPT_BASE
