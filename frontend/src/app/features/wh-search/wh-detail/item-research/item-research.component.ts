import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { Router } from '@angular/router';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatChipSet, MatChip } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { WhDetailDto } from '../../../../api/model/whDetailDto';
import { ExtractionStore } from '../../extraction.store';
import { IcecatData, IcecatFeatureGroup } from '../../../../core/model/icecat.model';
import { SpecsFeatureGroup } from '../../../../core/model/lookup.model';
import { LookupHistoryEntry } from '../../../../core/sse-events';
import { IcecatAccordionComponent } from './icecat-accordion/icecat-accordion.component';
import { SpecsAccordionComponent } from './specs-accordion/specs-accordion.component';
import { PreferenceEntry, PreferencesService } from '../../../../core/preferences.service';
import { HealthService } from '../../../../core/health.service';
import { ProviderStatusStore, ProviderState } from '../../../../core/provider-status.store';

type LookupState =
  | 'empty'
  | 'loading'
  | 'COMPLETE'
  | 'FAILED'
  | 'QUOTA_EXCEEDED'
  | 'NO_SOURCES'
  | 'ERROR'
  | 'RATE_LIMITED';

@Component({
  selector: 'app-item-research',
  imports: [
    DatePipe,
    FormsModule,
    MatButton,
    MatIconButton,
    MatChipSet,
    MatChip,
    MatFormFieldModule,
    MatIconModule,
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
  private readonly health = inject(HealthService);
  private readonly providerStatusStore = inject(ProviderStatusStore);
  private readonly router = inject(Router);

  protected readonly searchTerm = signal('');

  constructor() {
    this.loadPreferences();

    let lastLoadedWhItemId: number | null = null;
    effect(() => {
      const id = this.detail().whItemId;
      const listingId = this.detail().id;
      if (id == null || id === lastLoadedWhItemId) return;
      lastLoadedWhItemId = id;
      this.extractionStore.loadExistingTerms(id);
      if (listingId != null) this.extractionStore.loadHistory(id, listingId);
    });

    // Re-fetch terms after a server restart — the dl-extract SSE event may have been
    // broadcast during the reconnection window and missed by the frontend.
    // Capture the count at mount time so only restarts that happen AFTER this component
    // is created trigger a re-fetch. untracked() keeps detail() out of the dependency set.
    const restartCountAtMount = this.health.serverRestartCount();
    effect(() => {
      if (this.health.serverRestartCount() <= restartCountAtMount) return;
      const id = untracked(() => this.detail().whItemId);
      if (id != null) {
        this.extractionStore.loadExistingTerms(id);
      }
    });

    // Pre-fill from store suggestion, but only when the field is still empty.
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
      if (
        !this.extractionStore.lookupResults()[id] &&
        !this.extractionStore.lookupLoadingIds().includes(id)
      ) {
        this.extractionStore.lookup(id, listingId, suggested);
      }
    });
  }

  // --- Provider status ---

  /**
   * True when AI search is usable.
   * False only for UNCONFIGURED and UNAVAILABLE — these require a config change + restart.
   * UNREACHABLE keeps the button active so the user can retry.
   * Defaults to true while the status event hasn't arrived yet.
   */
  protected readonly aiSearchEnabled = computed<boolean>(() => {
    const s = this.providerStatusStore.status();
    if (!s) return true;
    const active = (state: ProviderState) => state !== 'UNCONFIGURED' && state !== 'UNAVAILABLE';
    return active(s.searchState) && active(s.llmState);
  });

  /** Message to display in the placeholder when AI search is disabled. */
  protected readonly providerDisabledMessage = computed<string | null>(() => {
    const s = this.providerStatusStore.status();
    if (!s) return null;
    const isDown = (state: ProviderState) => state === 'UNCONFIGURED' || state === 'UNAVAILABLE';
    const searchDown = isDown(s.searchState);
    const llmDown = isDown(s.llmState);
    if (!searchDown && !llmDown) return null;
    if (searchDown && llmDown)
      return 'Produktsuche nicht verfügbar — Web-Suche und KI-Provider nicht konfiguriert';
    if (searchDown) return 'Produktsuche nicht verfügbar — Web-Suche nicht konfiguriert';
    return 'Produktsuche nicht verfügbar — KI-Provider nicht konfiguriert';
  });

  /** HTTP status shown additionally when a provider is UNAVAILABLE (invalid key). */
  protected readonly providerUnavailableStatus = computed<number | null>(() => {
    const s = this.providerStatusStore.status();
    if (!s) return null;
    if (s.searchState === 'UNAVAILABLE') return s.searchHttpStatus;
    if (s.llmState === 'UNAVAILABLE') return s.llmHttpStatus;
    return null;
  });

  /** Inline warning shown below the search row when a provider is UNREACHABLE but still usable. */
  protected readonly providerWarningMessage = computed<string | null>(() => {
    const s = this.providerStatusStore.status();
    if (!s) return null;
    if (s.searchState === 'UNREACHABLE') {
      const name = s.searchProvider || 'Web-Suche';
      return s.searchError
        ? `${name}: ${s.searchError}`
        : `${name} nicht erreichbar — Suche trotzdem möglich`;
    }
    if (s.llmState === 'UNREACHABLE') {
      const name = s.llmProvider || 'KI-Provider';
      return s.llmError
        ? `${name}: ${s.llmError}`
        : `${name} nicht erreichbar — Suche trotzdem möglich`;
    }
    return null;
  });

  protected navigateToSettings(): void {
    void this.router.navigate(['/settings']);
  }

  // --- Extraction state ---

  protected readonly extractionLoading = computed<boolean>(() => {
    const id = this.detail().whItemId;
    if (id == null) return false;
    const status = this.extractionStore.extractionStatus()[id];
    return status == null || status === 'PENDING';
  });

  protected readonly extractionFailed = computed<boolean>(() => {
    const id = this.detail().whItemId;
    if (id == null) return false;
    return this.extractionStore.extractionStatus()[id] === 'FAILED';
  });

  /** Merged condensedSpec from all extracted terms for the current item. */
  protected readonly extractedCondensedSpec = computed<[string, string][]>(() => {
    const id = this.detail().whItemId;
    if (id == null) return [];
    const terms = this.extractionStore.results()[id] ?? [];
    const merged: Record<string, string> = {};
    for (const term of terms) {
      if (term.condensedSpec) Object.assign(merged, term.condensedSpec);
    }
    return Object.entries(merged);
  });

  /** Extraction finished but the LLM couldn't identify a product name. */
  protected readonly extractionDoneNoTerm = computed<boolean>(() => {
    const id = this.detail().whItemId;
    if (id == null) return false;
    const status = this.extractionStore.extractionStatus()[id];
    if (status !== 'DONE') return false;
    return !this.extractionStore.suggestedTerms()[id] && !this.searchTerm().trim();
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
      .filter((k) => k in facts && facts[k] != null && facts[k] !== '' && k !== 'Erscheinungsjahr')
      .map((k) => [k, facts[k]] as [string, string]);
    const rest = Object.entries(facts)
      .filter(
        ([k, v]) => !prefKeys.includes(k) && v != null && v !== '' && k !== 'Erscheinungsjahr',
      )
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
    const id = this.detail().whItemId;
    if (id == null) return null;
    const result = this.extractionStore.lookupResults()[id];
    return result?.siteLabel ?? result?.sourceDomain ?? null;
  });

  protected readonly lookupSourceUrl = computed<string | null>(() => {
    const id = this.detail().whItemId;
    if (id == null) return null;
    return this.extractionStore.lookupResults()[id]?.sourceUrl ?? null;
  });

  /** sourceUrl with fallback to Icecat search URL when Brave returned no product page. */
  protected readonly effectiveSourceUrl = computed<string | null>(() => {
    const url = this.lookupSourceUrl();
    if (url) return url;
    if (this.lookupSourceDomain()?.includes('icecat')) return this.icecatPageUrl();
    return null;
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

  protected readonly googleSearchUrl = computed<string | null>(() => {
    const term = this.lookupTerm() || this.searchTerm().trim();
    if (!term) return null;
    return 'https://www.google.com/search?q=' + encodeURIComponent(term);
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

  protected readonly icecatPageUrl = computed<string | null>(() => {
    const id = this.detail().whItemId;
    if (id == null) return null;
    const catalogUrl = this.extractionStore.icecatCatalogUrls()[id];
    if (catalogUrl) return catalogUrl;
    const term = this.lookupTerm() || this.searchTerm().trim();
    const icecatId = this.lookupIcecatId();
    if (!term && !icecatId) return null;
    const q = icecatId ? `${term} ${icecatId}`.trim() : term;
    return 'https://icecat.biz/de/search/?q=' + encodeURIComponent(q);
  });

  protected readonly lookupTimestamp = computed<Date | null>(() => {
    const id = this.detail().whItemId;
    if (id == null) return null;
    const ts = this.extractionStore.lookupTimestamps()[id];
    return ts ? new Date(ts) : null;
  });

  protected readonly fullSpecsTimestamp = computed<Date | null>(() => {
    const id = this.detail().whItemId;
    if (id == null) return null;
    const ts = this.extractionStore.fullSpecsTimestamps()[id];
    return ts ? new Date(ts) : null;
  });

  protected readonly retryAfter = computed<Date | null>(() => {
    const id = this.detail().whItemId;
    if (id == null) return null;
    const ts = this.extractionStore.lookupResults()[id]?.retryAfter;
    return ts ? new Date(ts) : null;
  });

  protected readonly retryAfterSeconds = computed<number | null>(() => {
    const id = this.detail().whItemId;
    if (id == null) return null;
    return this.extractionStore.lookupResults()[id]?.retryAfterSeconds ?? null;
  });

  protected readonly retryProvider = computed<string | null>(() => {
    const id = this.detail().whItemId;
    if (id == null) return null;
    return this.extractionStore.lookupResults()[id]?.retryProvider ?? null;
  });

  protected readonly retryModel = computed<string | null>(() => {
    const id = this.detail().whItemId;
    if (id == null) return null;
    return this.extractionStore.lookupResults()[id]?.retryModel ?? null;
  });

  protected readonly searchButtonDisabled = computed<boolean>(
    () =>
      !this.aiSearchEnabled() ||
      !this.searchTerm().trim() ||
      this.lookupState() === 'loading' ||
      this.lookupState() === 'NO_SOURCES',
  );

  protected readonly retryAfterDisplay = computed<string>(() => {
    const after = this.retryAfter();
    if (!after) return '';
    const now = new Date();
    const diffMs = after.getTime() - now.getTime();
    if (diffMs <= 0) return '';
    const diffSecs = Math.ceil(diffMs / 1000);
    if (diffSecs < 60) return `~${diffSecs}s`;
    const diffMins = Math.ceil(diffSecs / 60);
    return `~${diffMins} min`;
  });

  protected readonly quickFactsColumns = computed<2 | 3>(() => {
    const facts = this.orderedQuickFacts();
    const entries = facts.length > 0 ? facts : this.extractedCondensedSpec();
    return entries.some(([k, v]) => k.length > 15 || v.length > 25) ? 2 : 3;
  });

  protected readonly retryButtonDisabled = computed<boolean>(() => {
    const state = this.lookupState();
    if (state === 'RATE_LIMITED') {
      return (this.retryAfterSeconds() ?? 0) > 0;
    }
    if (state === 'ERROR') {
      return (this.retryAfter()?.getTime() ?? 0) > new Date().getTime();
    }
    return true;
  });

  // --- Preferences ---

  private readonly prefService = inject(PreferencesService);
  private readonly preferences = signal<PreferenceEntry[]>([]);

  private loadPreferences(): void {
    this.prefService.getAll().subscribe((list) => this.preferences.set(list));
  }

  protected readonly activeCategoryId = computed<number | null>(() => {
    const path = this.detail().categoryPath ?? [];
    return path.length > 0 ? (path[path.length - 1].id ?? null) : null;
  });

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
    const alreadyPreferred = currentKeys.some((k) => key.includes(k));
    const newKeys = alreadyPreferred
      ? currentKeys.filter((k) => !key.includes(k))
      : [...currentKeys, key];

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
      error: () => {
        this.prefService.invalidate();
        this.loadPreferences();
      },
    });
  }

  // --- History ---

  protected readonly lookupHistory = computed<LookupHistoryEntry[]>(() => {
    const id = this.detail().whItemId;
    if (id == null) return [];
    return this.extractionStore.lookupHistory()[id] ?? [];
  });

  protected onHistoryChipClick(entry: LookupHistoryEntry): void {
    const whItemId = this.detail().whItemId;
    if (whItemId == null) return;
    this.searchTerm.set(entry.lookupTerm);
    // Restore cached result directly — no new API call
    this.extractionStore.restoreLookupResult(whItemId, entry);
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
}
