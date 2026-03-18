import { inject } from '@angular/core';
import { patchState, signalStore, withHooks, withMethods, withState } from '@ngrx/signals';
import { DlExtractionTermDto } from '../../api/model/dlExtractionTermDto';
import { AppSseEventName, DlExtractionDonePayload } from '../../core/sse-events';
import { EventSourceServerService } from '../../shared/utils/event-source-server';
import { DlExtractionService, DlExtractionStatusResponse } from '../../core/dl-extraction.service';

interface ExtractionState {
  results: Record<number, DlExtractionTermDto[]>;
  extractionStatus: Record<number, DlExtractionStatusResponse['extractionStatus']>;
}

export const ExtractionStore = signalStore(
  { providedIn: 'root' },
  withState<ExtractionState>({ results: {}, extractionStatus: {} }),
  withMethods((store) => {
    const dlService = inject(DlExtractionService);
    return {
      remove(whItemId: number): void {
        patchState(store, (s) => {
          const { [whItemId]: _r, ...restResults } = s.results;
          const { [whItemId]: _s, ...restStatus } = s.extractionStatus;
          return { results: restResults, extractionStatus: restStatus };
        });
      },
      clear(): void {
        patchState(store, { results: {}, extractionStatus: {} });
      },
      loadExistingTerms(whItemId: number): void {
        dlService.getTerms(whItemId).subscribe((response) => {
          patchState(store, (s) => ({
            extractionStatus: { ...s.extractionStatus, [whItemId]: response.extractionStatus },
          }));
          if (response.terms && response.terms.length > 0) {
            patchState(store, (s) => ({
              results: { ...s.results, [whItemId]: response.terms },
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
      const whItemId = payload?.whItemId;
      if (whItemId == null) return;
      const incoming = payload.terms ?? [];
      // Replace entries for this model, keep others — handles retries cleanly
      const incomingModels = new Set(incoming.map((t) => t.modelName));
      patchState(store, (s) => ({
        results: {
          ...s.results,
          [whItemId]: [
            ...(s.results[whItemId] ?? []).filter((t) => !incomingModels.has(t.modelName)),
            ...incoming,
          ],
        },
        extractionStatus: { ...s.extractionStatus, [whItemId]: 'DONE' as const },
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
