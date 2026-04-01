const HISTORY_KEY = 'querchecker.searchHistory';
const MAX_ENTRIES = 10;

export interface SearchHistoryEntry {
  keyword: string;
  rows: number;
  priceFrom: number | null;
  priceTo: number | null;
  locationAreaId?: number;
  categoryWhId?: number;
  paylivery: boolean;
}

export function addToSearchHistory(entry: SearchHistoryEntry): void {
  const trimmed = entry.keyword.trim();
  if (!trimmed) return;
  try {
    const history = loadSearchHistory();
    const deduped = history.filter((e) => e.keyword.toLowerCase() !== trimmed.toLowerCase());
    localStorage.setItem(
      HISTORY_KEY,
      JSON.stringify([{ ...entry, keyword: trimmed }, ...deduped].slice(0, MAX_ENTRIES)),
    );
  } catch {
    // localStorage not available
  }
}

export function loadSearchHistory(): SearchHistoryEntry[] {
  try {
    const raw = localStorage.getItem(HISTORY_KEY);
    if (!raw) return [];
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return (parsed as unknown[]).filter(
      (e): e is SearchHistoryEntry =>
        typeof e === 'object' &&
        e !== null &&
        typeof (e as SearchHistoryEntry).keyword === 'string',
    );
  } catch {
    return [];
  }
}

export function clearSearchHistory(): void {
  try {
    localStorage.removeItem(HISTORY_KEY);
  } catch {
    // localStorage not available
  }
}
