import { Injectable, signal, inject, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_URLS } from './api-urls';

@Injectable({ providedIn: 'root' })
export class HealthService implements OnDestroy {
  private readonly http = inject(HttpClient);

  /** True once the backend has responded at least once. */
  readonly backendReady = signal(false);

  /** True when connection was established but then lost. */
  readonly connectionLost = signal(false);

  /** Number of poll attempts since last state change. */
  readonly attempts = signal(0);

  private pollTimer: ReturnType<typeof setTimeout> | null = null;
  private destroyed = false;

  constructor() {
    this.poll();
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    if (this.pollTimer) clearTimeout(this.pollTimer);
  }

  /** Notify that a server error occurred (called by interceptor). */
  notifyServerError(): void {
    if (this.backendReady() && !this.connectionLost()) {
      this.connectionLost.set(true);
      this.attempts.set(0);
      this.poll();
    }
  }

  private poll(): void {
    if (this.destroyed) return;

    this.http.get<{ status: string }>(API_URLS.health).subscribe({
      next: () => {
        this.backendReady.set(true);
        this.connectionLost.set(false);
        this.attempts.set(0);
        // No further polling needed — interceptor re-triggers on error
      },
      error: () => {
        this.attempts.update((n) => n + 1);
        const delay = this.backendReady() ? 3000 : 2000;
        this.pollTimer = setTimeout(() => this.poll(), delay);
      },
    });
  }
}
