import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_URLS } from './api-urls';

export interface ProviderUsage {
  calls: number;
  avgDurationMs: number;
}

export interface ApiUsageResponse {
  brave: ProviderUsage;
  groq: ProviderUsage;
  openRouter: ProviderUsage;
}

@Injectable({ providedIn: 'root' })
export class UsageService {
  private readonly http = inject(HttpClient);

  getUsage(): Observable<ApiUsageResponse> {
    return this.http.get<ApiUsageResponse>(API_URLS.apiUsage);
  }
}
