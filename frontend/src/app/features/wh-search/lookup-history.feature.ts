import { inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { patchState, signalStoreFeature, withMethods, withState } from '@ngrx/signals';
import { LookupHistoryEntry } from '../../core/sse-events';
import { API_URLS } from '../../core/api-urls';

export type { LookupHistoryEntry };

export function withLookupHistory() {
  return signalStoreFeature(
    withState({ lookupHistory: {} as Record<number, LookupHistoryEntry[]> }),
    withMethods((store) => {
      const http = inject(HttpClient);
      return {
        setHistory(whItemId: number, entries: LookupHistoryEntry[]): void {
          patchState(store, (s) => ({
            lookupHistory: { ...s.lookupHistory, [whItemId]: entries },
          }));
        },
        loadHistory(whItemId: number, listingId: number): void {
          http.get<LookupHistoryEntry[]>(API_URLS.productLookupHistory(listingId)).subscribe({
            next: (entries) => {
              patchState(store, (s) => ({
                lookupHistory: { ...s.lookupHistory, [whItemId]: entries },
              }));
            },
            error: () => {
              /* silently ignore — history is non-critical */
            },
          });
        },
        removeHistory(whItemId: number): void {
          patchState(store, (s) => {
            const { [whItemId]: _, ...rest } = s.lookupHistory;
            return { lookupHistory: rest };
          });
        },
        clearHistory(): void {
          patchState(store, { lookupHistory: {} });
        },
      };
    }),
  );
}
