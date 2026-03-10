import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { QuercheckerListingDto } from '../api/model/quercheckerListingDto';
import { WhListingDetailDto } from '../api/model/whListingDetailDto';
import { API_URLS } from './api-urls';

export type { QuercheckerListingDto } from '../api/model/quercheckerListingDto';
export type { WhListingDetailDto } from '../api/model/whListingDetailDto';

@Injectable({
  providedIn: 'root',
})
export class ListingService {
  private readonly http = inject(HttpClient);

  private readonly lastViewed = new Map<number, number>();

  shouldRecordView(id: number): boolean {
    const last = this.lastViewed.get(id);
    if (!last || Date.now() - last > 60_000) {
      this.lastViewed.set(id, Date.now());
      return true;
    }
    return false;
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${API_URLS.listings}/${id}`);
  }

  recordView(id: number): Observable<void> {
    return this.http.post<void>(`${API_URLS.listings}/${id}/views`, {});
  }

  getDetail(id: number): Observable<WhListingDetailDto> {
    return this.http.get<WhListingDetailDto>(`${API_URLS.listings}/${id}/detail`);
  }

  updateNote(id: number, note: string): Observable<WhListingDetailDto> {
    return this.http.put<WhListingDetailDto>(`${API_URLS.listings}/${id}/detail/note`, { note });
  }

  updateRating(id: number, rating: 'UP' | 'DOWN' | null): Observable<WhListingDetailDto> {
    return this.http.put<WhListingDetailDto>(`${API_URLS.listings}/${id}/detail/rating`, { rating });
  }

  updateInterest(id: number, level: 'LOW' | 'MEDIUM' | 'HIGH' | null): Observable<WhListingDetailDto> {
    return this.http.put<WhListingDetailDto>(`${API_URLS.listings}/${id}/detail/interest`, { level });
  }

  updateTags(id: number, tags: string[]): Observable<WhListingDetailDto> {
    return this.http.put<WhListingDetailDto>(`${API_URLS.listings}/${id}/detail/tags`, { tags });
  }
}
