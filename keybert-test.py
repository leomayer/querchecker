import time
from keybert import KeyBERT

# Your test data (Short German Willhaben-style text)
doc = """
Verkaufe mein gebrauchtes Mountainbike in sehr gutem Zustand. 
Shimano Schaltung, Scheibenbremsen und neue Reifen. 
Nur Selbstabholung in Wien. Preis verhandelbar.
"""

# The models you want to compare
models = [
    "paraphrase-multilingual-MiniLM-L12-v2",  # Light/Fast
    "distiluse-base-multilingual-cased-v1",   # Balanced
    "paraphrase-multilingual-mpnet-base-v2"   # Heavy/Accurate
]

print(f"{'Model Name':<45} | {'Time (s)':<10} | {'Top Keywords'}")
print("-" * 80)

for model_name in models:
    # 1. Start timer and load model
    start = time.time()
    kw_model = KeyBERT(model=model_name)
    
    # 2. Extract keywords
    keywords = kw_model.extract_keywords(
         doc, 
         keyphrase_ngram_range=(1, 1), 
         stop_words=None, 
         top_n=5
     )
    
    end = time.time()
    
    # 3. Print results
    kw_strings = [k[0] for k in keywords]
    print(f"{model_name:<45} | {end-start:<10.2f} | {kw_strings}")
