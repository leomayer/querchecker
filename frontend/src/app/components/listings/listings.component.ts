import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { ListingService, QuercheckerListingDto } from '../../services/listing.service';

@Component({
  selector: 'app-listings',
  standalone: true,
  imports: [
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatInputModule,
    MatFormFieldModule,
    MatCardModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    FormsModule,
    CurrencyPipe,
    DatePipe,
  ],
  templateUrl: './listings.component.html',
  styleUrl: './listings.component.scss',
})
export class ListingsComponent implements OnInit {
  private readonly listingService = inject(ListingService);

  listings = signal<QuercheckerListingDto[]>([]);
  filterText = signal('');
  loading = signal(false);
  error = signal<string | null>(null);

  displayedColumns = ['title', 'price', 'location', 'listedAt', 'actions'];

  filteredListings = computed(() => {
    const filter = this.filterText().toLowerCase();
    if (!filter) return this.listings();
    return this.listings().filter(
      (l) =>
        l.title.toLowerCase().includes(filter) ||
        (l.location ?? '').toLowerCase().includes(filter),
    );
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

  onFilterChange(value: string): void {
    this.filterText.set(value);
  }

  openOnWillhaben(listing: QuercheckerListingDto): void {
    window.open(listing.url, '_blank', 'noopener');
  }

  searchOnGeizhals(listing: QuercheckerListingDto): void {
    const query = encodeURIComponent(listing.title);
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
