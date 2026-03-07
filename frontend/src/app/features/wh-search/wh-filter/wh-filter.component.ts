import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { LocationFilterComponent } from '../location-filter/location-filter.component';
import { CategoryFilterComponent } from '../category-filter/category-filter.component';

export interface SearchParams {
  keyword: string;
  rows: number;
  priceFrom?: number;
  priceTo?: number;
  locationAreaId?: number;
  categoryWhId?: number;
  paylivery?: boolean;
}

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-wh-filter',
  imports: [
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatCheckboxModule,
    LocationFilterComponent,
    CategoryFilterComponent,
  ],
  templateUrl: './wh-filter.component.html',
  styleUrl: './wh-filter.component.scss',
})
export class WhFilterComponent {
  loading = input(false);
  error = input<string | null>(null);

  keyword = signal('');
  rows = signal(50);
  priceFrom = signal<number | null>(null);
  priceTo = signal<number | null>(null);
  locationAreaId = signal<number | undefined>(undefined);
  categoryWhId = signal<number | undefined>(undefined);
  paylivery = signal(false);

  readonly rowOptions = [50, 100, 250];

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
      locationAreaId: this.locationAreaId(),
      categoryWhId: this.categoryWhId(),
      paylivery: this.paylivery() || undefined,
    });
  }
}
