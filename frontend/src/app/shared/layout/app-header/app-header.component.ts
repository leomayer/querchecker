import { Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatBadgeModule } from '@angular/material/badge';
import { Theme } from '../../../features/settings/theme';
import { ProviderStatusStore } from '../../../core/provider-status.store';

@Component({
  selector: 'app-header',
  imports: [MatToolbarModule, MatIconModule, MatButtonModule, MatBadgeModule],
  templateUrl: './app-header.component.html',
  styleUrl: './app-header.component.scss',
})
export class AppHeaderComponent {
  private readonly router = inject(Router);
  readonly theme = inject(Theme);
  protected readonly providerStatus = inject(ProviderStatusStore);

  readonly themeIcon = computed(() => (this.theme.darkMode() ? 'light_mode' : 'dark_mode'));
  readonly themeAriaLabel = computed(() =>
    this.theme.darkMode() ? 'Hellmodus aktivieren' : 'Dunkelmodus aktivieren',
  );

  // TODO: align badge colors with project warning/error palette (--color-tertiary vs --mat-sys-error)
  readonly settingsBadgeColor = computed(() =>
    this.providerStatus.badgeIsError() ? 'warn' : 'accent',
  );

  toggleTheme(): void {
    this.theme.setDarkTheme(!this.theme.darkMode());
  }

  navigateSettings(): void {
    this.router.navigate(['/settings']);
  }
}
