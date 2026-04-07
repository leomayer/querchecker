import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_URLS } from './api-urls';

export interface ProviderUsage {
  calls: number;
  tokensIn: number;
  tokensOut: number;
  quotaUsage: number;
  quotaLimit: number;
  model: string | null;
  quotaPeriod: 'DAILY' | 'MONTHLY' | null;
  rateLimitCount: number;
  rateLimitEstimatedTokens: number;
}

export interface ModelUsage {
  model: string;
  calls: number;
  tokensIn: number;
  tokensOut: number;
}

export interface ApiUsageResponse {
  activeSearchProvider: 'BRAVE' | 'GOOGLE_DISCOVERY';
  activeLlmProvider: 'GROQ' | 'OPENROUTER';
  brave: ProviderUsage;
  googleDiscovery: ProviderUsage;
  groq: ProviderUsage;
  openRouter: ProviderUsage;
  groqModelBreakdown: ModelUsage[];
}

@Injectable({ providedIn: 'root' })
export class UsageService {
  private readonly http = inject(HttpClient);

  getUsage(): Observable<ApiUsageResponse> {
    return this.http.get<ApiUsageResponse>(API_URLS.apiUsage);
  }
}
