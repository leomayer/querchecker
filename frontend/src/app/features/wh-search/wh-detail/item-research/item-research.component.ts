import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { DecimalPipe, SlicePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatIconButton } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { DlExtractionTermDto } from '../../../../api/model/dlExtractionTermDto';
import { WhDetailDto } from '../../../../api/model/whDetailDto';
import { ExtractionStore } from '../../extraction.store';
import { IcecatData, IcecatFeatureGroup, SpecsFeatureGroup } from '../../../../core/model/icecat.model';
import { IcecatAccordionComponent } from './icecat-accordion/icecat-accordion.component';
import { SpecsAccordionComponent } from './specs-accordion/specs-accordion.component';
import { PreferenceEntry, PreferencesService } from '../../../../core/preferences.service';

interface TermGroup {
  modelName: string;
  terms: DlExtractionTermDto[];
  durationMs?: number;
}

type LookupState = 'empty' | 'loading' | 'COMPLETE' | 'FAILED' | 'QUOTA_EXCEEDED';

@Component({
  selector: 'app-item-research',
  imports: [
    DecimalPipe,
    SlicePipe,
    FormsModule,
    MatFormFieldModule,
    MatIconModule,
    MatIconButton,
    MatInputModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    IcecatAccordionComponent,
    SpecsAccordionComponent,
  ],
  templateUrl: './item-research.component.html',
  styleUrl: './item-research.component.scss',
})
export class ItemResearchComponent {
  readonly detail = input.required<WhDetailDto>();

  private readonly extractionStore = inject(ExtractionStore);

  protected readonly searchTerm = signal('');

  constructor() {
    this.loadPreferences();

    effect(() => {
      const id = this.detail().whItemId;
      if (id != null) {
        this.extractionStore.loadExistingTerms(id);
      }
    });

    // Pre-fill from store suggestion, but only when the field is still empty
    // — preserves any text the user has already typed.
    // Also auto-triggers the spec-lookup when a suggested term arrives and no
    // lookup result exists yet — the backend returns cached data instantly when available.
    effect(() => {
      const id = this.detail().whItemId;
      const listingId = this.detail().id;
      if (id == null || listingId == null) return;
      const suggested = this.extractionStore.suggestedTerms()[id];
      if (!suggested) return;
      if (!this.searchTerm()) {
        this.searchTerm.set(suggested);
      }
      if (!this.extractionStore.lookupResults()[id] &&
          !this.extractionStore.lookupLoadingIds().includes(id)) {
        this.extractionStore.lookup(id, listingId, suggested);
      }
    });
  }

  protected readonly state = computed<'idle' | 'loading' | 'done'>(() => {
    const id = this.detail().whItemId;
    if (id == null) return 'idle';
    return id in this.extractionStore.results() ? 'done' : 'loading';
  });

  protected readonly termGroups = computed<TermGroup[]>(() => {
    const id = this.detail().whItemId;
    if (id == null) return [];
    return this.groupByModel(this.extractionStore.results()[id] ?? []);
  });

  // --- Spec-Lookup ---

  protected readonly lookupState = computed<LookupState>(() => {
    const id = this.detail().whItemId;
    if (id == null) return 'empty';
    if (this.extractionStore.lookupLoadingIds().includes(id)) return 'loading';
    const result = this.extractionStore.lookupResults()[id];
    if (!result) return 'empty';
    return result.lookupStatus as LookupState;
  });

  protected readonly orderedQuickFacts = computed<[string, string][]>(() => {
    const id = this.detail().whItemId;
    if (id == null) return [];
    const result = this.extractionStore.lookupResults()[id];
    if (!result?.quickFacts) return [];
    const facts = result.quickFacts;
    const prefKeys = Array.from(this.preferredKeySet());
    const preferred = prefKeys
      .filter((k) => k in facts)
      .map((k) => [k, facts[k]] as [string, string]);
    const rest = Object.entries(facts)
      .filter(([k]) => !prefKeys.includes(k))
      .sort(([a], [b]) => a.localeCompare(b)) as [string, string][];
    return [...preferred, ...rest];
  });

  protected readonly lookupIcecatId = computed<string | null>(() => {
    const id = this.detail().whItemId;
    if (id == null) return null;
    return this.extractionStore.lookupResults()[id]?.icecatId ?? null;
  });

