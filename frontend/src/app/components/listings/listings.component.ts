import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { ListingService, QuercheckerListingDto } from '../../services/listing.service';
import { WhSearchComponent } from '../wh-search/wh-search.component';
import { ListingFilterComponent } from '../listing-filter/listing-filter.component';
import { CustomCurrencyPipe } from '../../pipes/custom-currency/custom-currency-pipe';

@Component({
  selector: 'app-listings',
  standalone: true,
  imports: [
    MatTableModule,
    MatSortModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    DatePipe,
    WhSearchComponent,
    ListingFilterComponent,
    CustomCurrencyPipe,
  ],
  templateUrl: './listings.component.html',
  styleUrl: './listings.component.scss',
})
export class ListingsComponent implements OnInit {
  private readonly listingService = inject(ListingService);

  listings = signal<QuercheckerListingDto[]>([]);
  filterText = signal('');
  freeOnly = signal(false);
  loading = signal(false);
  error = signal<string | null>(null);
  searchMode = signal(false);

  sortColumn = signal('');
  sortDirection = signal<'asc' | 'desc' | ''>('');

  displayedColumns = ['title', 'price', 'location', 'listedAt', 'actions'];

  filteredListings = computed(() => {
    const filter = this.filterText().toLowerCase();
    const free = this.freeOnly();
    return this.listings().filter((l) => {
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

  ngOnInit(): void {
    this.loadListings();
  }

  loadListings(): void {
    this.loading.set(true);
    this.error.set(null);
    this.listingService.getAll().subscribe({
      next: (data) => {
        this.listings.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set('Fehler beim Laden der Inserate: ' + err.message);
        this.loading.set(false);
      },
    });
  }

  onSearchResults(results: QuercheckerListingDto[]): void {
    this.listings.set(results);
    this.filterText.set('');
    this.freeOnly.set(false);
    this.searchMode.set(true);
  }

  showAllListings(): void {
    this.searchMode.set(false);
    this.loadListings();
  }

  onSortChange(sort: Sort): void {
    this.sortColumn.set(sort.active);
    this.sortDirection.set(sort.direction);
  }

  openOnWillhaben(listing: QuercheckerListingDto): void {
    window.open(listing.url, '_blank', 'noopener');
  }

  searchOnGeizhals(listing: QuercheckerListingDto): void {
    const query = encodeURIComponent(listing.title ?? '');
    window.open(`https://geizhals.at/?fs=${query}`, '_blank', 'noopener');
  }

  deleteListing(listing: QuercheckerListingDto): void {
    if (!listing.id) return;
    this.listingService.delete(listing.id).subscribe({
      next: () => {
        this.listings.update((list) => list.filter((l) => l.id !== listing.id));
      },
      error: (err) => {
        this.error.set('Fehler beim Löschen: ' + err.message);
      },
    });
  }
}
