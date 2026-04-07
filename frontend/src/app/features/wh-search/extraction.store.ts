import { inject } from '@angular/core';
import { patchState, signalStore, withHooks, withMethods, withState } from '@ngrx/signals';
import { withLookupHistory } from './lookup-history.feature';
import { DlExtractionTermDto } from '../../api/model/dlExtractionTermDto';
import {
  AppSseEventName,
  DlExtractionDonePayload,
  ErrorNotificationPayload,
  LookupHistoryEntry,
  LookupResultPayload,
  SseEvent,
} from '../../core/sse-events';
import { EventSourceServerService } from '../../shared/utils/event-source-server';
import { DlExtractionService, DlExtractionStatusResponse } from '../../core/dl-extraction.service';
import { LookupResult, ProductLookupService } from '../../core/product-lookup.service';
import { SpecsFeatureGroup } from '../../core/model/lookup.model';
import { IcecatData, IcecatFeatureGroup, IcecatResponse } from '../../core/model/icecat.model';

interface ExtractionState {
  results: Record<number, DlExtractionTermDto[]>;
  extractionStatus: Record<number, DlExtractionStatusResponse['extractionStatus']>;
  suggestedTerms: Record<number, string>;
  lookupResults: Record<number, LookupResult>;
  lookupLoadingIds: number[];
  lookupTimestamps: Record<number, number>;
  /** Maps listingId → whItemId so SSE lookup-result events can update the store. */
  listingToWhItemId: Record<number, number>;
  fullSpecsLoadingIds: number[];
  fullSpecsLoaded: Record<number, boolean>;
  fullSpecsTimestamps: Record<number, number>;
  fullSpecsData: Record<number, IcecatFeatureGroup[]>;
  fullSpecsGeneralInfo: Record<number, IcecatData['GeneralInfo']>;
  icecatCatalogUrls: Record<number, string | undefined>;
}

