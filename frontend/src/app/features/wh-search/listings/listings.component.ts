import { ChangeDetectionStrategy, Component, input, model, output } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { FormsModule } from '@angular/forms';
import { QuercheckerListingDto } from '../../../api/model/quercheckerListingDto';
import { ListingCardComponent } from './listing-card/listing-card.component';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-listings',
  imports: [
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatSlideToggleModule,
    FormsModule,
    ListingCardComponent,
  ],
  templateUrl: './listings.component.html',
  styleUrl: './listings.component.scss',
})
export class ListingsComponent {
  listings = input.required<QuercheckerListingDto[]>();
  loading = input.required<boolean>();
  error = input<string | null>(null);
  searchMode = input(false);
  count = input(0);
  filterText = model('');
  freeOnly = model(false);

  delete = output<QuercheckerListingDto>();
  showAll = output<void>();
}
