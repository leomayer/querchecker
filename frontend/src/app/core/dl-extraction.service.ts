import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DlExtractionTermDto } from '../api/model/dlExtractionTermDto';
import { API_URLS } from './api-urls';

@Injectable({
  providedIn: 'root',
})
export class DlExtractionService {
  private readonly http = inject(HttpClient);

  getTerms(itemTextId: number): Observable<DlExtractionTermDto[]> {
    return this.http.get<DlExtractionTermDto[]>(API_URLS.dlExtractionTerms(itemTextId));
  }
}
