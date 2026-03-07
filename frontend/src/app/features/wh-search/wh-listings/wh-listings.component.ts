import { ChangeDetectionStrategy, Component, input, model, output } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { QuercheckerListingDto } from '../../../api/model/quercheckerListingDto';
import { ListingCardComponent } from './listing-card/listing-card.component';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-wh-listings',
  imports: [
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatButtonModule,
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

  cardClicked = output<QuercheckerListingDto>();
  ratingChanged = output<void>();
  showAll = output<void>();

  onFilterInput(e: Event): void {
    this.filterText.set((e.target as HTMLInputElement).value);
  }
}
