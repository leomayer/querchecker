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
  readonly isSuperuser = computed(() => this.role() === 'SUPERUSER');

  constructor() {
    this.refresh();
  }

  refresh(): void {
    this.http.get<AuthStatusResponse>(API_URLS.authMe).subscribe({
      next: (res) => this.applyStatus(res.authenticated, res.role, res.hasKey, res.accessKeyId),
      error: () => this.applyStatus(false, null, false, null),
    });
  }

  login(key: string) {
    return this.http
      .post<{ role: AuthRole }>(API_URLS.authLoginWithKey, { key })
      .pipe(tap(() => this.refresh()));
  }

  logout() {
    return this.http
      .post(API_URLS.authLogout, {})
      .pipe(tap(() => this.applyStatus(false, null, false, null)));
  }

  private applyStatus(
    authenticated: boolean,
    role: AuthRole,
    hasKey: boolean,
    accessKeyId: number | null,
  ): void {
    this.authenticated.set(authenticated);
    this.role.set(role);
    this.hasKey.set(hasKey);
    this.accessKeyId.set(accessKeyId);
  }
}
