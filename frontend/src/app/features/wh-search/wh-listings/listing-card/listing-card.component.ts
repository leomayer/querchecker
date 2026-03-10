import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { DatePipe } from '@angular/common';
import { QuercheckerListingDto } from '../../../../api/model/quercheckerListingDto';
import { CustomCurrencyPipe } from '../../../../shared/pipes/custom-currency/custom-currency-pipe';
import { ListingService } from '../../../../core/listing.service';

export interface RatingChangedEvent {
  id: number;
  newRating: string | null;
}

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-listing-card',
  imports: [MatIconModule, MatButtonModule, MatTooltipModule, DatePipe, CustomCurrencyPipe],
  templateUrl: './listing-card.component.html',
  styleUrl: './listing-card.component.scss',
})
export class ListingCardComponent {
  private readonly listingService = inject(ListingService);

  listing = input.required<QuercheckerListingDto>();
  selected = input(false);
  clicked = output<QuercheckerListingDto>();
  ratingChanged = output<RatingChangedEvent>();

  private _tempRating = signal<string | null | undefined>(undefined);

  displayRating = computed(() => {
    const temp = this._tempRating();
    return temp !== undefined ? temp : (this.listing().rating ?? null);
  });

  constructor() {
    // Reset temp rating when the listing input updates (parent applied override)
    effect(() => {
      void this.listing();
      untracked(() => this._tempRating.set(undefined));
    });
  }

  setRating(rating: 'UP' | 'DOWN', event: MouseEvent): void {
    event.stopPropagation();
    const newRating = this.displayRating() === rating ? null : rating;
    this._tempRating.set(newRating);
    this.listingService.updateRating(this.listing().id!, newRating).subscribe({
      next: () => this.ratingChanged.emit({ id: this.listing().id!, newRating }),
      error: () => this._tempRating.set(undefined),
    });
  }
}
