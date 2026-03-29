import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DlExtractionTermDto } from '../api/model/dlExtractionTermDto';
import { API_URLS } from './api-urls';

export interface DlSettingsDto {
  contextMaxTokens: number;
}

export interface DlExtractionStatusResponse {
  extractionStatus: 'DONE' | 'PENDING' | 'CANCELLED' | 'NONE' | 'FAILED';
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
