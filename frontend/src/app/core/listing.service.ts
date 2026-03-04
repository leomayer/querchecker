import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { QuercheckerListingDto } from '../api/model/quercheckerListingDto';
import { QuercheckerNoteDto } from '../api/model/quercheckerNoteDto';
import { API_URLS } from './api-urls';

export type { QuercheckerListingDto } from '../api/model/quercheckerListingDto';
export type { QuercheckerNoteDto } from '../api/model/quercheckerNoteDto';

@Injectable({
  providedIn: 'root',
})
export class ListingService {
  private readonly http = inject(HttpClient);

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${API_URLS.listings}/${id}`);
  }

  getNotes(listingId: number): Observable<QuercheckerNoteDto[]> {
    return this.http.get<QuercheckerNoteDto[]>(`${API_URLS.listings}/${listingId}/notes`);
  }

  createNote(listingId: number, note: QuercheckerNoteDto): Observable<QuercheckerNoteDto> {
    return this.http.post<QuercheckerNoteDto>(`${API_URLS.listings}/${listingId}/notes`, note);
  }

  updateNote(
    listingId: number,
    noteId: number,
    note: QuercheckerNoteDto,
  ): Observable<QuercheckerNoteDto> {
    return this.http.put<QuercheckerNoteDto>(
      `${API_URLS.listings}/${listingId}/notes/${noteId}`,
      note,
    );
  }

  deleteNote(listingId: number, noteId: number): Observable<void> {
    return this.http.delete<void>(`${API_URLS.listings}/${listingId}/notes/${noteId}`);
  }
}
