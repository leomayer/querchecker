import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { EanSearchResponse } from './model/ean-search.model';
import { API_URLS } from './api-urls';

@Injectable({ providedIn: 'root' })
export class EanSearchService {
  private readonly http = inject(HttpClient);

  search(query: string): Observable<EanSearchResponse> {
    const params = new HttpParams().set('q', query);
    return this.http.get<EanSearchResponse>(API_URLS.productSearch, { params });
  }
}
