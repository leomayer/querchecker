import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { DatePipe } from '@angular/common';
import { QuercheckerListingDto } from '../../../../api/model/quercheckerListingDto';
import { CustomCurrencyPipe } from '../../../../shared/pipes/custom-currency/custom-currency-pipe';

@Component({
  selector: 'app-listing-card',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, MatTooltipModule, DatePipe, CustomCurrencyPipe],
  templateUrl: './listing-card.component.html',
  styleUrl: './listing-card.component.scss',
})
export class ListingCardComponent {
  listing = input.required<QuercheckerListingDto>();
  delete = output<QuercheckerListingDto>();

  openOnWillhaben(): void {
    window.open(this.listing().url, '_blank', 'noopener');
  }

  searchOnGeizhals(): void {
    const query = encodeURIComponent(this.listing().title ?? '');
    window.open(`https://geizhals.at/?fs=${query}`, '_blank', 'noopener');
  }
}
