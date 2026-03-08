import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { signalStore, withState, withComputed, withMethods, patchState } from '@ngrx/signals';
import { computed } from '@angular/core';
import { QuercheckerListingDto } from '../../api/model/quercheckerListingDto';
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
  withMethods((store, router = inject(Router)) => ({
    search(query: SearchQuery): void {
      patchState(store, { searchQuery: query, layoutState: LayoutState.LISTINGS, searchPatches: {} });
      // TODO: router.navigate(['/wh-listings']);
    },
    selectListing(id: string): void {
      patchState(store, { selectedId: id, layoutState: LayoutState.DETAIL });
      // TODO: router.navigate(['/wh-listings', id]);
    },
    backToListings(): void {
      patchState(store, { selectedId: null, layoutState: LayoutState.LISTINGS });
      // TODO: router.navigate(['/wh-listings']);
    },
    clearSearch(): void {
      patchState(store, {
        searchQuery: null,
        listings: [],
        selectedId: null,
        layoutState: LayoutState.SEARCH,
        whTotal: null,
        searchPatches: {},
      });
      // TODO: router.navigate(['/']);
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
          patchState(store, { selectedId: listings[i].id!.toString() });
          return;
        }
      }
      for (let i = currentIdx - 1; i >= 0; i--) {
        if (listings[i].rating !== 'DOWN') {
          patchState(store, { selectedId: listings[i].id!.toString() });
          return;
        }
      }
      patchState(store, { selectedId: null, layoutState: LayoutState.LISTINGS });
    },
    // TODO: withHooks for Router → Store sync (not part of this refactoring)
  })),
);
