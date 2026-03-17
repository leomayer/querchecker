#!/usr/bin/env python3
"""
Download bartowski/NuExtract-v1.5-GGUF (Q5_K_M quantization).
Base model: numind/NuExtract-v1.5 (Qwen2-1.5B fine-tuned for structured extraction).
3× larger than NuExtract-tiny-v1.5 (1.5B vs 0.5B) — better quality on ambiguous listings.
Architecture: qwen2 — fully supported by llama.cpp 4.x.
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
    subprocess.check_call([VENV_PYTHON, "-m", "pip", "install", "-q", "huggingface_hub"])
    os.execv(VENV_PYTHON, [VENV_PYTHON] + sys.argv)

# ── actual download logic ────────────────────────────────────────────────────

from huggingface_hub import hf_hub_download, list_repo_files

REPO_ID = "bartowski/NuExtract-v1.5-GGUF"
OUTPUT_DIR = os.path.join(os.path.dirname(__file__), "nuextract-1.5")
PREFERRED_QUANT = "Q4_K_M"

os.makedirs(OUTPUT_DIR, exist_ok=True)
target = os.path.join(OUTPUT_DIR, "model.gguf")

if os.path.exists(target):
    print(f"Already exists: {target}")
    sys.exit(0)

files = [f for f in list_repo_files(REPO_ID) if f.endswith(".gguf")]
print(f"Available GGUF files in {REPO_ID}:")
for f in files:
    print(f"  {f}")

chosen = next((f for f in files if PREFERRED_QUANT in f), None) or files[0]
print(f"\nDownloading: {chosen}")

path = hf_hub_download(repo_id=REPO_ID, filename=chosen, local_dir=OUTPUT_DIR)

if os.path.abspath(path) != os.path.abspath(target):
    os.rename(path, target)

print(f"Saved to {target}")
