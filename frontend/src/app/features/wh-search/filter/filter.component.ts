import { ChangeDetectionStrategy, Component, input, model, output, signal } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';

export interface SearchParams {
  keyword: string;
  rows: number;
  priceFrom?: number;
  priceTo?: number;
  locationAreaId?: number;
}

export const SORT_FIELDS = [
  { value: 'title', label: 'Titel' },
  { value: 'price', label: 'Preis' },
  { value: 'location', label: 'Standort' },
  { value: 'listedAt', label: 'Eingestellt' },
] as const;

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-filter',
  imports: [
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './filter.component.html',
  styleUrl: './filter.component.scss',
})
export class FilterComponent {
  sortColumn = model('');
  sortDirection = model<'asc' | 'desc' | ''>('');

  loading = input(false);
  error = input<string | null>(null);

  keyword = signal('');
  rows = signal(50);
  priceFrom = signal<number | null>(null);
  priceTo = signal<number | null>(null);

  readonly rowOptions = [50, 100, 250];
  readonly sortFields = SORT_FIELDS;

  search = output<SearchParams>();

  onKeywordInput(e: Event): void {
    this.keyword.set((e.target as HTMLInputElement).value);
  }

  onPriceFromInput(e: Event): void {
    const val = (e.target as HTMLInputElement).valueAsNumber;
    this.priceFrom.set(isNaN(val) ? null : val);
  }

  onPriceToInput(e: Event): void {
    const val = (e.target as HTMLInputElement).valueAsNumber;
    this.priceTo.set(isNaN(val) ? null : val);
  }

  onSearch(): void {
    if (!this.keyword().trim()) return;
    this.search.emit({
      keyword: this.keyword().trim(),
      rows: this.rows(),
      priceFrom: this.priceFrom() ?? undefined,
      priceTo: this.priceTo() ?? undefined,
    });
  }

  onSortFieldChange(field: string): void {
    this.sortColumn.set(field);
    if (field && !this.sortDirection()) {
      this.sortDirection.set('asc');
    }
    if (!field) {
      this.sortDirection.set('');
    }
  }
}
