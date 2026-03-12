import { Component, inject } from '@angular/core';
import { Location } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatSliderModule } from '@angular/material/slider';
import { ThemeToggle } from './theme-toggle/theme-toggle';
import { DeleteSweep } from './delete-sweep/delete-sweep';

@Component({
  selector: 'app-settings',
  imports: [MatButtonModule, MatIconModule, MatCardModule, ThemeToggle, DeleteSweep],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss',
})
export class SettingsComponent {
  private readonly location = inject(Location);

  goBack(): void {
    this.location.back();
  }
}
