import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, shareReplay } from 'rxjs';
import { API_URLS } from './api-urls';

export interface PreferenceEntry {
  categoryId: number;
  categoryName: string;
  fieldKeys: string[];
}

@Injectable({ providedIn: 'root' })
export class PreferencesService {
  private readonly http = inject(HttpClient);
  private cache$: Observable<PreferenceEntry[]> | null = null;

  getAll(): Observable<PreferenceEntry[]> {
    if (!this.cache$) {
      this.cache$ = this.http
        .get<PreferenceEntry[]>(API_URLS.settingsPreferences)
        .pipe(shareReplay(1));
    }
    return this.cache$;
  }

  invalidate(): void {
    this.cache$ = null;
  }

  save(categoryId: number, fieldKeys: string[]): Observable<PreferenceEntry> {
    return this.http.put<PreferenceEntry>(API_URLS.settingsPreferenceCategory(categoryId), {
      fieldKeys,
    });
  }

  delete(categoryId: number): Observable<PreferenceEntry> {
    return this.http.put<PreferenceEntry>(API_URLS.settingsPreferenceCategory(categoryId), {
      fieldKeys: [],
    });
  }
}
