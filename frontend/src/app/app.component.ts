import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AppHeaderComponent } from './shared/layout/app-header/app-header.component';
import { AppFooterComponent } from './shared/layout/app-footer/app-footer.component';
import { StartupOverlayComponent } from './core/startup-overlay/startup-overlay';
import { ExtractionStore } from './features/wh-search/extraction.store';
import { HealthService } from './core/health.service';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
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
}
