import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { QuercheckerListingDto } from '../api/model/quercheckerListingDto';
import { QuercheckerNoteDto } from '../api/model/quercheckerNoteDto';

// Re-export so existing component imports keep working
export type { QuercheckerListingDto } from '../api/model/quercheckerListingDto';
export type { QuercheckerNoteDto } from '../api/model/quercheckerNoteDto';

@Injectable({
  providedIn: 'root',
})
export class ListingService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/listings';

  getAll(): Observable<QuercheckerListingDto[]> {
    return this.http.get<QuercheckerListingDto[]>(this.baseUrl);
  }

  getById(id: number): Observable<QuercheckerListingDto> {
    return this.http.get<QuercheckerListingDto>(`${this.baseUrl}/${id}`);
  }

  create(listing: QuercheckerListingDto): Observable<QuercheckerListingDto> {
    return this.http.post<QuercheckerListingDto>(this.baseUrl, listing);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  search(
    keyword: string,
    rows: number,
    priceFrom?: number,
    priceTo?: number,
  ): Observable<QuercheckerListingDto[]> {
    let params = new HttpParams()
      .set('keyword', keyword)
      .set('rows', rows.toString());
    if (priceFrom != null) params = params.set('priceFrom', priceFrom.toString());
    if (priceTo != null) params = params.set('priceTo', priceTo.toString());
    return this.http.get<QuercheckerListingDto[]>('/api/wh/search', { params });
  }

  getNotes(listingId: number): Observable<QuercheckerNoteDto[]> {
    return this.http.get<QuercheckerNoteDto[]>(`${this.baseUrl}/${listingId}/notes`);
  }

  createNote(listingId: number, note: QuercheckerNoteDto): Observable<QuercheckerNoteDto> {
    return this.http.post<QuercheckerNoteDto>(`${this.baseUrl}/${listingId}/notes`, note);
  }

  updateNote(
    listingId: number,
    noteId: number,
    note: QuercheckerNoteDto,
  ): Observable<QuercheckerNoteDto> {
    return this.http.put<QuercheckerNoteDto>(
      `${this.baseUrl}/${listingId}/notes/${noteId}`,
      note,
    );
  }

  deleteNote(listingId: number, noteId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${listingId}/notes/${noteId}`);
  }
}
