import { Component, computed, inject, signal } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { LocationFilterComponent } from '../location-filter/location-filter.component';
import { CategoryFilterComponent } from '../category-filter/category-filter.component';
import { SearchStore } from '../search.store';
import {
  addToSearchHistory,
  loadSearchHistory,
  SearchHistoryEntry,
} from '../../../core/search-history';

@Component({
  selector: 'app-wh-filter',
  imports: [
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatCheckboxModule,
    MatAutocompleteModule,
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

  private readonly keywordHistory = signal<SearchHistoryEntry[]>(loadSearchHistory());

  readonly keywordSuggestions = computed(() => {
    const q = this.keyword().toLowerCase().trim();
    const history = this.keywordHistory();
    if (!q) return history;
    return history.filter((e) => e.keyword.toLowerCase().includes(q));
  });

  /** At least one search criterion is filled → Suchen enabled. */
  readonly canSearch = computed(
    () => !!this.keyword().trim() || this.categoryWhId() != null || this.locationAreaId() != null,
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

  readonly displayEntry = (entry: SearchHistoryEntry | string | null): string => {
    if (!entry) return '';
    if (typeof entry === 'string') return entry;
    return entry.keyword;
  };

  formatPriceRange(entry: SearchHistoryEntry): string | null {
    if (entry.priceFrom != null && entry.priceTo != null)
      return `€${entry.priceFrom}–€${entry.priceTo}`;
    if (entry.priceFrom != null) return `ab €${entry.priceFrom}`;
    if (entry.priceTo != null) return `bis €${entry.priceTo}`;
    return null;
  }

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

  onKeywordSelected(entry: SearchHistoryEntry): void {
    this.store.setFilterDraft({
      keyword: entry.keyword,
      rows: entry.rows,
      priceFrom: entry.priceFrom,
      priceTo: entry.priceTo,
      locationAreaId: entry.locationAreaId,
      categoryWhId: entry.categoryWhId,
      paylivery: entry.paylivery,
    });
    this.store.search({
      keyword: entry.keyword,
      rows: entry.rows,
      priceFrom: entry.priceFrom ?? undefined,
      priceTo: entry.priceTo ?? undefined,
      locationAreaId: entry.locationAreaId,
      categoryWhId: entry.categoryWhId,
      paylivery: entry.paylivery || undefined,
    });
  }

  onSearch(): void {
    const d = this.store.filterDraft();
    if (!d.keyword.trim() && !d.categoryWhId && !d.locationAreaId) return;
    if (d.keyword.trim()) {
      addToSearchHistory({
        keyword: d.keyword.trim(),
        rows: d.rows,
        priceFrom: d.priceFrom,
        priceTo: d.priceTo,
        locationAreaId: d.locationAreaId,
        categoryWhId: d.categoryWhId,
        paylivery: d.paylivery,
      });
      this.keywordHistory.set(loadSearchHistory());
    }
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
