import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { WhItemDto } from '../api/model/whItemDto';
import { WhDetailDto } from '../api/model/whDetailDto';
import { API_URLS } from './api-urls';

export type { WhItemDto } from '../api/model/whItemDto';
export type { WhDetailDto } from '../api/model/whDetailDto';

@Injectable({
  providedIn: 'root',
})
export class ListingService {
  private readonly http = inject(HttpClient);

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${API_URLS.listings}/${id}`);
  }

  openDetail(id: number): Observable<WhDetailDto> {
    return this.http.post<WhDetailDto>(`${API_URLS.listings}/${id}/detail`, {});
  }

  updateNote(id: number, note: string): Observable<WhDetailDto> {
    return this.http.put<WhDetailDto>(`${API_URLS.listings}/${id}/detail/note`, { note });
  }

  updateRating(id: number, rating: 'UP' | 'DOWN' | null): Observable<WhDetailDto> {
    return this.http.put<WhDetailDto>(`${API_URLS.listings}/${id}/detail/rating`, { rating });
  }

  updateInterest(
    id: number,
    level: 'LOW' | 'MEDIUM' | 'HIGH' | null,
  ): Observable<WhDetailDto> {
    return this.http.put<WhDetailDto>(`${API_URLS.listings}/${id}/detail/interest`, { level });
  }

  updateTags(id: number, tags: string[]): Observable<WhDetailDto> {
    return this.http.put<WhDetailDto>(`${API_URLS.listings}/${id}/detail/tags`, { tags });
  }

  cleanupByRating(rating: string, olderThanDays: number): Observable<{ deleted: number }> {
    return this.http.delete<{ deleted: number }>(`${API_URLS.listings}/cleanup`, {
      params: { rating, olderThanDays: olderThanDays.toString() },
    });
  }
}
