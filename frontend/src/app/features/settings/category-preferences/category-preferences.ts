import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { PreferenceEntry, PreferencesService } from '../../../core/preferences.service';
import { WhCategoryDto } from '../../../api/model/whCategoryDto';
import { API_URLS } from '../../../core/api-urls';

interface EditState {
  categoryId: number;
  draft: string;
}

interface CategoryOption {
  id: number;
  displayName: string;
}

@Component({
  selector: 'app-category-preferences',
  imports: [
    FormsModule,
    MatButtonModule,
    MatChipsModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatTooltipModule,
  ],
  templateUrl: './category-preferences.html',
  styleUrl: './category-preferences.scss',
})
export class CategoryPreferences implements OnInit {
  private readonly prefService = inject(PreferencesService);

  readonly preferences = signal<PreferenceEntry[]>([]);
  readonly loading = signal(false);
  readonly editState = signal<EditState | null>(null);
  readonly saveError = signal<string | null>(null);

  readonly preferencesWithKeywords = computed(() =>
    this.preferences().filter((p) => p.fieldKeys.length > 0),
  );

  // New preference form
  readonly showNewForm = signal(false);
  readonly newCategoryId = signal<number | null>(null);
  readonly newDraft = signal('');
  readonly newSaveError = signal<string | null>(null);

  private readonly categoriesResource = httpResource<WhCategoryDto[]>(
    () => ({ url: API_URLS.whCategories }),
    { defaultValue: [] },
  );

  readonly categoryOptions = computed<CategoryOption[]>(() => {
    const all = this.categoriesResource.value();
    const result: CategoryOption[] = [];
    const flatten = (cats: WhCategoryDto[], parentName?: string): void => {
      for (const cat of cats) {
        if (cat.id == null || !cat.name) continue;
        const displayName = parentName ? `${parentName} › ${cat.name}` : cat.name;
        result.push({ id: cat.id, displayName });
        if (cat.children?.length) flatten(cat.children, cat.name);
      }
    };
    flatten(all);
    return result.sort((a, b) => a.displayName.localeCompare(b.displayName));
  });

  readonly availableCategoryOptions = computed<CategoryOption[]>(() => {
    const usedIds = new Set(this.preferences().map((p) => p.categoryId));
    return this.categoryOptions().filter((o) => !usedIds.has(o.id));
  });

  readonly draftKeywords = computed<string[]>(() => {
    const draft = this.editState()?.draft ?? '';
    return draft
      .split(',')
      .map((k) => k.trim())
      .filter((k) => k.length > 0);
  });

  readonly newDraftKeywords = computed<string[]>(() =>
    this.newDraft()
      .split(',')
      .map((k) => k.trim())
      .filter((k) => k.length > 0),
  );

  ngOnInit(): void {
    this.loadAll();
  }

  private loadAll(): void {
    this.prefService.getAll().subscribe({
      next: (list) => this.preferences.set(list),
    });
  }

  startEdit(pref: PreferenceEntry): void {
    this.showNewForm.set(false);
    this.saveError.set(null);
    this.editState.set({
      categoryId: pref.categoryId,
      draft: pref.fieldKeys.join(', '),
    });
  }

  cancelEdit(): void {
    this.editState.set(null);
    this.saveError.set(null);
  }

  deletePref(categoryId: number): void {
    this.prefService.delete(categoryId).subscribe({
      next: () => {
        this.preferences.update((list) => list.filter((p) => p.categoryId !== categoryId));
      },
    });
  }

  saveEdit(): void {
    const state = this.editState();
    if (!state) return;
    const keywords = this.draftKeywords();
    if (keywords.length === 0) {
      this.saveError.set('Mindestens 1 Keyword erforderlich.');
      return;
    }
    if (keywords.length > 5) {
      this.saveError.set('Maximal 5 Keywords erlaubt.');
      return;
    }
    this.loading.set(true);
    this.saveError.set(null);
    this.prefService.save(state.categoryId, keywords).subscribe({
      next: (updated) => {
        this.preferences.update((list) =>
          list.map((p) => (p.categoryId === updated.categoryId ? updated : p)),
        );
        this.editState.set(null);
        this.loading.set(false);
      },
      error: (err) => {
        this.saveError.set(err?.error?.message ?? 'Fehler beim Speichern.');
        this.loading.set(false);
      },
    });
  }

  openNewForm(): void {
    this.editState.set(null);
    this.newCategoryId.set(null);
    this.newDraft.set('');
    this.newSaveError.set(null);
    this.showNewForm.set(true);
  }

  cancelNewForm(): void {
    this.showNewForm.set(false);
    this.newSaveError.set(null);
  }

  saveNew(): void {
    const categoryId = this.newCategoryId();
    if (categoryId == null) return;
    const keywords = this.newDraftKeywords();
    if (keywords.length === 0) {
      this.newSaveError.set('Mindestens 1 Keyword erforderlich.');
      return;
    }
    if (keywords.length > 5) {
      this.newSaveError.set('Maximal 5 Keywords erlaubt.');
      return;
    }
    this.loading.set(true);
    this.newSaveError.set(null);
    this.prefService.save(categoryId, keywords).subscribe({
      next: (created) => {
        this.preferences.update((list) => [...list, created]);
        this.showNewForm.set(false);
        this.loading.set(false);
      },
      error: (err) => {
        this.newSaveError.set(err?.error?.message ?? 'Fehler beim Speichern.');
        this.loading.set(false);
      },
    });
  }
}
