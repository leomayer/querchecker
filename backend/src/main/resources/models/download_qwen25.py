#!/usr/bin/env python3
"""
Download Qwen/Qwen2.5-3B-Instruct-GGUF (Q4_K_M quantization).
Architecture: qwen2 — fully supported by llama.cpp 4.x.
Creates a local .venv on first run — no system-wide install needed.

Note: Qwen3-4B was originally planned but requires a newer llama.cpp build
(qwen3 arch support not yet in de.kherud:llama 4.2.0).
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

REPO_ID = "Qwen/Qwen2.5-3B-Instruct-GGUF"
OUTPUT_DIR = os.path.join(os.path.dirname(__file__), "qwen2.5-3b")
PREFERRED_QUANT = "q4_k_m"

os.makedirs(OUTPUT_DIR, exist_ok=True)
target = os.path.join(OUTPUT_DIR, "model.gguf")

if os.path.exists(target):
    print(f"Already exists: {target}")
    sys.exit(0)

files = [f for f in list_repo_files(REPO_ID) if f.endswith(".gguf")]
print(f"Available GGUF files in {REPO_ID}:")
for f in files:
    print(f"  {f}")

chosen = next((f for f in files if PREFERRED_QUANT in f.lower()), None) or files[0]
print(f"\nDownloading: {chosen}")

path = hf_hub_download(repo_id=REPO_ID, filename=chosen, local_dir=OUTPUT_DIR)

if os.path.abspath(path) != os.path.abspath(target):
    os.rename(path, target)

print(f"Saved to {target}")
