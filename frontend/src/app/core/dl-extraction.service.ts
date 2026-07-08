import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DlExtractionTermDto } from '../api/model/dlExtractionTermDto';
import { API_URLS } from './api-urls';

export interface DlSettingsDto {
  contextMaxTokens: number;
}

export interface DlExtractionStatusResponse {
  extractionStatus:
    | 'DONE'
    | 'PENDING'
    | 'CANCELLED'
    | 'NONE'
    | 'FAILED'
    // Transient, only ever arrives via SSE (never persisted, never returned by GET /terms) —
    // background rate-limit/volume-cap block for DL-Extraktion (Konzept Kap. 4, Ebene-2b).
    | 'RATE_LIMITED'
    | 'EXTRACTION_QUOTA_EXCEEDED';
  terms: DlExtractionTermDto[];
  /** Best term from the configured source model — pre-fills the research search field. */
  suggestedTerm?: string | null;
}

@Injectable({
  providedIn: 'root',
})
export class DlExtractionService {
  private readonly http = inject(HttpClient);

  getTerms(whItemId: number): Observable<DlExtractionStatusResponse> {
    return this.http.get<DlExtractionStatusResponse>(API_URLS.dlExtractionTerms(whItemId));
  }

  getSettings(): Observable<DlSettingsDto> {
    return this.http.get<DlSettingsDto>(API_URLS.dlSettings);
  }

  updateSettings(settings: DlSettingsDto): Observable<DlSettingsDto> {
    return this.http.put<DlSettingsDto>(API_URLS.dlSettings, settings);
  }
}
