import { ChangeDetectionStrategy, Component, inject, signal, computed } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { ListingService } from '../../core/listing.service';
import { QuercheckerListingDto } from '../../api/model/quercheckerListingDto';
import { API_URLS } from '../../core/api-urls';
import { WhFilterComponent, SearchParams } from './wh-filter/wh-filter.component';
import { WhListingsComponent } from './wh-listings/wh-listings.component';
import { WhSortComponent } from './wh-sort/wh-sort.component';
import { ZoneLeftComponent } from '../../shared/layout/zone-left/zone-left.component';
import { ZoneRightComponent } from '../../shared/layout/zone-right/zone-right.component';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-wh-search',
  imports: [ZoneLeftComponent, ZoneRightComponent, WhFilterComponent, WhListingsComponent, WhSortComponent],
  templateUrl: './wh-search.component.html',
  styleUrl: './wh-search.component.scss',
})
export class WhSearchComponent {
  private readonly listingService = inject(ListingService);

  searchParams = signal<SearchParams | null>(null);
  filterText = signal('');
  freeOnly = signal(false);
  sortColumn = signal('');
  sortDirection = signal<'asc' | 'desc' | ''>('');

  searchMode = computed(() => this.searchParams() !== null);

  private allResource = httpResource<QuercheckerListingDto[]>(
    () => (!this.searchMode() ? { url: API_URLS.listings } : undefined),
    { defaultValue: [] },
  );

  private searchResource = httpResource<QuercheckerListingDto[]>(
    () => {
      const p = this.searchParams();
      if (!p) return undefined;
      const params: Record<string, string | number> = { keyword: p.keyword, rows: p.rows };
      if (p.priceFrom != null) params['priceFrom'] = p.priceFrom;
      if (p.priceTo != null) params['priceTo'] = p.priceTo;
      if (p.locationAreaId != null) params['areaId'] = p.locationAreaId;
      return { url: API_URLS.whSearch, params };
    },
    { defaultValue: [] },
  );

  loading = computed(() =>
    this.searchMode() ? this.searchResource.isLoading() : this.allResource.isLoading(),
  );

  error = computed(() => {
    const e = this.searchMode() ? this.searchResource.error() : this.allResource.error();
    if (!e) return null;
    return e instanceof Error ? e.message : 'Fehler beim Laden der Daten.';
  });

  private rawListings = computed(
    () => (this.searchMode() ? this.searchResource.value() : this.allResource.value()) ?? [],
  );

  filteredListings = computed(() => {
    const filter = this.filterText().toLowerCase();
    const free = this.freeOnly();
    return this.rawListings().filter((l) => {
      if (free && l.price !== 0) return false;
      if (!filter) return true;
      return (
        (l.title ?? '').toLowerCase().includes(filter) ||
        (l.location ?? '').toLowerCase().includes(filter)
      );
    });
  });

  sortedListings = computed(() => {
    const data = this.filteredListings();
    const col = this.sortColumn() as keyof QuercheckerListingDto;
    const dir = this.sortDirection();
    if (!col || !dir) return data;
    return [...data].sort((a, b) => {
      const av = a[col];
      const bv = b[col];
      if (av == null && bv == null) return 0;
      if (av == null) return 1;
      if (bv == null) return -1;
      const cmp =
        typeof av === 'number' && typeof bv === 'number'
          ? av - bv
          : String(av).localeCompare(String(bv));
      return dir === 'asc' ? cmp : -cmp;
    });
  });

  count = computed(() => this.filteredListings().length);

  onSearch(params: SearchParams): void {
    this.searchParams.set(params);
    this.filterText.set('');
    this.freeOnly.set(false);
  }

  showAllListings(): void {
    this.searchParams.set(null);
  }

  deleteListing(listing: QuercheckerListingDto): void {
    if (!listing.id) return;
    this.listingService.delete(listing.id).subscribe({
      next: () => {
        this.searchMode() ? this.searchResource.reload() : this.allResource.reload();
      },
      error: (err) => console.error('Fehler beim Löschen:', err),
    });
  }
}
