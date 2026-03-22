import { Component, inject, signal } from '@angular/core';
import { Location } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { ThemeToggle } from './theme-toggle/theme-toggle';
import { DeleteSweep } from './delete-sweep/delete-sweep';
import { DlExtractionSettings } from './dl-extraction-settings/dl-extraction-settings';
import { UsageMonitor } from './usage-monitor/usage-monitor';
import { CategoryPreferences } from './category-preferences/category-preferences';

@Component({
  selector: 'app-settings',
  imports: [
    MatButtonModule,
    MatExpansionModule,
    MatIconModule,
    ThemeToggle,
    DeleteSweep,
    DlExtractionSettings,
    UsageMonitor,
    CategoryPreferences,
  ],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss',
})
export class SettingsComponent {
  private readonly location = inject(Location);

  readonly usageHasWarning = signal(false);

  goBack(): void {
    this.location.back();
  }
}
