import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { DatePipe } from '@angular/common';
import { QuercheckerListingDto } from '../../../../api/model/quercheckerListingDto';
import { CustomCurrencyPipe } from '../../../../shared/pipes/custom-currency/custom-currency-pipe';
import { ListingService } from '../../../../core/listing.service';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-listing-card',
  imports: [MatIconModule, MatButtonModule, DatePipe, CustomCurrencyPipe],
  templateUrl: './listing-card.component.html',
  styleUrl: './listing-card.component.scss',
})
export class ListingCardComponent {
  private readonly listingService = inject(ListingService);

  listing = input.required<QuercheckerListingDto>();
  clicked = output<QuercheckerListingDto>();
  ratingChanged = output<void>();

  setRating(rating: 'UP' | 'DOWN', event: MouseEvent): void {
    event.stopPropagation();
    const next = this.listing().rating === rating ? null : rating;
    this.listingService.updateRating(this.listing().id!, next).subscribe(() => {
      this.ratingChanged.emit();
    });
  }
}
