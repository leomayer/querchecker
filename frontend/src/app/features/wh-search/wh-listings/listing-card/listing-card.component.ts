import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { DatePipe } from '@angular/common';
import { WhItemDto } from '../../../../api/model/whItemDto';
import { CustomCurrencyPipe } from '../../../../shared/pipes/custom-currency/custom-currency-pipe';
import { ListingService } from '../../../../core/listing.service';

export interface RatingChangedEvent {
  id: number;
  newRating: string | null;
}

export interface InterestLevelChangedEvent {
  id: number;
  newLevel: 'LOW' | 'MEDIUM' | 'HIGH' | null;
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

  listing = input.required<WhItemDto>();
  selected = input(false);
  clicked = output<WhItemDto>();
  ratingChanged = output<RatingChangedEvent>();
  interestLevelChanged = output<InterestLevelChangedEvent>();

  private _tempRating = signal<string | null | undefined>(undefined);
  private _tempInterestLevel = signal<'LOW' | 'MEDIUM' | 'HIGH' | null | undefined>(undefined);

  displayRating = computed(() => {
    const temp = this._tempRating();
    return temp !== undefined ? temp : (this.listing().rating ?? null);
  });

  displayInterestLevel = computed(() => {
    const temp = this._tempInterestLevel();
    return temp !== undefined
      ? temp
      : ((this.listing().interestLevel as 'LOW' | 'MEDIUM' | 'HIGH' | null) ?? null);
  });

  constructor() {
    // Reset temp values when the listing input updates (parent applied override)
    effect(() => {
      void this.listing();
      untracked(() => {
        this._tempRating.set(undefined);
        this._tempInterestLevel.set(undefined);
      });
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

  cycleInterestLevel(event: MouseEvent): void {
    event.stopPropagation();
    const levels = [null, 'LOW', 'MEDIUM', 'HIGH'] as const;
    const idx = levels.indexOf(this.displayInterestLevel());
    const newLevel = levels[(idx + 1) % levels.length];
    this._tempInterestLevel.set(newLevel);
    this.listingService.updateInterest(this.listing().id!, newLevel).subscribe({
      next: () => this.interestLevelChanged.emit({ id: this.listing().id!, newLevel }),
      error: () => this._tempInterestLevel.set(undefined),
    });
  }
}
