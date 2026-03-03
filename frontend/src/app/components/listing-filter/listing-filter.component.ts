import { Component, model } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-listing-filter',
  standalone: true,
  imports: [MatFormFieldModule, MatInputModule, MatIconModule, MatSlideToggleModule, FormsModule],
  templateUrl: './listing-filter.component.html',
  styleUrl: './listing-filter.component.scss',
})
export class ListingFilterComponent {
  filterText = model('');
  freeOnly = model(false);
}
