import { ChangeDetectionStrategy, Component, computed, inject, model, output, signal } from '@angular/core';
import { RatingChangedEvent } from './listing-card/listing-card.component';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { QuercheckerListingDto } from '../../../api/model/quercheckerListingDto';
import { ListingCardComponent } from './listing-card/listing-card.component';
import { SearchStore } from '../search.store';

export type RatingFilter = 'UP' | 'UP_NULL' | 'DOWN' | 'ALL';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-wh-listings',
  imports: [
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatProgressSpinnerModule,
    MatSlideToggleModule,
    ListingCardComponent,
  ],
  templateUrl: './wh-listings.component.html',
  styleUrl: './wh-listings.component.scss',
})
export class WhListingsComponent {
  protected readonly store = inject(SearchStore);

  filterText = model('');
  freeOnly = model(false);
  ratingFilter = model<RatingFilter>('UP_NULL');

  removingIds = signal<Set<number>>(new Set());

  ratingChanged = output<RatingChangedEvent>();

  private filteredListings = computed(() => {
    const filter = this.filterText().toLowerCase();
    const free = this.freeOnly();
    const ratingF = this.ratingFilter();
    const searchMode = this.store.searchMode();
    return this.store.patchedListings().filter((l) => {
      if (searchMode) {
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
    const col = this.store.sortColumn() as keyof QuercheckerListingDto;
    const dir = this.store.sortDirection();
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

  onFilterInput(e: Event): void {
    this.filterText.set((e.target as HTMLInputElement).value);
  }

  onCardClicked(listing: QuercheckerListingDto): void {
    this.store.selectListing(listing.id!.toString());
  }

  private cardLeavesView(newRating: string | null): boolean {
    switch (this.ratingFilter()) {
      case 'UP':      return newRating !== 'UP';
      case 'DOWN':    return newRating !== 'DOWN';
      case 'UP_NULL': return newRating === 'DOWN';
      default:        return false; // 'ALL': Karte bleibt immer
    }
  }

  onCardRatingChanged(event: RatingChangedEvent): void {
    if (this.cardLeavesView(event.newRating)) {
      this.removingIds.update((s) => new Set([...s, event.id]));
      setTimeout(() => {
        this.removingIds.update((s) => {
          const next = new Set(s);
          next.delete(event.id);
          return next;
        });
        if (this.store.searchMode()) {
          this.store.applySearchPatch(event.id, { rating: event.newRating ?? undefined });
        }
        this.ratingChanged.emit(event);
      }, 350);
    } else if (this.store.searchMode()) {
      this.store.applySearchPatch(event.id, { rating: event.newRating ?? undefined });
      this.ratingChanged.emit(event);
    }
  }
}
