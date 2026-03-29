export interface SpecsFeatureGroup {
  name: string;
  features: { name: string; value: string }[];
}

export interface LookupResult {
  lookupStatus: 'COMPLETE' | 'FAILED' | 'QUOTA_EXCEEDED' | 'NO_SOURCES' | 'ERROR';
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
  /** Frontend-only: the term that was used for this lookup */
  lookupTerm?: string;
}
