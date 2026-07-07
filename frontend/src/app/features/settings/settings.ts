import { Component, inject, signal } from '@angular/core';
import { Location } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { DeleteSweep } from './delete-sweep/delete-sweep';
import { DlExtractionSettings } from './dl-extraction-settings/dl-extraction-settings';
import { UsageMonitor } from './usage-monitor/usage-monitor';
import { CategoryPreferences } from './category-preferences/category-preferences';
import { ProviderConfig } from './provider-config/provider-config';
import { AccessKeyManagement } from './access-key-management/access-key-management';
import { ProviderStatusStore } from '../../core/provider-status.store';
import { AuthService } from '../../core/auth.service';
import { Theme } from './theme';

@Component({
  selector: 'app-settings',
  imports: [
    FormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatExpansionModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    DeleteSweep,
    DlExtractionSettings,
    UsageMonitor,
    CategoryPreferences,
    ProviderConfig,
    AccessKeyManagement,
  ],
  templateUrl: './settings.html',
  styleUrl: './settings.scss',
})
export class SettingsComponent {
  private readonly location = inject(Location);

  readonly theme = inject(Theme);
  readonly usageHasWarning = signal(false);
  readonly providerStatus = inject(ProviderStatusStore);
  readonly auth = inject(AuthService);

  readonly accessKeyInput = signal('');
  readonly loginError = signal<string | null>(null);

  goBack(): void {
    this.location.back();
  }

  submitAccessKey(): void {
    const key = this.accessKeyInput().trim();
    if (!key) return;

    this.loginError.set(null);
    this.auth.login(key).subscribe({
      next: () => this.accessKeyInput.set(''),
      error: () => this.loginError.set('Ungültiger oder gesperrter Zugriffs-Key'),
    });
  }

  logout(): void {
    this.auth.logout().subscribe();
  }
}
