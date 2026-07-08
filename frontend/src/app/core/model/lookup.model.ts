export interface SpecsFeatureGroup {
  name: string;
  features: { name: string; value: string }[];
}

export interface LookupResult {
  lookupStatus:
    | 'COMPLETE'
    | 'FAILED'
    | 'QUOTA_EXCEEDED'
    | 'KEY_QUOTA_EXCEEDED'
    | 'NO_SOURCES'
    | 'ERROR'
    | 'RATE_LIMITED';
  quickFacts: Record<string, string>;
  icecatId: string | null;
  icecatSpecsJson?: string | null;
  sourceType: string | null;
  sourceDomain: string | null;
  siteLabel: string | null;
  sourceUrl: string | null;
  /** Raw JSON string from backend — parsed into featureGroups by ExtractionStore. */
  featureGroupsJson?: string | null;
  featureGroups: SpecsFeatureGroup[] | null;
  /** ISO datetime ab dem ein erneuter Lookup möglich ist (nur bei FAILED/ERROR). */
  retryAfter?: string | null;
  /** Sekunden bis zum Backend-Retry (nur bei RATE_LIMITED). */
  retryAfterSeconds?: number | null;
  /** Provider-Anzeigename (nur bei RATE_LIMITED), z.B. "Groq". */
  retryProvider?: string | null;
  /** Modellname (nur bei RATE_LIMITED), z.B. "llama-3.1-8b-instant". */
  retryModel?: string | null;
  /** Frontend-only: the term that was used for this lookup */
  lookupTerm?: string;
  /** Lookup-Verlauf für dieses Inserat (neueste zuerst, max. 5 Einträge). */
  history?: import('../sse-events').LookupHistoryEntry[] | null;
}
