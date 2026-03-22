import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_URLS } from './api-urls';

export interface PreferenceEntry {
  categoryId: number;
  categoryName: string;
  fieldKeys: string[];
}

@Injectable({ providedIn: 'root' })
export class PreferencesService {
  private readonly http = inject(HttpClient);

  getAll(): Observable<PreferenceEntry[]> {
    return this.http.get<PreferenceEntry[]>(API_URLS.settingsPreferences);
  }

  save(categoryId: number, fieldKeys: string[]): Observable<PreferenceEntry> {
    return this.http.put<PreferenceEntry>(API_URLS.settingsPreferenceCategory(categoryId), { fieldKeys });
  }

  delete(categoryId: number): Observable<PreferenceEntry> {
    return this.http.put<PreferenceEntry>(API_URLS.settingsPreferenceCategory(categoryId), { fieldKeys: [] });
  }
}
