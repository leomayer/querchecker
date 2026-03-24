import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject, output, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { GradientProgressBarComponent } from '../../../shared/components/gradient-progress-bar/gradient-progress-bar.component';
import { ApiUsageResponse, ProviderUsage, UsageService } from '../../../core/usage.service';

@Component({
  selector: 'app-usage-monitor',
  imports: [DecimalPipe, MatButtonModule, MatCardModule, MatIconModule, MatProgressSpinnerModule, GradientProgressBarComponent],
  templateUrl: './usage-monitor.html',
  styleUrl: './usage-monitor.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UsageMonitor implements OnInit, OnDestroy {
  private readonly usageService = inject(UsageService);

  readonly usage = signal<ApiUsageResponse | null>(null);
  readonly loading = signal(false);
  readonly error = signal(false);
  readonly warningChange = output<boolean>();

  private refreshTimer?: ReturnType<typeof setInterval>;

  ngOnInit(): void {
    this.load();
    this.refreshTimer = setInterval(() => this.load(), 60_000);
  }

  ngOnDestroy(): void {
    clearInterval(this.refreshTimer);
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.usageService.getUsage().subscribe({
      next: (data) => {
        this.usage.set(data);
        this.loading.set(false);
        this.warningChange.emit(this.hasAnyWarning());
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  protected hasAnyWarning(): boolean {
    const d = this.usage();
    if (!d) return false;
    return this.isOverThreshold(d.brave) || this.isOverThreshold(d.groq);
  }

  protected isOverThreshold(p: ProviderUsage): boolean {
    if (p.quotaLimit <= 0) return false;
    return p.quotaUsage / p.quotaLimit >= 0.8;
  }

  protected hasQuota(p: ProviderUsage): boolean {
    return p.quotaLimit > 0;
  }

  protected hasTokens(p: ProviderUsage): boolean {
    return p.tokensIn > 0 || p.tokensOut > 0;
  }
}
