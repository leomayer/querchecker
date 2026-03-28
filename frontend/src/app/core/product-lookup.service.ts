import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_URLS } from './api-urls';
export { LookupResult } from './model/lookup.model';

export interface FullSpecsResult {
  icecatSpecsJson: string | null;
}

@Injectable({ providedIn: 'root' })
export class ProductLookupService {
  private readonly http = inject(HttpClient);

  lookup(listingId: number, lookupTerm: string): Observable<LookupResult> {
    return this.http.post<LookupResult>(API_URLS.productLookup(listingId), { lookupTerm });
  }

  loadFullSpecs(listingId: number, icecatId: string): Observable<FullSpecsResult> {
    return this.http.post<FullSpecsResult>(API_URLS.productFullSpecs(listingId), { icecatId });
  }
}
