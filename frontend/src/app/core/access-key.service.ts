import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_URLS } from './api-urls';
import { AuthRole } from './auth.service';

export interface AccessKeyOverview {
  id: number;
  role: AuthRole;
  quotaLimit: number;
  createdAt: string;
  lastUsedAt: string | null;
  revoked: boolean;
}

export interface AccessKeyCreated {
  id: number;
  secretKey: string;
  role: AuthRole;
  quotaLimit: number;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class AccessKeyService {
  private readonly http = inject(HttpClient);

  listKeys(): Observable<AccessKeyOverview[]> {
    return this.http.get<AccessKeyOverview[]>(API_URLS.authKeys);
  }

  generateKey(role: AuthRole, quotaLimit: number): Observable<AccessKeyCreated> {
    return this.http.post<AccessKeyCreated>(API_URLS.authGenerateKey, { role, quotaLimit });
  }

  updateKey(id: number, role: AuthRole, quotaLimit: number | null): Observable<AccessKeyOverview> {
    return this.http.patch<AccessKeyOverview>(API_URLS.authKey(id), { role, quotaLimit });
  }

  revoke(id: number): Observable<AccessKeyOverview> {
    return this.http.post<AccessKeyOverview>(API_URLS.authKeyRevoke(id), {});
  }

  unrevoke(id: number): Observable<AccessKeyOverview> {
    return this.http.post<AccessKeyOverview>(API_URLS.authKeyUnrevoke(id), {});
  }

  deleteKey(id: number): Observable<void> {
    return this.http.delete<void>(API_URLS.authKey(id));
  }
}
