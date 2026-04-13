import { inject } from '@angular/core';
import {
  patchState,
  signalStore,
  withComputed,
  withHooks,
  withMethods,
  withState,
} from '@ngrx/signals';
import { computed } from '@angular/core';
import { AppSseEventName, ProviderState, ProviderStatusPayload } from './sse-events';
import { EventSourceServerService } from '../shared/utils/event-source-server';

const ACKED_HASH_KEY = 'provider-status-acknowledged-hash';

function statusHash(p: ProviderStatusPayload): string {
  return `${p.serverStartToken}:${p.searchProvider}:${p.llmProvider}:${p.searchState}:${p.llmState}`;
}

interface ProviderStatusState {
  status: ProviderStatusPayload | null;
  acknowledgedHash: string | null;
}

export type { ProviderState, ProviderStatusPayload };

export const ProviderStatusStore = signalStore(
  { providedIn: 'root' },
  withState<ProviderStatusState>({
    status: null,
    acknowledgedHash: null,
  }),
  withComputed((store) => ({
    /** True when at least one provider is not CONFIGURED or VALID. */
    badgeVisible: computed(() => {
      const s = store.status();
      if (!s) return false;
      const isConfigured = (state: ProviderState) => state === 'CONFIGURED' || state === 'VALID';
      return !isConfigured(s.searchState) || !isConfigured(s.llmState);
    }),
    /** True when a provider is UNREACHABLE or UNAVAILABLE (error severity). */
    badgeIsError: computed(() => {
      const s = store.status();
      if (!s) return false;
      return (
        s.searchState === 'UNREACHABLE' ||
        s.searchState === 'UNAVAILABLE' ||
        s.llmState === 'UNREACHABLE' ||
        s.llmState === 'UNAVAILABLE'
      );
    }),
    /**
     * True when popup should be shown:
     * at least one UNCONFIGURED AND hash not yet acknowledged for this session+state.
     */
    showPopup: computed(() => {
      const s = store.status();
      if (!s) return false;
      const hasUnconfigured =
        s.searchState === 'UNCONFIGURED' || s.llmState === 'UNCONFIGURED';
      if (!hasUnconfigured) return false;
      return statusHash(s) !== store.acknowledgedHash();
    }),
  })),
  withMethods((store) => ({
    handleStatus(payload: ProviderStatusPayload): void {
      patchState(store, { status: payload });
    },
    /** Stores the current hash in localStorage — hides popup until next restart or state change. */
    acknowledge(): void {
      const s = store.status();
      if (!s) return;
      const hash = statusHash(s);
      localStorage.setItem(ACKED_HASH_KEY, hash);
      patchState(store, { acknowledgedHash: hash });
    },
  })),
  withHooks((store) => {
    const sseService = inject(EventSourceServerService) as EventSourceServerService<
      AppSseEventName,
      ProviderStatusPayload
    >;
    return {
      onInit() {
        patchState(store, { acknowledgedHash: localStorage.getItem(ACKED_HASH_KEY) });
        const handler = (payload: ProviderStatusPayload) => store.handleStatus(payload);
        sseService.addEventListener('provider-status', handler);
      },
    };
  }),
);
