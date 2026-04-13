import { Component, inject, computed } from '@angular/core';
import { Router } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Theme } from '../../../features/settings/theme';
import { ProviderStatusStore } from '../../../core/provider-status.store';

@Component({
  selector: 'app-header',
  imports: [MatToolbarModule, MatIconModule, MatButtonModule, MatTooltipModule],
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

  toggleTheme(): void {
    this.theme.setDarkTheme(!this.theme.darkMode());
  }

  openSetupWizard(): void {
    this.router.navigate(['/', 'setup']);
  }

  navigateSettings(): void {
    this.router.navigate(['/settings']);
  }
}
