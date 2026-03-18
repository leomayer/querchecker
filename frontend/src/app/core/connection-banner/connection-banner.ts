import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { HealthService } from '../health.service';

@Component({
  selector: 'app-connection-banner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule, MatProgressBarModule],
  templateUrl: './connection-banner.html',
  styleUrl: './connection-banner.scss',
})
export class ConnectionBannerComponent {
  protected readonly health = inject(HealthService);
}
