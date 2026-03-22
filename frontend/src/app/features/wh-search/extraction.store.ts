import { inject } from '@angular/core';
import { patchState, signalStore, withHooks, withMethods, withState } from '@ngrx/signals';
import { DlExtractionTermDto } from '../../api/model/dlExtractionTermDto';
import { AppSseEventName, DlExtractionDonePayload } from '../../core/sse-events';
import { EventSourceServerService } from '../../shared/utils/event-source-server';
import { DlExtractionService, DlExtractionStatusResponse } from '../../core/dl-extraction.service';
import { LookupResult, ProductLookupService } from '../../core/product-lookup.service';
import { IcecatData, IcecatFeatureGroup, IcecatResponse } from '../../core/model/icecat.model';

interface ExtractionState {
  results: Record<number, DlExtractionTermDto[]>;
  extractionStatus: Record<number, DlExtractionStatusResponse['extractionStatus']>;
  suggestedTerms: Record<number, string>;
  lookupResults: Record<number, LookupResult>;
  lookupLoadingIds: number[];
  fullSpecsLoadingIds: number[];
  fullSpecsLoaded: Record<number, boolean>;
  fullSpecsData: Record<number, IcecatFeatureGroup[]>;
  fullSpecsGeneralInfo: Record<number, IcecatData['GeneralInfo']>;
  icecatCatalogUrls: Record<number, string | undefined>;
}

export const ExtractionStore = signalStore(
  { providedIn: 'root' },
  withState<ExtractionState>({
    results: {},
    extractionStatus: {},
    suggestedTerms: {},
    lookupResults: {},
    lookupLoadingIds: [],
    fullSpecsLoadingIds: [],
    fullSpecsLoaded: {},
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
          const { [whItemId]: _fs, ...restFullSpecs } = s.fullSpecsLoaded;
          const { [whItemId]: _fd, ...restFullSpecsData } = s.fullSpecsData;
          const { [whItemId]: _fg, ...restFullSpecsGeneralInfo } = s.fullSpecsGeneralInfo;
          const { [whItemId]: _cu, ...restCatalogUrls } = s.icecatCatalogUrls;
          return {
            results: restResults,
            extractionStatus: restStatus,
            suggestedTerms: restSuggested,
            lookupResults: restLookup,
            fullSpecsLoaded: restFullSpecs,
            fullSpecsData: restFullSpecsData,
            fullSpecsGeneralInfo: restFullSpecsGeneralInfo,
            icecatCatalogUrls: restCatalogUrls,
            lookupLoadingIds: s.lookupLoadingIds.filter((id) => id !== whItemId),
            fullSpecsLoadingIds: s.fullSpecsLoadingIds.filter((id) => id !== whItemId),
          };
        });
      },
      clear(): void {
        patchState(store, {
          results: {},
          extractionStatus: {},
          suggestedTerms: {},
          lookupResults: {},
          lookupLoadingIds: [],
          fullSpecsLoadingIds: [],
          fullSpecsLoaded: {},
          fullSpecsData: {},
          fullSpecsGeneralInfo: {},
          icecatCatalogUrls: {},
        });
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
        }));
        productLookupService.lookup(listingId, lookupTerm).subscribe({
          next: (result) => {
            // Parse cached full specs if the backend already has them stored
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
              } catch { /* ignore */ }
            }
            patchState(store, (s) => {
              const next: Partial<ExtractionState> = {
                lookupResults: { ...s.lookupResults, [whItemId]: { ...result, lookupTerm } },
                lookupLoadingIds: s.lookupLoadingIds.filter((id) => id !== whItemId),
              };
              if (specsAlreadyCached) {
                next.fullSpecsLoaded = { ...s.fullSpecsLoaded, [whItemId]: true };
                next.fullSpecsData = { ...s.fullSpecsData, [whItemId]: cachedGroups };
                next.fullSpecsGeneralInfo = { ...s.fullSpecsGeneralInfo, [whItemId]: cachedGeneralInfo };
                next.icecatCatalogUrls = { ...s.icecatCatalogUrls, [whItemId]: cachedCatalogUrl };
              }
              return next;
            });
          },
          error: () => {
            patchState(store, (s) => ({
              lookupLoadingIds: s.lookupLoadingIds.filter((id) => id !== whItemId),
            }));
          },
        });
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
              } catch { /* ignore parse errors */ }
            }
            patchState(store, (s) => ({
              fullSpecsLoaded: { ...s.fullSpecsLoaded, [whItemId]: true },
              fullSpecsData: { ...s.fullSpecsData, [whItemId]: featureGroups },
              fullSpecsGeneralInfo: { ...s.fullSpecsGeneralInfo, [whItemId]: generalInfo },
              icecatCatalogUrls: { ...s.icecatCatalogUrls, [whItemId]: catalogUrl },
              fullSpecsLoadingIds: s.fullSpecsLoadingIds.filter((id) => id !== whItemId),
            }));
          },
          error: () => {
            patchState(store, (s) => ({
              fullSpecsLoadingIds: s.fullSpecsLoadingIds.filter((id) => id !== whItemId),
            }));
          },
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
      patchState(store, (s) => {
        const next: Partial<typeof s> = {
          results: {
            ...s.results,
            [whItemId]: [
              ...(s.results[whItemId] ?? []).filter((t) => !incomingModels.has(t.modelName)),
              ...incoming,
            ],
          },
          extractionStatus: { ...s.extractionStatus, [whItemId]: 'DONE' as const },
        };
        // Pre-fill the spec-lookup search term when suggested term arrives via SSE
        if (payload.suggestedTerm && !s.suggestedTerms[whItemId]) {
          next.suggestedTerms = { ...s.suggestedTerms, [whItemId]: payload.suggestedTerm };
        }
        return next;
      });
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
