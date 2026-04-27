import { Component, inject, signal } from '@angular/core';
import { Location } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { DeleteSweep } from './delete-sweep/delete-sweep';
import { DlExtractionSettings } from './dl-extraction-settings/dl-extraction-settings';
import { UsageMonitor } from './usage-monitor/usage-monitor';
import { CategoryPreferences } from './category-preferences/category-preferences';
import { ProviderConfig } from './provider-config/provider-config';
import { ProviderStatusStore } from '../../core/provider-status.store';
import { Theme } from './theme';

@Component({
  selector: 'app-settings',
  imports: [
    MatButtonModule,
    MatButtonToggleModule,
    MatExpansionModule,
    MatIconModule,
    DeleteSweep,
    DlExtractionSettings,
    UsageMonitor,
    CategoryPreferences,
    ProviderConfig,
  ],
  templateUrl: './settings.html',
  styleUrl: './settings.scss',
})
export class SettingsComponent {
  private readonly location = inject(Location);

  readonly theme = inject(Theme);
  readonly usageHasWarning = signal(false);
  readonly providerStatus = inject(ProviderStatusStore);

  goBack(): void {
    this.location.back();
  }
}
