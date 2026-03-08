import { ChangeDetectionStrategy, Component, input, model, output, signal } from '@angular/core';
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
  listings = input.required<QuercheckerListingDto[]>();
  loading = input.required<boolean>();
  error = input<string | null>(null);
  searchMode = input(false);
  count = input(0);
  whTotal = input<number | null>(null);
  filterText = model('');
  freeOnly = model(false);
  ratingFilter = model<RatingFilter>('UP');

  removingIds = signal<Set<number>>(new Set());

  cardClicked = output<QuercheckerListingDto>();
  ratingChanged = output<RatingChangedEvent>();
  showAll = output<void>();

  onFilterInput(e: Event): void {
    this.filterText.set((e.target as HTMLInputElement).value);
  }

  private cardLeavesView(newRating: string | null): boolean {
    switch (this.ratingFilter()) {
      case 'UP':     return newRating !== 'UP';
      case 'DOWN':   return newRating !== 'DOWN';
      case 'UP_NULL': return newRating === 'DOWN';
      default:       return false; // 'ALL': Karte bleibt immer
    }
  }

  onCardRatingChanged(event: RatingChangedEvent): void {
    if (this.cardLeavesView(event.newRating)) {
      // Karte verschwindet aus der aktuellen Ansicht → animieren, dann Event senden
      this.removingIds.update(s => new Set([...s, event.id]));
      setTimeout(() => {
        this.removingIds.update(s => {
          const next = new Set(s);
          next.delete(event.id);
          return next;
        });
        this.ratingChanged.emit(event);
      }, 350);
    } else if (this.searchMode()) {
      // Karte bleibt, aber im Such-Modus lokales Override updaten
      this.ratingChanged.emit(event);
    }
    // Karte bleibt im Alle-Modus → kein Reload nötig, Backend schon aktualisiert
  }
}
