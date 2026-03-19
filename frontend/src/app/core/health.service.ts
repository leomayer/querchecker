import { Injectable, signal, inject, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_URLS } from './api-urls';

const POLL_INTERVAL_IDLE_MS  = 30_000; // background check while healthy
const POLL_INTERVAL_RETRY_MS =  3_000; // rapid retry while reconnecting
const POLL_INTERVAL_INIT_MS  =  2_000; // initial startup probes

@Injectable({ providedIn: 'root' })
export class HealthService implements OnDestroy {
  private readonly http = inject(HttpClient);

  /** True once the backend has responded at least once. */
  readonly backendReady = signal(false);

  /** True when connection was established but then lost. */
  readonly connectionLost = signal(false);

  /** Increments each time the server restarts mid-session (SSE token mismatch). */
  readonly serverRestartCount = signal(0);

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

  /** Notify that the server restarted mid-session (called by SSE on token mismatch). */
  notifyServerRestart(): void {
    this.serverRestartCount.update((n) => n + 1);
  }

  /** Notify that a server error occurred (called by interceptor or SSE). */
  notifyServerError(): void {
    if (this.backendReady() && !this.connectionLost()) {
      this.connectionLost.set(true);
      this.attempts.set(0);
      // Kick off rapid retry immediately, cancelling any scheduled idle poll.
      this.scheduleNext(0);
    }
  }

  private poll(): void {
    if (this.destroyed) return;

    this.http.get<{ status: string }>(API_URLS.health).subscribe({
      next: () => {
        this.backendReady.set(true);
        this.connectionLost.set(false);
        this.attempts.set(0);
        this.scheduleNext(POLL_INTERVAL_IDLE_MS);
      },
      error: () => {
        this.attempts.update((n) => n + 1);
        if (this.backendReady()) {
          this.connectionLost.set(true);
        }
        const delay = this.backendReady() ? POLL_INTERVAL_RETRY_MS : POLL_INTERVAL_INIT_MS;
        this.scheduleNext(delay);
      },
    });
  }

  private scheduleNext(delayMs: number): void {
    if (this.pollTimer) clearTimeout(this.pollTimer);
    if (delayMs === 0) {
      this.poll();
    } else {
      this.pollTimer = setTimeout(() => this.poll(), delayMs);
    }
  }
}
