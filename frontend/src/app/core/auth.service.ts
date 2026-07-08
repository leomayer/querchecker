import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { tap } from 'rxjs';
import { API_URLS } from './api-urls';

export type AuthRole = 'USER' | 'SUPERUSER' | null;

interface AuthStatusResponse {
  authenticated: boolean;
  role: AuthRole;
  hasKey: boolean;
  accessKeyId: number | null;
  quotaRemaining: number | null;
  quotaLimit: number | null;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  readonly authenticated = signal(false);
  readonly role = signal<AuthRole>(null);
  /** False for the dev-only LocalProfileAuthFilter SUPERUSER — there's no session to log out of. */
  readonly hasKey = signal(false);
  /** Id of the AccessKey backing the current session — null without a real key (dev/local-profile). */
  readonly accessKeyId = signal<number | null>(null);
  /** Heute verbleibendes Key-Kontingent — nur für role === 'USER' befüllt, sonst null. */
  readonly quotaRemaining = signal<number | null>(null);
  /** Tageskontingent des Keys — nur für role === 'USER' befüllt, sonst null. */
  readonly quotaLimit = signal<number | null>(null);
  readonly isSuperuser = computed(() => this.role() === 'SUPERUSER');
  /** Verbrauch heute (limit - remaining) für die "X/Y"-Anzeige. */
  readonly quotaUsed = computed(() => {
    const limit = this.quotaLimit();
    const remaining = this.quotaRemaining();
    return limit !== null && remaining !== null ? limit - remaining : null;
  });

  constructor() {
    this.refresh();
  }

  refresh(): void {
    this.http.get<AuthStatusResponse>(API_URLS.authMe).subscribe({
      next: (res) => this.applyStatus(res),
      error: () => this.applyStatus(null),
    });
  }

  login(key: string) {
    return this.http
      .post<{ role: AuthRole }>(API_URLS.authLoginWithKey, { key })
      .pipe(tap(() => this.refresh()));
  }

  logout() {
    return this.http.post(API_URLS.authLogout, {}).pipe(tap(() => this.applyStatus(null)));
  }

  private applyStatus(res: AuthStatusResponse | null): void {
    this.authenticated.set(res?.authenticated ?? false);
    this.role.set(res?.role ?? null);
    this.hasKey.set(res?.hasKey ?? false);
    this.accessKeyId.set(res?.accessKeyId ?? null);
    this.quotaRemaining.set(res?.quotaRemaining ?? null);
    this.quotaLimit.set(res?.quotaLimit ?? null);
  }
}
