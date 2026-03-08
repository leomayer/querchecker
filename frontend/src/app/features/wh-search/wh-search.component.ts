import { ChangeDetectionStrategy, Component, inject, signal, computed } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { MatDialog } from '@angular/material/dialog';
import { ListingService } from '../../core/listing.service';
import { QuercheckerListingDto } from '../../api/model/quercheckerListingDto';
import { WhSearchResultDto } from '../../api/model/whSearchResultDto';
import { API_URLS } from '../../core/api-urls';
import { WhFilterComponent, SearchParams } from './wh-filter/wh-filter.component';
import { WhListingsComponent, RatingFilter } from './wh-listings/wh-listings.component';
import { RatingChangedEvent } from './wh-listings/listing-card/listing-card.component';
import { WhSortComponent } from './wh-sort/wh-sort.component';
import { ZoneLeftComponent } from '../../shared/layout/zone-left/zone-left.component';
import { ZoneRightComponent } from '../../shared/layout/zone-right/zone-right.component';
import { ListingDetailDialogComponent } from './listing-detail-dialog/listing-detail-dialog.component';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-wh-search',
  imports: [ZoneLeftComponent, ZoneRightComponent, WhFilterComponent, WhListingsComponent, WhSortComponent],
  templateUrl: './wh-search.component.html',
  styleUrl: './wh-search.component.scss',
})
export class WhSearchComponent {
  private readonly listingService = inject(ListingService);
  private readonly dialog = inject(MatDialog);

  searchParams = signal<SearchParams | null>(null);
  filterText = signal('');
  freeOnly = signal(false);
  ratingFilter = signal<RatingFilter>('UP_NULL');
  sortColumn = signal('');
  sortDirection = signal<'asc' | 'desc' | ''>('');

  searchMode = computed(() => this.searchParams() !== null);

  private allResource = httpResource<QuercheckerListingDto[]>(
    () => (!this.searchMode() ? { url: API_URLS.listings, params: { ratingFilter: this.ratingFilter() } } : undefined),
    { defaultValue: [] },
  );

  private searchResource = httpResource<WhSearchResultDto>(
    () => {
      const p = this.searchParams();
      if (!p) return undefined;
      const params: Record<string, string | number> = { keyword: p.keyword, rows: p.rows };
      if (p.priceFrom != null) params['priceFrom'] = p.priceFrom;
      if (p.priceTo != null) params['priceTo'] = p.priceTo;
      if (p.locationAreaId != null) params['areaId'] = p.locationAreaId;
      if (p.categoryWhId != null) params['attributeTree'] = p.categoryWhId;
      if (p.paylivery) params['paylivery'] = 'true';
      return { url: API_URLS.whSearch, params };
    },
  );

  // Lokale Patches für Such-Modus (Rating, View-Stats, etc.) – kein neuer WH-Request nötig
  private searchPatches = signal<Record<number, Partial<QuercheckerListingDto>>>({});

  loading = computed(() =>
    this.searchMode() ? this.searchResource.isLoading() : this.allResource.isLoading(),
  );

  error = computed(() => {
    const e = this.searchMode() ? this.searchResource.error() : this.allResource.error();
    if (!e) return null;
    return e instanceof Error ? e.message : 'Fehler beim Laden der Daten.';
  });

  whTotal = computed(() => this.searchResource.value()?.totalCount ?? null);

  private rawListings = computed(() => {
    const base = (this.searchMode() ? (this.searchResource.value()?.listings ?? []) : this.allResource.value()) ?? [];
    if (!this.searchMode()) return base;
    const patches = this.searchPatches();
    if (!Object.keys(patches).length) return base;
    return base.map(l => {
      const patch = l.id != null ? patches[l.id] : undefined;
      return patch ? ({ ...l, ...patch } as QuercheckerListingDto) : l;
    });
  });

  filteredListings = computed(() => {
    const filter = this.filterText().toLowerCase();
    const free = this.freeOnly();
    const ratingF = this.ratingFilter();
    return this.rawListings().filter((l) => {
      if (this.searchMode()) {
        if (ratingF === 'UP' && l.rating !== 'UP') return false;
        if (ratingF === 'UP_NULL' && l.rating !== 'UP' && l.rating != null) return false;
        if (ratingF === 'DOWN' && l.rating !== 'DOWN') return false;
      }
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
    this.searchPatches.set({});
  }

  showAllListings(): void {
    this.searchParams.set(null);
  }

  reloadAfterRating(event: RatingChangedEvent): void {
    if (this.searchMode()) {
      this.searchPatches.update(p => ({
        ...p,
        [event.id]: { ...p[event.id], rating: event.newRating ?? undefined },
      }));
    } else {
      this.allResource.reload();
    }
  }

  openDetail(listing: QuercheckerListingDto): void {
    const viewRecorded = this.listingService.shouldRecordView(listing.id!);
    if (viewRecorded) {
      this.listingService.recordView(listing.id!).subscribe();
    }
    const ref = this.dialog.open(ListingDetailDialogComponent, {
      data: { listing },
      width: '600px',
      maxWidth: '95vw',
    });
    ref.afterClosed().subscribe((result) => {
      if (result === 'deleted') {
        this.searchMode() ? this.searchResource.reload() : this.allResource.reload();
        return;
      }
      if (this.searchMode()) {
        if (viewRecorded || result === 'saved') {
          this.listingService.getDetail(listing.id!).subscribe(detail => {
            this.searchPatches.update(p => ({
              ...p,
              [listing.id!]: {
                ...p[listing.id!],
                viewCount: detail.viewCount,
                lastViewedAt: detail.lastViewedAt,
                hasNote: !!(detail.note?.trim()),
              },
            }));
          });
        }
      } else {
        if (viewRecorded || result === 'saved') {
          this.allResource.reload();
        }
      }
    });
  }
}
