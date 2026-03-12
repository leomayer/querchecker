const STORAGE_KEY = 'querchecker.lastSearch';
const MAX_AGE_MS = 3 * 24 * 60 * 60 * 1000; // 3 Tage

export interface PersistedFilterDraft {
  keyword: string;
  rows: number;
  priceFrom: number | null;
  priceTo: number | null;
  locationAreaId?: number;
  categoryWhId?: number;
  paylivery: boolean;
}

interface StorageEntry {
  filterDraft: PersistedFilterDraft;
  savedAt: number;
}

export function persistSearch(filterDraft: PersistedFilterDraft): void {
  try {
    const entry: StorageEntry = { filterDraft, savedAt: Date.now() };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(entry));
  } catch {
    // localStorage nicht verfügbar (z.B. private browsing mit Einschränkung)
  }
}

export function loadPersistedSearch(): PersistedFilterDraft | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const entry: StorageEntry = JSON.parse(raw);
    if (Date.now() - entry.savedAt > MAX_AGE_MS) {
      localStorage.removeItem(STORAGE_KEY);
      return null;
    }
    return entry.filterDraft;
  } catch {
    return null;
  }
}
