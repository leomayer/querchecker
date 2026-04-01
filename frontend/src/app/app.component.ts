import { Component, effect, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AppHeaderComponent } from './shared/layout/app-header/app-header.component';
import { AppFooterComponent } from './shared/layout/app-footer/app-footer.component';
import { StartupOverlayComponent } from './core/startup-overlay/startup-overlay';
import { ExtractionStore } from './features/wh-search/extraction.store';
import { HealthService } from './core/health.service';
import { SnackService } from './shared/services/snack.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, AppHeaderComponent, AppFooterComponent, StartupOverlayComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {
  // Eagerly initialize so the SSE listener is registered from app start,
  // before any detail view mounts.
  readonly extractionStore = inject(ExtractionStore);
  protected readonly health = inject(HealthService);
  private readonly snackService = inject(SnackService);

  constructor() {
    effect(() => {
      if (this.health.serverRestartCount() > 0) {
        this.snackService.info('Server neugestartet — Verbindung wiederhergestellt');
      }
    });
  }
}