  protected readonly showFullSpecsButton = computed<boolean>(() => {
    const id = this.detail().whItemId;
    if (id == null) return false;
    const result = this.extractionStore.lookupResults()[id];
    return result?.icecatId != null && result?.sourceType === 'ICECAT';
  });

  protected readonly lookupSourceDomain = computed<string | null>(() => {
    const id = this.detail().whItemId;
    if (id == null) return null;
    return this.extractionStore.lookupResults()[id]?.sourceDomain ?? null;
  });

  protected readonly lookupSourceLabel = computed<string | null>(() => {
    const domain = this.lookupSourceDomain();
    if (!domain) return null;
    const labelMap: Record<string, string> = {
      'icecat.biz': 'Icecat',
      'gsmarena.com': 'GSMArena',
      'flatpanelshd.com': 'FlatpanelsHD',
    };
    return labelMap[domain] || domain;
  });

  protected readonly lookupSourceUrl = computed<string | null>(() => {
    const id = this.detail().whItemId;
    if (id == null) return null;
    return this.extractionStore.lookupResults()[id]?.sourceUrl ?? null;
  });

  protected readonly lookupTerm = computed<string>(() => {
    const id = this.detail().whItemId;
    if (id == null) return '';
    return this.extractionStore.lookupResults()[id]?.lookupTerm ?? '';
  });

  protected readonly geizhalUrl = computed<string | null>(() => {
    const term = this.lookupTerm() || this.searchTerm().trim();
    if (!term) return null;
    return 'https://geizhals.at/?fs=' + encodeURIComponent(term);
  });

  protected readonly fullSpecsLoading = computed<boolean>(() => {
    const id = this.detail().whItemId;
    if (id == null) return false;
    return this.extractionStore.fullSpecsLoadingIds().includes(id);
  });

  protected readonly fullSpecsLoaded = computed<boolean>(() => {
    const id = this.detail().whItemId;
    if (id == null) return false;
    return this.extractionStore.fullSpecsLoaded()[id] ?? false;
  });

  protected readonly icecatFeatureGroups = computed<IcecatFeatureGroup[]>(() => {
    const id = this.detail().whItemId;
    if (id == null) return [];
    return this.extractionStore.fullSpecsData()[id] ?? [];
  });

  protected readonly icecatGeneralInfo = computed<IcecatData['GeneralInfo'] | undefined>(() => {
    const id = this.detail().whItemId;
    if (id == null) return undefined;
    return this.extractionStore.fullSpecsGeneralInfo()[id];
  });

  protected readonly specsFeatureGroups = computed<SpecsFeatureGroup[]>(() => {
    const id = this.detail().whItemId;
    if (id == null) return [];
    return this.extractionStore.lookupResults()[id]?.featureGroups ?? [];
  });

  protected readonly noIcecatData = computed<boolean>(() => {
    const id = this.detail().whItemId;
    if (id == null) return false;
    const result = this.extractionStore.lookupResults()[id];
    if (!result || result.lookupStatus !== 'COMPLETE') return false;
    const hasQuickFacts = Object.keys(result.quickFacts ?? {}).length > 0;
    const hasFeatureGroups = (result.featureGroups?.length ?? 0) > 0;
    return !hasQuickFacts && !result.icecatId && !hasFeatureGroups;
  });

  protected readonly icecatPageUrl = computed<string | null>(() => {
    const id = this.detail().whItemId;
    if (id == null) return null;
    // Prefer the canonical catalog URL from the full specs JSON
    const catalogUrl = this.extractionStore.icecatCatalogUrls()[id];
    if (catalogUrl) return catalogUrl;
    // Fallback: construct an icecat.biz search using the lookup term
    const term = this.lookupTerm() || this.searchTerm().trim();
    const icecatId = this.lookupIcecatId();
    if (!term && !icecatId) return null;
    const q = icecatId ? `${term} ${icecatId}`.trim() : term;
    return 'https://icecat.biz/de/search/?q=' + encodeURIComponent(q);
  });

