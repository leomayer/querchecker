import { Component, inject, computed } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map, startWith } from 'rxjs';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Theme } from '../../../features/settings/theme';
import { ProviderStatusStore } from '../../../core/provider-status.store';
import { SnackService } from '../../services/snack.service';

@Component({
  selector: 'app-header',
  imports: [MatToolbarModule, MatIconModule, MatButtonModule, MatTooltipModule],
  templateUrl: './app-header.html',
  styleUrl: './app-header.scss',
})
export class AppHeaderComponent {
  private readonly router = inject(Router);
  private readonly snack = inject(SnackService);
  readonly theme = inject(Theme);
  protected readonly providerStatus = inject(ProviderStatusStore);

  readonly isOnSettings = toSignal(
    this.router.events.pipe(
      filter((e) => e instanceof NavigationEnd),
      map(() => this.router.url.startsWith('/settings')),
      startWith(this.router.url.startsWith('/settings')),
    ),
    { initialValue: this.router.url.startsWith('/settings') },
  );

  readonly themeIcon = computed(() => (this.theme.darkMode() ? 'light_mode' : 'dark_mode'));
  readonly themeAriaLabel = computed(() =>
    this.theme.darkMode() ? 'Hellmodus aktivieren' : 'Dunkelmodus aktivieren',
  );
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
