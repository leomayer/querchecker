#!/usr/bin/env python3
"""
Download timpal0l/mdeberta-v3-base-squad2 and export to ONNX.
Creates a local .venv on first run — no system-wide install needed.
"""

import sys, os, subprocess

# Self-bootstrap: re-exec inside a local venv so no system pip needed
VENV = os.path.join(os.path.dirname(__file__), ".venv")
VENV_PYTHON = os.path.join(VENV, "bin", "python3")
if sys.executable != VENV_PYTHON:
    if not os.path.exists(VENV):
        print("Creating .venv ...")
        subprocess.check_call([sys.executable, "-m", "venv", VENV])
    subprocess.check_call([VENV_PYTHON, "-m", "pip", "install", "-q", "optimum[onnxruntime]", "transformers"])
    os.execv(VENV_PYTHON, [VENV_PYTHON] + sys.argv)

# ── actual download logic ────────────────────────────────────────────────────

from optimum.onnxruntime import ORTModelForQuestionAnswering
from transformers import AutoTokenizer

MODEL_ID = "timpal0l/mdeberta-v3-base-squad2"
OUTPUT_DIR = os.path.join(os.path.dirname(__file__), "mdeberta-v3-base-squad2")

print(f"Exporting {MODEL_ID} to ONNX ...")
model = ORTModelForQuestionAnswering.from_pretrained(MODEL_ID, export=True)
tokenizer = AutoTokenizer.from_pretrained(MODEL_ID)

model.save_pretrained(OUTPUT_DIR)
tokenizer.save_pretrained(OUTPUT_DIR)
print(f"Done — files saved to {OUTPUT_DIR}/")
