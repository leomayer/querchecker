import { Component, inject, computed } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map, startWith } from 'rxjs';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatBadgeModule } from '@angular/material/badge';
import { Theme } from '../../../features/settings/theme';
import { ProviderStatusStore } from '../../../core/provider-status.store';
import { SnackService } from '../../services/snack.service';

@Component({
  selector: 'app-header',
  imports: [MatToolbarModule, MatIconModule, MatButtonModule, MatTooltipModule, MatBadgeModule],
  templateUrl: './app-header.component.html',
  styleUrl: './app-header.component.scss',
})
export class AppHeaderComponent {
  private readonly router = inject(Router);
  private readonly snack = inject(SnackService);
  readonly theme = inject(Theme);
  protected readonly providerStatus = inject(ProviderStatusStore);

  readonly isOnSettings = toSignal(
    this.router.events.pipe(
      filter(e => e instanceof NavigationEnd),
      map(() => this.router.url.startsWith('/settings')),
      startWith(this.router.url.startsWith('/settings')),
    ),
    { initialValue: this.router.url.startsWith('/settings') },
  );

  readonly themeIcon = computed(() => (this.theme.darkMode() ? 'light_mode' : 'dark_mode'));
  readonly themeAriaLabel = computed(() =>
    this.theme.darkMode() ? 'Hellmodus aktivieren' : 'Dunkelmodus aktivieren',
  );
  readonly settingsBadgeColor = computed(() => {
    const status = this.providerStatus.status();
    if (!status) return 'accent';
    const isUnconfigured = status.searchState === 'UNCONFIGURED' || status.llmState === 'UNCONFIGURED';
    const isUnavailable = status.searchState === 'UNAVAILABLE' || status.llmState === 'UNAVAILABLE';
    if (isUnavailable) return 'warn';
    if (isUnconfigured) return 'accent';
    return 'primary';
  });

  toggleTheme(): void {
    this.theme.setDarkTheme(!this.theme.darkMode());
  }

  openSetupWizard(): void {
    this.router.navigate(['/', 'setup']);
  }

  navigateSettings(): void {
    if (this.isOnSettings()) {
      this.snack.info('Sie befinden sich bereits auf dieser Seite', 'Einstellungen');
      return;
    }
    this.router.navigate(['/settings']);
  }
}
