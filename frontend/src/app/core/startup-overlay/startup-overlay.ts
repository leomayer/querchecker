import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { HealthService } from '../health.service';

@Component({
  selector: 'app-startup-overlay',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatProgressSpinnerModule],
  templateUrl: './startup-overlay.html',
  styleUrl: './startup-overlay.scss',
})
export class StartupOverlayComponent {
  protected readonly health = inject(HealthService);
}
