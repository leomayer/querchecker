import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { DecimalPipe, SlicePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatIconButton } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatExpansionModule } from '@angular/material/expansion';
import { DlExtractionTermDto } from '../../../../api/model/dlExtractionTermDto';
import { WhDetailDto } from '../../../../api/model/whDetailDto';
import { ExtractionStore } from '../../extraction.store';
import { EanProduct } from '../../../../core/model/ean-search.model';
import { IcecatData, IcecatFeatureGroup } from '../../../../core/model/icecat.model';
import { UpcSearchComponent } from './upc-search/upc-search.component';
import { IcecatSpecComponent } from './icecat-spec/icecat-spec.component';
import { API_URLS } from '../../../../core/api-urls';
import { PreferenceEntry } from '../../../../core/preferences.service';

interface TermGroup {
  modelName: string;
  terms: DlExtractionTermDto[];
  durationMs?: number;
}

type LookupState = 'empty' | 'loading' | 'COMPLETE' | 'FAILED' | 'QUOTA_EXCEEDED';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
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
    UpcSearchComponent,
    IcecatSpecComponent,
    MatExpansionModule,
  ],
  templateUrl: './item-research.component.html',
  styleUrl: './item-research.component.scss',
})
export class ItemResearchComponent {
  readonly detail = input.required<WhDetailDto>();

  private readonly extractionStore = inject(ExtractionStore);

  protected readonly searchTerm = signal('');
  protected readonly activeQuery = signal('');
  protected readonly selectedEan = signal<string | null>(null);

  constructor() {
    effect(() => {
      const id = this.detail().whItemId;
      if (id != null) {
        this.extractionStore.loadExistingTerms(id);
      }
      // Reset product search when item changes
      this.activeQuery.set('');
      this.selectedEan.set(null);
    });

    // Pre-fill from store suggestion, but only when the field is still empty
    // — preserves any text the user has already typed.
    effect(() => {
      const id = this.detail().whItemId;
      if (id == null) return;
      const suggested = this.extractionStore.suggestedTerms()[id];
      if (suggested && !this.searchTerm()) {
        this.searchTerm.set(suggested);
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

  protected readonly lookupQuickFacts = computed<[string, string][]>(() => {
    const id = this.detail().whItemId;
    if (id == null) return [];
    const result = this.extractionStore.lookupResults()[id];
    if (!result?.quickFacts) return [];
    return Object.entries(result.quickFacts) as [string, string][];
  });

  protected readonly lookupIcecatId = computed<string | null>(() => {
    const id = this.detail().whItemId;
    if (id == null) return null;
    return this.extractionStore.lookupResults()[id]?.icecatId ?? null;
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

  protected readonly noIcecatData = computed<boolean>(() => {
    const id = this.detail().whItemId;
    if (id == null) return false;
    const result = this.extractionStore.lookupResults()[id];
    if (!result || result.lookupStatus !== 'COMPLETE') return false;
    const hasQuickFacts = Object.keys(result.quickFacts ?? {}).length > 0;
    return !hasQuickFacts && !result.icecatId;
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

  private readonly preferencesResource = httpResource<PreferenceEntry[]>(
    () => ({ url: API_URLS.settingsPreferences }),
    { defaultValue: [] },
  );

  protected readonly preferredKeySet = computed<Set<string>>(() => {
    const categoryPath = this.detail().categoryPath ?? [];
    const prefs = this.preferencesResource.value();
    // Walk from most specific to least specific category
    for (let i = categoryPath.length - 1; i >= 0; i--) {
      const catId = categoryPath[i].id;
      const entry = prefs.find((p) => p.categoryId === catId);
      if (entry && entry.fieldKeys.length > 0) {
        return new Set(entry.fieldKeys.map((k) => k.toLowerCase()));
      }
    }
    return new Set<string>();
  });

  protected isPreferred(featureName: string): boolean {
    const keys = this.preferredKeySet();
    if (keys.size === 0) return false;
    const lower = featureName.toLowerCase();
    return [...keys].some((k) => lower.includes(k));
  }

  // --- Handlers ---

  protected onSearch(): void {
    const term = this.searchTerm().trim();
    if (!term) return;
    this.selectedEan.set(null);
    this.activeQuery.set(term);
  }

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

  protected onProductSelected(product: EanProduct): void {
    this.selectedEan.set(product.ean || null);
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
