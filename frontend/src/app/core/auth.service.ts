import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { tap } from 'rxjs';
import { API_URLS } from './api-urls';

export type AuthRole = 'USER' | 'SUPERUSER' | null;

interface AuthStatusResponse {
  authenticated: boolean;
  role: AuthRole;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  readonly authenticated = signal(false);
  readonly role = signal<AuthRole>(null);
  readonly isSuperuser = computed(() => this.role() === 'SUPERUSER');

  constructor() {
    this.refresh();
  }

  refresh(): void {
    this.http.get<AuthStatusResponse>(API_URLS.authMe).subscribe({
      next: (res) => this.applyStatus(res.authenticated, res.role),
      error: () => this.applyStatus(false, null),
    });
  }

  login(key: string) {
    return this.http.post<{ role: AuthRole }>(API_URLS.authLoginWithKey, { key }).pipe(
      tap((res) => this.applyStatus(true, res.role)),
    );
  }

  logout() {
    return this.http.post(API_URLS.authLogout, {}).pipe(tap(() => this.applyStatus(false, null)));
  }

  private applyStatus(authenticated: boolean, role: AuthRole): void {
    this.authenticated.set(authenticated);
    this.role.set(role);
  }
}
