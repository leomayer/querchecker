import { Component, EventEmitter, Output, signal, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { FormsModule } from '@angular/forms';
import { ListingService } from '../../services/listing.service';
import { QuercheckerListingDto } from '../../api/model/quercheckerListingDto';

@Component({
  selector: 'app-wh-search',
  standalone: true,
  imports: [
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    FormsModule,
  ],
  templateUrl: './wh-search.component.html',
  styleUrl: './wh-search.component.scss',
})
export class WhSearchComponent {
  private readonly listingService = inject(ListingService);

  keyword = '';
  rows = 50;
  priceFrom: number | null = null;
  priceTo: number | null = null;
  loading = signal(false);
  error = signal<string | null>(null);

  readonly rowOptions = [50, 100, 250];

  @Output() results = new EventEmitter<QuercheckerListingDto[]>();

  onSearch(): void {
    if (!this.keyword.trim()) return;
    this.loading.set(true);
    this.error.set(null);
    this.listingService
      .search(
        this.keyword.trim(),
        this.rows,
        this.priceFrom ?? undefined,
        this.priceTo ?? undefined,
      )
      .subscribe({
        next: (data) => {
          this.loading.set(false);
          this.results.emit(data);
        },
        error: (err) => {
          this.loading.set(false);
          this.error.set('Fehler bei der Suche: ' + err.message);
        },
      });
  }
}