export const ExtractionStore = signalStore(
  { providedIn: 'root' },
  withLookupHistory(),
  withState<ExtractionState>({
    results: {},
    extractionStatus: {},
    suggestedTerms: {},
    lookupResults: {},
    lookupLoadingIds: [],
    lookupTimestamps: {},
    listingToWhItemId: {},
    fullSpecsLoadingIds: [],
    fullSpecsLoaded: {},
    fullSpecsTimestamps: {},
    fullSpecsData: {},
    fullSpecsGeneralInfo: {},
    icecatCatalogUrls: {},
  }),
  withMethods((store) => {
    const dlService = inject(DlExtractionService);
    const productLookupService = inject(ProductLookupService);
    return {
      remove(whItemId: number): void {
        patchState(store, (s) => {
          const { [whItemId]: _r, ...restResults } = s.results;
          const { [whItemId]: _s, ...restStatus } = s.extractionStatus;
          const { [whItemId]: _t, ...restSuggested } = s.suggestedTerms;
          const { [whItemId]: _l, ...restLookup } = s.lookupResults;
          const { [whItemId]: _lt, ...restLookupTimestamps } = s.lookupTimestamps;
          const restListingMap = Object.fromEntries(
            Object.entries(s.listingToWhItemId).filter(([, v]) => v !== whItemId),
          );
          const { [whItemId]: _fs, ...restFullSpecs } = s.fullSpecsLoaded;
          const { [whItemId]: _ft, ...restFullSpecsTimestamps } = s.fullSpecsTimestamps;
          const { [whItemId]: _fd, ...restFullSpecsData } = s.fullSpecsData;
          const { [whItemId]: _fg, ...restFullSpecsGeneralInfo } = s.fullSpecsGeneralInfo;
          const { [whItemId]: _cu, ...restCatalogUrls } = s.icecatCatalogUrls;
          return {
            results: restResults,
            extractionStatus: restStatus,
            suggestedTerms: restSuggested,
            lookupResults: restLookup,
            lookupTimestamps: restLookupTimestamps,
            listingToWhItemId: restListingMap,
            fullSpecsLoaded: restFullSpecs,
            fullSpecsTimestamps: restFullSpecsTimestamps,
            fullSpecsData: restFullSpecsData,
            fullSpecsGeneralInfo: restFullSpecsGeneralInfo,
            icecatCatalogUrls: restCatalogUrls,
            lookupLoadingIds: s.lookupLoadingIds.filter((id) => id !== whItemId),
            fullSpecsLoadingIds: s.fullSpecsLoadingIds.filter((id) => id !== whItemId),
          };
        });
        store.removeHistory(whItemId);
      },
      clear(): void {
        patchState(store, {
          results: {},
          extractionStatus: {},
          suggestedTerms: {},
          lookupResults: {},
          lookupLoadingIds: [],
          lookupTimestamps: {},
          listingToWhItemId: {},
          fullSpecsLoadingIds: [],
          fullSpecsLoaded: {},
          fullSpecsTimestamps: {},
          fullSpecsData: {},
          fullSpecsGeneralInfo: {},
          icecatCatalogUrls: {},
        });
        store.clearHistory();
      },
      loadExistingTerms(whItemId: number): void {
        dlService.getTerms(whItemId).subscribe((response) => {
          patchState(store, (s) => {
            const current = s.extractionStatus[whItemId];
            // Don't overwrite a terminal status (DONE/FAILED) already set by SSE
            // — the GET response may be stale if it raced with the SSE.
            const terminal = current === 'DONE' || current === 'FAILED';
            return {
              extractionStatus: terminal
                ? s.extractionStatus
                : { ...s.extractionStatus, [whItemId]: response.extractionStatus },
            };
          });
          if (response.terms && response.terms.length > 0) {
            patchState(store, (s) => ({
              results: { ...s.results, [whItemId]: response.terms },
            }));
          }
          if (response.suggestedTerm) {
            patchState(store, (s) => ({
              suggestedTerms: { ...s.suggestedTerms, [whItemId]: response.suggestedTerm! },
            }));
          }
        });
      },
      lookup(whItemId: number, listingId: number, lookupTerm: string): void {
        patchState(store, (s) => ({
          lookupLoadingIds: [...s.lookupLoadingIds, whItemId],
          listingToWhItemId: { ...s.listingToWhItemId, [listingId]: whItemId },
        }));
        productLookupService.lookup(listingId, lookupTerm).subscribe({
          next: (result) => {
            // Parse cached full specs if the backend already has them stored (Icecat path)
            let cachedGroups: IcecatFeatureGroup[] = [];
            let cachedGeneralInfo: IcecatData['GeneralInfo'] = undefined;
            let cachedCatalogUrl: string | undefined;
            const specsAlreadyCached = !!result.icecatSpecsJson;
            if (specsAlreadyCached) {
              try {
                const parsed = JSON.parse(result.icecatSpecsJson!) as IcecatResponse;
                cachedGroups = parsed?.data?.FeaturesGroups ?? [];
                cachedGeneralInfo = parsed?.data?.GeneralInfo;
                cachedCatalogUrl = parsed?.data?.CatalogObjectCloud?.ProductPage?.URL;
              } catch {
                /* ignore */
              }
            }

            // Parse featureGroups from HTML-Fetch path (GSMArena, FlatpanelsHD)
            let featureGroups: SpecsFeatureGroup[] | null = null;
            if (result.featureGroupsJson) {
              try {
                featureGroups = JSON.parse(result.featureGroupsJson) as SpecsFeatureGroup[];
              } catch {
                /* ignore */
              }
            }

            const lookupResultWithExtras: LookupResult = {
              ...result,
              lookupTerm,
              featureGroups: featureGroups ?? result.featureGroups ?? null,
              retryProvider: result.retryProvider ?? null,
              retryModel: result.retryModel ?? null,
            };

            patchState(store, (s) => {
              const next: Partial<ExtractionState> = {
                lookupResults: { ...s.lookupResults, [whItemId]: lookupResultWithExtras },
                lookupTimestamps: { ...s.lookupTimestamps, [whItemId]: Date.now() },
                lookupLoadingIds: s.lookupLoadingIds.filter((id) => id !== whItemId),
              };
              if (specsAlreadyCached) {
                next.fullSpecsLoaded = { ...s.fullSpecsLoaded, [whItemId]: true };
                next.fullSpecsData = { ...s.fullSpecsData, [whItemId]: cachedGroups };
                next.fullSpecsGeneralInfo = {
                  ...s.fullSpecsGeneralInfo,
                  [whItemId]: cachedGeneralInfo,
                };
                next.icecatCatalogUrls = { ...s.icecatCatalogUrls, [whItemId]: cachedCatalogUrl };
              }
              return next;
            });
            if (result.history) {
              store.setHistory(whItemId, result.history);
            }
          },
          error: () => {
            patchState(store, (s) => ({
              lookupLoadingIds: s.lookupLoadingIds.filter((id) => id !== whItemId),
            }));
          },
        });
      },
      restoreLookupResult(whItemId: number, entry: LookupHistoryEntry): void {
        let featureGroups = null;
        if (entry.featureGroupsJson) {
          try { featureGroups = JSON.parse(entry.featureGroupsJson); } catch { /* ignore */ }
        }
        const result = {
          lookupStatus: entry.lookupStatus as LookupResult['lookupStatus'],
          quickFacts: entry.quickFacts ?? {},
          icecatId: entry.icecatId,
          sourceType: entry.sourceType,
          sourceDomain: entry.sourceDomain,
          siteLabel: entry.siteLabel,
          sourceUrl: entry.sourceUrl,
          featureGroupsJson: entry.featureGroupsJson,
          featureGroups,
          lookupTerm: entry.lookupTerm,
          retryProvider: null,
          retryModel: null,
        };
        patchState(store, (s) => ({
          lookupResults: { ...s.lookupResults, [whItemId]: result },
          lookupTimestamps: { ...s.lookupTimestamps, [whItemId]: Date.now() },
        }));
      },
      loadFullSpecs(whItemId: number, listingId: number, icecatId: string): void {
        patchState(store, (s) => ({
          fullSpecsLoadingIds: [...s.fullSpecsLoadingIds, whItemId],
        }));
        productLookupService.loadFullSpecs(listingId, icecatId).subscribe({
          next: (result) => {
            let featureGroups: IcecatFeatureGroup[] = [];
            let generalInfo: IcecatData['GeneralInfo'] = undefined;
            let catalogUrl: string | undefined;
            if (result.icecatSpecsJson) {
              try {
                const parsed = JSON.parse(result.icecatSpecsJson) as IcecatResponse;
                featureGroups = parsed?.data?.FeaturesGroups ?? [];
                generalInfo = parsed?.data?.GeneralInfo;
                catalogUrl = parsed?.data?.CatalogObjectCloud?.ProductPage?.URL;
              } catch {
                /* ignore parse errors */
              }
            }
            patchState(store, (s) => ({
              fullSpecsLoaded: { ...s.fullSpecsLoaded, [whItemId]: true },
              fullSpecsTimestamps: { ...s.fullSpecsTimestamps, [whItemId]: Date.now() },
              fullSpecsData: { ...s.fullSpecsData, [whItemId]: featureGroups },
              fullSpecsGeneralInfo: { ...s.fullSpecsGeneralInfo, [whItemId]: generalInfo },
              icecatCatalogUrls: { ...s.icecatCatalogUrls, [whItemId]: catalogUrl },
              fullSpecsLoadingIds: s.fullSpecsLoadingIds.filter((id) => id !== whItemId),
            }));
          },
          error: () => {
            // Mark as loaded (with no data) so the button does not reappear and re-trigger the
            // same failing request. Also clear icecatId so showFullSpecsButton() returns false.
            patchState(store, (s) => {
              const existing = s.lookupResults[whItemId];
              return {
                fullSpecsLoadingIds: s.fullSpecsLoadingIds.filter((id) => id !== whItemId),
                fullSpecsLoaded: { ...s.fullSpecsLoaded, [whItemId]: true },
                lookupResults: existing
                  ? { ...s.lookupResults, [whItemId]: { ...existing, icecatId: null } }
                  : s.lookupResults,
              };
            });
          },
        });
      },
    };
  }),
  withHooks((store) => {
    const sseService = inject(EventSourceServerService) as EventSourceServerService<
      AppSseEventName,
      DlExtractionDonePayload
    >;

    const onDlExtract = (payload: DlExtractionDonePayload): void => {
      const whItemId = payload?.whItemId;
      if (whItemId == null) return;
      const incoming = payload.terms ?? [];
      // Replace entries for this model, keep others — handles retries cleanly
      const incomingModels = new Set(incoming.map((t) => t.modelName));
      patchState(store, (s) => {
        const next: Partial<typeof s> = {
          results: {
            ...s.results,
            [whItemId]: [
              ...(s.results[whItemId] ?? []).filter((t) => !incomingModels.has(t.modelName)),
              ...incoming,
            ],
          },
          extractionStatus: {
            ...s.extractionStatus,
            [whItemId]: (payload.modelStatus ??
              'DONE') as DlExtractionStatusResponse['extractionStatus'],
          },
        };
        // Pre-fill the spec-lookup search term when suggested term arrives via SSE
        if (payload.suggestedTerm && !s.suggestedTerms[whItemId]) {
          next.suggestedTerms = { ...s.suggestedTerms, [whItemId]: payload.suggestedTerm };
        }
        return next;
      });
    };

    const onLookupResult = (payload: LookupResultPayload): void => {
      const listingId = payload?.listingId;
      if (listingId == null) return;
      const whItemId = store.listingToWhItemId()[listingId];
      if (whItemId == null) return;

      let featureGroups = null;
      if (payload.featureGroupsJson) {
        try {
          featureGroups = JSON.parse(payload.featureGroupsJson);
        } catch {
          /* ignore */
        }
      }

      const lookupResult: LookupResult = {
        lookupStatus: payload.lookupStatus as LookupResult['lookupStatus'],
        quickFacts: payload.quickFacts ?? {},
        icecatId: payload.icecatId,
        sourceType: payload.sourceType,
        sourceDomain: payload.sourceDomain,
        siteLabel: payload.siteLabel,
        sourceUrl: payload.sourceUrl,
        featureGroupsJson: payload.featureGroupsJson,
        featureGroups,
        lookupTerm: store.lookupResults()[whItemId]?.lookupTerm,
        retryProvider: payload.retryProvider,
        retryModel: payload.retryModel,
      };

      patchState(store, (s) => ({
        lookupResults: { ...s.lookupResults, [whItemId]: lookupResult },
        lookupTimestamps: { ...s.lookupTimestamps, [whItemId]: Date.now() },
      }));
      if (payload.history) {
        store.setHistory(whItemId, payload.history);
      }
    };

    const onErrorNotification = (event: SseEvent<ErrorNotificationPayload>): void => {
      // handled by ErrorNotificationService (snackbars)
    };

    return {
      onInit() {
        sseService.addEventListener('dl-extract', onDlExtract);
        sseService.addEventListener('lookup-result', onLookupResult as never);
        sseService.addEventListener('error-notification', onErrorNotification as never);
      },
      onDestroy() {
        sseService.deleteEventListener('dl-extract', onDlExtract);
        sseService.deleteEventListener('lookup-result', onLookupResult as never);
        sseService.deleteEventListener('error-notification', onErrorNotification as never);
      },
    };
  }),
);
