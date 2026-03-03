import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

// Diese Typen werden nach `npm run generate-api` durch die generierten Typen ersetzt.
// Bis dahin als lokale Platzhalter definiert.
export interface QuercheckerListingDto {
  id?: number;
  whId: string;
  title: string;
  description?: string;
  price?: number;
  location?: string;
  url: string;
  listedAt?: string;
  fetchedAt?: string;
}

export interface QuercheckerNoteDto {
  id?: number;
  whListingId?: number;
  content: string;
  createdAt?: string;
  updatedAt?: string;
}

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

  getNotes(listingId: number): Observable<QuercheckerNoteDto[]> {
    return this.http.get<QuercheckerNoteDto[]>(`${this.baseUrl}/${listingId}/notes`);
  }

  createNote(listingId: number, note: QuercheckerNoteDto): Observable<QuercheckerNoteDto> {
    return this.http.post<QuercheckerNoteDto>(`${this.baseUrl}/${listingId}/notes`, note);
  }

  updateNote(listingId: number, noteId: number, note: QuercheckerNoteDto): Observable<QuercheckerNoteDto> {
    return this.http.put<QuercheckerNoteDto>(`${this.baseUrl}/${listingId}/notes/${noteId}`, note);
  }

  deleteNote(listingId: number, noteId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${listingId}/notes/${noteId}`);
  }
}
