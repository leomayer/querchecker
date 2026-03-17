import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DlExtractionTermDto } from '../api/model/dlExtractionTermDto';
import { API_URLS } from './api-urls';

export interface DlSettingsDto {
  contextMaxTokens: number;
}

@Injectable({
  providedIn: 'root',
})
export class DlExtractionService {
  private readonly http = inject(HttpClient);

  getTerms(itemTextId: number): Observable<DlExtractionTermDto[]> {
    return this.http.get<DlExtractionTermDto[]>(API_URLS.dlExtractionTerms(itemTextId));
  }

  getSettings(): Observable<DlSettingsDto> {
    return this.http.get<DlSettingsDto>(API_URLS.dlSettings);
  }

  updateSettings(settings: DlSettingsDto): Observable<DlSettingsDto> {
    return this.http.put<DlSettingsDto>(API_URLS.dlSettings, settings);
  }
}
