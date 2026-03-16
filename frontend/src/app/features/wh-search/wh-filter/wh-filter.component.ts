import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { LocationFilterComponent } from '../location-filter/location-filter.component';
import { CategoryFilterComponent } from '../category-filter/category-filter.component';
import { SearchStore } from '../search.store';

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
  protected readonly store = inject(SearchStore);

  protected readonly loading = this.store.loading;
  protected readonly error = this.store.error;
  protected readonly searchMode = this.store.searchMode;
  protected readonly clearSearch = () => this.store.clearSearch();

  readonly keyword = this.store.filterDraft.keyword;
  readonly rows = this.store.filterDraft.rows;
  readonly priceFrom = this.store.filterDraft.priceFrom;
  readonly priceTo = this.store.filterDraft.priceTo;
  readonly locationAreaId = this.store.filterDraft.locationAreaId;
  readonly categoryWhId = this.store.filterDraft.categoryWhId;
  readonly paylivery = this.store.filterDraft.paylivery;

  readonly rowOptions = [50, 100, 250];

  /** At least one search criterion is filled → Suchen enabled. */
  readonly canSearch = computed(
    () =>
      !!this.keyword().trim() ||
      this.categoryWhId() != null ||
      this.locationAreaId() != null,
  );

  /** Any non-default filter value → show Zurücksetzen even before first search. */
  readonly hasDraft = computed(
    () =>
      !!this.keyword().trim() ||
      this.categoryWhId() != null ||
      this.locationAreaId() != null ||
      this.priceFrom() != null ||
      this.priceTo() != null ||
      !!this.paylivery(),
  );

  onKeywordInput(e: Event): void {
    this.store.setFilterDraft({ keyword: (e.target as HTMLInputElement).value });
  }

  onPriceFromInput(e: Event): void {
    const val = (e.target as HTMLInputElement).valueAsNumber;
    this.store.setFilterDraft({ priceFrom: isNaN(val) ? null : val });
  }

  onPriceToInput(e: Event): void {
    const val = (e.target as HTMLInputElement).valueAsNumber;
    this.store.setFilterDraft({ priceTo: isNaN(val) ? null : val });
  }

  onSearch(): void {
    const d = this.store.filterDraft();
    if (!d.keyword.trim() && !d.categoryWhId && !d.locationAreaId) return;
    this.store.search({
      keyword: d.keyword.trim(),
      rows: d.rows,
      priceFrom: d.priceFrom ?? undefined,
      priceTo: d.priceTo ?? undefined,
      locationAreaId: d.locationAreaId,
      categoryWhId: d.categoryWhId,
      paylivery: d.paylivery || undefined,
    });
  }
}