  protected readonly icecatMismatch = computed<boolean>(() => {
    const info = this.icecatGeneralInfo();
    if (!info) return false;
    const brand = (info.Brand ?? '').toLowerCase().trim();
    if (brand.length < 2) return false;
    const term = (this.lookupTerm() || this.searchTerm()).toLowerCase();
    return !term.includes(brand);
  });

  // --- Preferences ---

  private readonly prefService = inject(PreferencesService);
  private readonly preferences = signal<PreferenceEntry[]>([]);

  private loadPreferences(): void {
    this.prefService.getAll().subscribe((list) => this.preferences.set(list));
  }

  /** The leaf category ID to save new preferences to (most specific in path). */
  protected readonly activeCategoryId = computed<number | null>(() => {
    const path = this.detail().categoryPath ?? [];
    return path.length > 0 ? (path[path.length - 1].id ?? null) : null;
  });

  /** The preference entry currently active for this listing's category path. */
  private readonly activePrefEntry = computed<PreferenceEntry | null>(() => {
    const path = this.detail().categoryPath ?? [];
    const prefs = this.preferences();
    for (let i = path.length - 1; i >= 0; i--) {
      const entry = prefs.find((p) => p.categoryId === path[i].id);
      if (entry) return entry;
    }
    return null;
  });

  protected readonly preferredKeySet = computed<Set<string>>(() => {
    const entry = this.activePrefEntry();
    if (!entry || entry.fieldKeys.length === 0) return new Set<string>();
    return new Set(entry.fieldKeys.map((k) => k.toLowerCase()));
  });

  protected togglePreference(featureName: string): void {
    const catId = this.activeCategoryId();
    if (catId == null) return;
    const key = featureName.toLowerCase();
    const currentKeys = (this.activePrefEntry()?.fieldKeys ?? []).map((k) => k.toLowerCase());
    // Use same fuzzy check as isPreferred for removal, exact add for insertion
    const alreadyPreferred = currentKeys.some((k) => key.includes(k));
    const newKeys = alreadyPreferred
      ? currentKeys.filter((k) => !key.includes(k))
      : [...currentKeys, key];

    // Optimistic update
    const patch = (list: PreferenceEntry[]): PreferenceEntry[] => {
      const idx = list.findIndex((p) => p.categoryId === catId);
      if (idx >= 0) {
        return list.map((p, i) => (i === idx ? { ...p, fieldKeys: newKeys } : p));
      }
      return [...list, { categoryId: catId, categoryName: '', fieldKeys: newKeys }];
    };
    this.preferences.update(patch);

    this.prefService.save(catId, newKeys).subscribe({
      next: (saved) =>
        this.preferences.update((list) =>
          list.map((p) => (p.categoryId === saved.categoryId ? saved : p)),
        ),
      error: () => this.loadPreferences(), // rollback on error
    });
  }

  // --- Handlers ---

  protected onLookup(): void {
    const term = this.searchTerm().trim();
    const whItemId = this.detail().whItemId;
    const listingId = this.detail().id;
    if (!term || whItemId == null || listingId == null) return;
    this.extractionStore.lookup(whItemId, listingId, term);
  }

  protected onLoadFullSpecs(): void {
    const icecatId = this.lookupIcecatId();
    const whItemId = this.detail().whItemId;
    const listingId = this.detail().id;
    if (!icecatId || whItemId == null || listingId == null) return;
    this.extractionStore.loadFullSpecs(whItemId, listingId, icecatId);
  }

  private groupByModel(terms: DlExtractionTermDto[]): TermGroup[] {
    const map = new Map<string, DlExtractionTermDto[]>();
    for (const t of terms) {
      const key = t.modelName ?? 'Unbekannt';
      if (!map.has(key)) map.set(key, []);
      map.get(key)!.push(t);
    }
    return Array.from(map.entries()).map(([modelName, ts]) => ({
      modelName,
      terms: ts,
      durationMs: ts[0]?.durationMs,
    }));
  }

  protected confidencePct(confidence: number | undefined): string {
    if (confidence == null) return '';
    return Math.round(confidence * 100) + '%';
  }

  protected confidenceClass(confidence: number | undefined): string {
    if (confidence == null) return '';
    if (confidence >= 0.7) return 'conf-high';
    if (confidence >= 0.4) return 'conf-mid';
    return 'conf-low';
  }
}
