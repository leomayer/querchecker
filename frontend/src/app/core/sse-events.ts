/**
 * Typed SSE event names and payloads used with EventSourceServerService.
 * Payload types are generated from the backend OpenAPI spec where available.
 */
export type AppSseEventName =
  | 'dl-extract'
  | 'listing-refreshed'
  | 'lookup-result'
  | 'error-notification'
  | 'provider-status';

export type ProviderState = 'UNCONFIGURED' | 'CONFIGURED' | 'VALID' | 'UNREACHABLE' | 'UNAVAILABLE';

/** Pushed on SSE connect and on every provider state change. */
export interface ProviderStatusPayload {
  searchState: ProviderState;
  llmState: ProviderState;
  searchProvider: string;      // "BRAVE" | "GOOGLE_DISCOVERY"
  llmProvider: string;         // "GROQ" | "OPENROUTER" | "LOCAL"
  searchError: string | null;
  llmError: string | null;
  searchHttpStatus: number | null;
  llmHttpStatus: number | null;
  serverStartToken: string;
}

export type { DlExtractionDonePayload } from '../api/model/dlExtractionDonePayload';

/** Unified SSE event envelope for all event types. */
export interface SseEvent<T> {
  eventType: string;
  whItemId: number;
  payload: T;
  timestamp: number;
}

/** Pushed when a rate-limited lookup completes its backend retry. */
export interface LookupResultPayload {
  listingId: number;
  lookupStatus: string;
  quickFacts: Record<string, string> | null;
  icecatId: string | null;
  sourceType: string | null;
  sourceDomain: string | null;
  siteLabel: string | null;
  sourceUrl: string | null;
  featureGroupsJson: string | null;
  retryProvider: string | null;
  retryModel: string | null;
  history: LookupHistoryEntry[] | null;
}

export interface LookupHistoryEntry {
  lookupTerm: string;
  lookupStatus: string;
  quickFacts: Record<string, string> | null;
  icecatId: string | null;
  sourceType: string | null;
  sourceDomain: string | null;
  siteLabel: string | null;
  sourceUrl: string | null;
  featureGroupsJson: string | null;
  createdAt: string;
}

/** Pushed when an error occurs (DL extraction failure, lookup failure, network error, etc). */
export interface ErrorNotificationPayload {
  errorType: string;
  message: string;
  retryAfterSeconds: number | null;
}

/** Pushed after the async Willhaben detail refresh completes. */
export interface ListingRefreshedPayload {
  whItemId: number;
  description: string;
  previews: Array<{ thumbUrl: string; fullUrl: string }>;
  categoryPath: Array<{ id: number; whId: number; name: string; level: number }>;
}
