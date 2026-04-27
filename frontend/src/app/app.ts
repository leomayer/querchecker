import { Component, effect, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AppHeaderComponent } from './shared/layout/app-header/app-header';
import { AppFooterComponent } from './shared/layout/app-footer/app-footer';
import { StartupOverlayComponent } from './core/startup-overlay/startup-overlay';
import { ProviderStatusPopup } from './core/provider-status-popup/provider-status-popup';
import { ExtractionStore } from './features/wh-search/extraction.store';
import { ProviderStatusStore } from './core/provider-status.store';
import { HealthService } from './core/health.service';
import { SnackService } from './shared/services/snack.service';

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet,
    AppHeaderComponent,
    AppFooterComponent,
    StartupOverlayComponent,
    ProviderStatusPopup,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class AppComponent {
  // Eagerly initialize so SSE listeners are registered from app start.
  readonly extractionStore = inject(ExtractionStore);
  readonly providerStatusStore = inject(ProviderStatusStore);

  protected readonly health = inject(HealthService);
  private readonly snackService = inject(SnackService);

  constructor() {
    effect(() => {
      if (this.health.serverRestartCount() > 0) {
        this.snackService.info('Verbindung wiederhergestellt', 'Server neugestartet');
      }
    });
  }
}
