"""
Download timpal0l/mdeberta-v3-base-squad2 and export to ONNX.

Requirements:
    pip install optimum[onnxruntime] transformers
"""

from optimum.onnxruntime import ORTModelForQuestionAnswering
from transformers import AutoTokenizer

MODEL_ID = "timpal0l/mdeberta-v3-base-squad2"
OUTPUT_DIR = "mdeberta-v3-base-squad2"

print(f"Exporting {MODEL_ID} to ONNX ...")
model = ORTModelForQuestionAnswering.from_pretrained(MODEL_ID, export=True)
tokenizer = AutoTokenizer.from_pretrained(MODEL_ID)

model.save_pretrained(OUTPUT_DIR)
tokenizer.save_pretrained(OUTPUT_DIR)
print(f"Done — files saved to {OUTPUT_DIR}/")
