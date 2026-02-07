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

VISION_PROMPT = """Analyze this short video clip of a tennis swing.

Identify the ONE single biggest flaw that the player needs to fix immediately.
Do not give a list. Give one specific correction.

Return the result in JSON format with these fields:
- feedback_text: A direct, spoken-style command to the player (max 15 words). e.g., "You are muscling it. Drop the racket head sooner."
- metric_name: A technical term for the issue. e.g., "Contact Point", "Racket Drop", "Follow Through".
- metric_value: The specific observation. e.g., "Too Close to Body", "Late", "Abbreviated"."""
