/**
 * Typed SSE event names and payloads used with EventSourceServerService.
 * Payload types are generated from the backend OpenAPI spec where available.
 */
export type AppSseEventName = 'dl-extract' | 'listing-refreshed';

export type { DlExtractionDonePayload } from '../api/model/dlExtractionDonePayload';

/** Pushed after the async Willhaben detail refresh completes. */
export interface ListingRefreshedPayload {
  whItemId: number;
  description: string;
  previews: Array<{ thumbUrl: string; fullUrl: string }>;
  categoryPath: Array<{ id: number; whId: number; name: string; level: number }>;
}
