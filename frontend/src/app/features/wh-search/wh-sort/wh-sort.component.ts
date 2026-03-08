import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { SearchStore } from '../search.store';

export const SORT_FIELDS = [
  { value: 'title', label: 'Titel' },
  { value: 'price', label: 'Preis' },
  { value: 'location', label: 'Standort' },
  { value: 'listedAt', label: 'Eingestellt' },
] as const;

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-wh-sort',
  imports: [MatFormFieldModule, MatSelectModule, MatButtonToggleModule, MatIconModule],
  templateUrl: './wh-sort.component.html',
  styleUrl: './wh-sort.component.scss',
})
export class WhSortComponent {
  protected readonly store = inject(SearchStore);

  readonly sortFields = SORT_FIELDS;

  onSortFieldChange(field: string): void {
    this.store.setSortColumn(field);
  }
}
