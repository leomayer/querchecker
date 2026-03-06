import { ChangeDetectionStrategy, Component, model } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';

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
  sortColumn = model('');
  sortDirection = model<'asc' | 'desc' | ''>('');

  readonly sortFields = SORT_FIELDS;

  onSortFieldChange(field: string): void {
    this.sortColumn.set(field);
    if (field && !this.sortDirection()) this.sortDirection.set('asc');
    if (!field) this.sortDirection.set('');
  }
}
