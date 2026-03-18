import { computed, inject } from '@angular/core';
import { Location } from '@angular/common';
import { NavigationEnd, Router } from '@angular/router';
import { ExtractionStore } from './extraction.store';
import {
  signalStore,
  withState,
  withComputed,
  withMethods,
  withHooks,
  patchState,
} from '@ngrx/signals';
import { filter } from 'rxjs';
import { WhItemDto } from '../../api/model/whItemDto';
import { AppRoutePath } from '../../core/app-route-paths';
import { persistSearch, loadPersistedSearch } from '../../core/search-persistence';
import { SearchQuery } from './search-query.model';
import { LayoutState } from './layout-state.enum';

export const SearchStore = signalStore(
  { providedIn: 'root' },
  withState({
    layoutState: LayoutState.SEARCH,
    listings: [] as WhItemDto[],
    selectedId: null as string | null,
    searchQuery: null as SearchQuery | null,
    loading: false,
    error: null as string | null,
    whTotal: null as number | null,
    searchPatches: {} as Record<number, Partial<WhItemDto>>,
    sortColumn: '',
    sortDirection: '' as 'asc' | 'desc' | '',
    filterDraft: {
      keyword: '',
      rows: 50,
      priceFrom: null as number | null,
      priceTo: null as number | null,
      locationAreaId: undefined as number | undefined,
      categoryWhId: undefined as number | undefined,
      paylivery: false,
    },
  }),
  withComputed((store) => ({
    searchMode: computed(() => store.searchQuery() !== null),
    patchedListings: computed(() => {
      const base = store.listings();
      const patches = store.searchPatches();
      if (!Object.keys(patches).length) return base;
      return base.map((l) => {
        const patch = l.id != null ? patches[l.id] : undefined;
        return patch ? ({ ...l, ...patch } as WhItemDto) : l;
      });
    }),
  })),
  withMethods((store, router = inject(Router), location = inject(Location), extractionStore = inject(ExtractionStore)) => {
    let extractionDebounceTimer: ReturnType<typeof setTimeout> | null = null;

    return {
    search(query: SearchQuery): void {
      patchState(store, {
        searchQuery: query,
        layoutState: LayoutState.LISTINGS,
        searchPatches: {},
      });
      extractionStore.clear();
      persistSearch(store.filterDraft());
      router.navigate(['/', AppRoutePath.LISTINGS]);
    },
    selectListing(id: string): void {
      // Navigation and detail panel are immediate
      router.navigate(['/', AppRoutePath.DETAIL, id]);

      // Debounce extraction scheduling: prevent queue spam from rapid detail clicks
      if (extractionDebounceTimer) clearTimeout(extractionDebounceTimer);
      extractionDebounceTimer = setTimeout(() => {
        // Opening detail already calls scheduleExtraction() via openDetail().
        // This debounce ensures rapid clicks don't create multiple extraction runs.
        extractionDebounceTimer = null;
      }, 400);
    },
    backToListings(): void {
      router.navigate(['/', AppRoutePath.LISTINGS]);
    },
    setFilterDraft(patch: {
      keyword?: string;
      rows?: number;
      priceFrom?: number | null;
      priceTo?: number | null;
      locationAreaId?: number | undefined;
      categoryWhId?: number | undefined;
      paylivery?: boolean;
    }): void {
      patchState(store, (s) => ({ filterDraft: { ...s.filterDraft, ...patch } }));
    },
    clearSearch(): void {
      patchState(store, {
        searchQuery: null,
        listings: [],
        selectedId: null,
        layoutState: LayoutState.SEARCH,
        whTotal: null,
        searchPatches: {},
        filterDraft: {
          keyword: '',
          rows: 50,
          priceFrom: null,
          priceTo: null,
          locationAreaId: undefined,
          categoryWhId: undefined,
          paylivery: false,
        },
      });
      extractionStore.clear();
      router.navigate(['/']);
    },
    setSortColumn(col: string): void {
      patchState(store, { sortColumn: col });
      if (col && !store.sortDirection()) patchState(store, { sortDirection: 'asc' });
      if (!col) patchState(store, { sortDirection: '' });
    },
    setSortDirection(dir: 'asc' | 'desc' | ''): void {
      patchState(store, { sortDirection: dir });
    },
    // Internal: called by MainLayoutComponent to sync httpResource state
    setResourceState(state: {
      listings?: WhItemDto[];
      loading?: boolean;
      error?: string | null;
      whTotal?: number | null;
    }): void {
      patchState(store, state);
    },
    // Internal: apply a patch to a single listing (rating/view updates in search mode)
    applySearchPatch(id: number, patch: Partial<WhItemDto>): void {
      patchState(store, (s) => ({
        searchPatches: { ...s.searchPatches, [id]: { ...s.searchPatches[id], ...patch } },
      }));
    },
    removeListing(id: number): void {
      patchState(store, (s) => ({
        listings: s.listings.filter((l) => l.id !== id),
        searchPatches: (() => {
          const { [id]: _, ...rest } = s.searchPatches;
          return rest;
        })(),
      }));
    },
    advanceToNext(): void {
      const listings = store.patchedListings();
      const currentId = store.selectedId();
      const currentIdx = listings.findIndex((l) => l.id?.toString() === currentId);
      for (let i = currentIdx + 1; i < listings.length; i++) {
        if (listings[i].rating !== 'DOWN') {
          router.navigate(['/', AppRoutePath.DETAIL, listings[i].id!.toString()], {
            replaceUrl: true,
          });
          return;
        }
      }
      for (let i = currentIdx - 1; i >= 0; i--) {
        if (listings[i].rating !== 'DOWN') {
          router.navigate(['/', AppRoutePath.DETAIL, listings[i].id!.toString()], {
            replaceUrl: true,
          });
          return;
        }
      }
      router.navigate(['/', AppRoutePath.LISTINGS]);
    },
  };
  }),
  withHooks((store) => {
    const router = inject(Router);
    return {
      onInit() {
        const saved = loadPersistedSearch();
        if (saved) {
          // Ensure locationAreaId/categoryWhId keys exist — JSON strips undefined values,
          // which would break ngrx/signals deep signal proxies for those keys.
          patchState(store, {
            filterDraft: { locationAreaId: undefined, categoryWhId: undefined, ...saved },
          });
        }

        // URL is the single source of truth for selectedId.
        // Store → Route is handled by the methods above; this covers Route → Store.
        const detailPrefix = `/${AppRoutePath.DETAIL}/`;
        router.events
          .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
          .subscribe((e) => {
            const url = e.urlAfterRedirects;
            if (url.startsWith(detailPrefix)) {
              const id = decodeURIComponent(url.slice(detailPrefix.length));
              patchState(store, { layoutState: LayoutState.DETAIL, selectedId: id });
            } else if (url === `/${AppRoutePath.LISTINGS}`) {
              patchState(store, { layoutState: LayoutState.LISTINGS, selectedId: null });
            } else {
              patchState(store, { layoutState: LayoutState.SEARCH, selectedId: null });
            }
          });
      },
    };
  }),
);
