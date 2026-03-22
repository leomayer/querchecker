import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { IcecatResponse } from './model/icecat.model';
import { API_URLS } from './api-urls';

@Injectable({ providedIn: 'root' })
export class IcecatService {
  private readonly http = inject(HttpClient);

  getByGtin(ean: string): Observable<IcecatResponse> {
    return this.http.get<IcecatResponse>(API_URLS.icecatSpec(ean));
  }
}
