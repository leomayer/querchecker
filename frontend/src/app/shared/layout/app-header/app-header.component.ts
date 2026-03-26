import { Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { Theme } from '../../../features/settings/theme';

@Component({
  selector: 'app-header',
  imports: [MatToolbarModule, MatIconModule, MatButtonModule],
  templateUrl: './app-header.component.html',
  styleUrl: './app-header.component.scss',
})
export class AppHeaderComponent {
  private readonly router = inject(Router);
  readonly theme = inject(Theme);
  readonly themeIcon = computed(() => (this.theme.darkMode() ? 'light_mode' : 'dark_mode'));
  readonly themeAriaLabel = computed(() =>
    this.theme.darkMode() ? 'Hellmodus aktivieren' : 'Dunkelmodus aktivieren',
  );

  toggleTheme(): void {
    this.theme.setDarkTheme(!this.theme.darkMode());
  }

  navigateSettings(): void {
    this.router.navigate(['/settings']);
  }
}
