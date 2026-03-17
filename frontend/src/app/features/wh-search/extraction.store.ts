import { inject } from '@angular/core';
import { patchState, signalStore, withHooks, withMethods, withState } from '@ngrx/signals';
import { DlExtractionTermDto } from '../../api/model/dlExtractionTermDto';
import { AppSseEventName, DlExtractionDonePayload } from '../../core/sse-events';
import { EventSourceServerService } from '../../shared/utils/event-source-server';
import { DlExtractionService } from '../../core/dl-extraction.service';

interface ExtractionState {
  results: Record<number, DlExtractionTermDto[]>;
}

export const ExtractionStore = signalStore(
  { providedIn: 'root' },
  withState<ExtractionState>({ results: {} }),
  withMethods((store) => {
    const dlService = inject(DlExtractionService);
    return {
      remove(itemTextId: number): void {
        patchState(store, (s) => {
          const { [itemTextId]: _, ...rest } = s.results;
          return { results: rest };
        });
      },
      clear(): void {
        patchState(store, { results: {} });
      },
      loadExistingTerms(itemTextId: number): void {
        dlService.getTerms(itemTextId).subscribe((terms) => {
          if (terms && terms.length > 0) {
            patchState(store, (s) => ({
              results: {
                ...s.results,
                [itemTextId]: terms,
              },
            }));
          }
        });
      },
    };
  }),
  withHooks((store) => {
    const sseService = inject(
      EventSourceServerService,
    ) as EventSourceServerService<AppSseEventName, DlExtractionDonePayload>;

    const onDlExtract = (payload: DlExtractionDonePayload): void => {
      const itemTextId = payload?.itemTextId;
      if (itemTextId == null) return;
      const incoming = payload.terms ?? [];
      // Replace entries for this model, keep others — handles retries cleanly
      const incomingModels = new Set(incoming.map((t) => t.modelName));
      patchState(store, (s) => ({
        results: {
          ...s.results,
          [itemTextId]: [
            ...(s.results[itemTextId] ?? []).filter((t) => !incomingModels.has(t.modelName)),
            ...incoming,
          ],
        },
      }));
    };

    return {
      onInit() {
        sseService.addEventListener('dl-extract', onDlExtract);
      },
      onDestroy() {
        sseService.deleteEventListener('dl-extract', onDlExtract);
      },
    };
  }),
);
