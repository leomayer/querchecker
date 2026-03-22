import { Component, OnDestroy, OnInit, inject, output, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ApiUsageResponse, UsageService } from '../../../core/usage.service';

/** Brave free tier: 2000 calls/month */
const BRAVE_WARN_THRESHOLD = 1500;
/** Groq free tier: ~14400 RPD */
const GROQ_WARN_THRESHOLD = 10000;

@Component({
  selector: 'app-usage-monitor',
  imports: [DecimalPipe, MatButtonModule, MatCardModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './usage-monitor.html',
  styleUrl: './usage-monitor.scss',
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

  isBraveWarn(): boolean {
    return (this.usage()?.brave.calls ?? 0) >= BRAVE_WARN_THRESHOLD;
  }

  isGroqWarn(): boolean {
    return (this.usage()?.groq.calls ?? 0) >= GROQ_WARN_THRESHOLD;
  }

  hasAnyWarning(): boolean {
    return this.isBraveWarn() || this.isGroqWarn();
  }
}
