import { computed, inject } from '@angular/core';
import { Location } from '@angular/common';
import { NavigationEnd, Router } from '@angular/router';
import { signalStore, withState, withComputed, withMethods, withHooks, patchState } from '@ngrx/signals';
import { filter } from 'rxjs';
import { QuercheckerListingDto } from '../../api/model/quercheckerListingDto';
import { AppRoutePath } from '../../core/app-route-paths';
import { SearchQuery } from './search-query.model';
import { LayoutState } from './layout-state.enum';

export const SearchStore = signalStore(
  { providedIn: 'root' },
  withState({
    layoutState: LayoutState.SEARCH,
    listings: [] as QuercheckerListingDto[],
    selectedId: null as string | null,
    searchQuery: null as SearchQuery | null,
    loading: false,
    error: null as string | null,
    whTotal: null as number | null,
    searchPatches: {} as Record<number, Partial<QuercheckerListingDto>>,
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
        return patch ? ({ ...l, ...patch } as QuercheckerListingDto) : l;
      });
    }),
  })),
  withMethods((store, router = inject(Router), location = inject(Location)) => ({
    search(query: SearchQuery): void {
      patchState(store, { searchQuery: query, layoutState: LayoutState.LISTINGS, searchPatches: {} });
      router.navigate(['/', AppRoutePath.LISTINGS]);
    },
    selectListing(id: string): void {
      router.navigate(['/', AppRoutePath.DETAIL, id]);
    },
    backToListings(): void {
      location.back();
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
        filterDraft: { keyword: '', rows: 50, priceFrom: null, priceTo: null, locationAreaId: undefined, categoryWhId: undefined, paylivery: false },
      });
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
      listings?: QuercheckerListingDto[];
      loading?: boolean;
      error?: string | null;
      whTotal?: number | null;
    }): void {
      patchState(store, state);
    },
    // Internal: apply a patch to a single listing (rating/view updates in search mode)
    applySearchPatch(id: number, patch: Partial<QuercheckerListingDto>): void {
      patchState(store, (s) => ({
        searchPatches: { ...s.searchPatches, [id]: { ...s.searchPatches[id], ...patch } },
      }));
    },
    advanceToNext(): void {
      const listings = store.patchedListings();
      const currentId = store.selectedId();
      const currentIdx = listings.findIndex((l) => l.id?.toString() === currentId);
      for (let i = currentIdx + 1; i < listings.length; i++) {
        if (listings[i].rating !== 'DOWN') {
          router.navigate(['/', AppRoutePath.DETAIL, listings[i].id!.toString()], { replaceUrl: true });
          return;
        }
      }
      for (let i = currentIdx - 1; i >= 0; i--) {
        if (listings[i].rating !== 'DOWN') {
          router.navigate(['/', AppRoutePath.DETAIL, listings[i].id!.toString()], { replaceUrl: true });
          return;
        }
      }
      router.navigate(['/', AppRoutePath.LISTINGS]);
    },
  })),
  withHooks((store) => {
    const router = inject(Router);
    return {
      onInit() {
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
