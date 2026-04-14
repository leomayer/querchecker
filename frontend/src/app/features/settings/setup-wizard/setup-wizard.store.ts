import { Injectable, computed, signal } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { ProviderSetupInitResponse, ProviderSetupSaveRequest } from '../../../api';

@Injectable()
export class SetupWizardStore {
  private readonly initResource = httpResource<ProviderSetupInitResponse>(
    () => ({ url: '/api/provider-setup/init' }),
  );

  /** Gewählter aktiver Provider pro Dimension: { 'SEARCH': 'BRAVE', 'LLM': 'GROQ' } */
  readonly selectedProviders = signal<Record<string, string>>({});
  /** Editierte Feldwerte: { 'BRAVE.api-key': 'gsk_...', 'GROQ.model': 'llama-3.1-8b-instant' } */
  readonly fieldEdits = signal<Record<string, string>>({});
  readonly saving = signal(false);
  readonly saveError = signal<string | null>(null);

  private initialized = false;

  readonly loading = this.initResource.isLoading;
  readonly error = computed(() => this.initResource.error() ? 'Init fehlgeschlagen' : null);
  readonly initData = this.initResource.value;
  readonly dimensions = computed(() => this.initData()?.dimensions ?? []);
  readonly canSaveToServer = computed(() => this.initData()?.canSaveToServer ?? false);
  readonly secretsYmlPath = computed(() => this.initData()?.secretsYmlPath ?? 'config/secrets.yml');
  readonly quercheckerYmlPath = computed(() => this.initData()?.quercheckerYmlPath ?? 'config/querchecker.yml');

  readonly allFieldsComplete = computed(() => {
    const data = this.initData();
    if (!data?.dimensions) return false;
    const selected = this.selectedProviders();
    const edits = this.fieldEdits();
    for (const dim of data.dimensions) {
      const providerName = selected[dim.name!] ?? dim.activeProvider;
      const provider = dim.providers?.find(p => p.name === providerName);
      if (!provider) return false;
      for (const field of provider.fields ?? []) {
        if (field.secret) {
          const editKey = `${providerName}.${field.key}`;
          const val = edits[editKey] ?? field.value;
          if (!val || val.trim() === '') return false;
        }
      }
    }
    return true;
  });

  /** Initialisiert selectedProviders und fieldEdits aus den geladenen Daten. */
  initFromData(data: ProviderSetupInitResponse): void {
    if (this.initialized) return;
    this.initialized = true;

    const selected: Record<string, string> = {};
    const edits: Record<string, string> = {};
    for (const dim of data.dimensions ?? []) {
      if (dim.activeProvider) {
        selected[dim.name!] = dim.activeProvider;
      }
      for (const prov of dim.providers ?? []) {
        for (const field of prov.fields ?? []) {
          if (field.value != null) {
            edits[`${prov.name}.${field.key}`] = field.value;
          }
        }
      }
    }
    this.selectedProviders.set(selected);
    this.fieldEdits.set(edits);
  }

  selectProvider(dimensionName: string, providerName: string): void {
    this.selectedProviders.update(prev => ({ ...prev, [dimensionName]: providerName }));
  }

  updateField(providerName: string, fieldKey: string, value: string): void {
    this.fieldEdits.update(prev => ({ ...prev, [`${providerName}.${fieldKey}`]: value }));
  }

  getFieldValue(providerName: string, fieldKey: string): string {
    return this.fieldEdits()[`${providerName}.${fieldKey}`] ?? '';
  }

  buildSavePayload(): ProviderSetupSaveRequest {
    const data = this.initData();
    const selected = this.selectedProviders();
    const edits = this.fieldEdits();

    const secrets: Record<string, string> = {};
    const config: Record<string, string> = {};

    for (const dim of data?.dimensions ?? []) {
      const selectedProviderName = selected[dim.name!] ?? dim.activeProvider;
      for (const prov of dim.providers ?? []) {
        for (const field of prov.fields ?? []) {
          const editKey = `${prov.name}.${field.key}`;
          const value = edits[editKey] ?? field.value ?? field.placeholder ?? '';
          if (field.secret) {
            // Secrets aller Provider → secrets.yml (Platzhalter für nicht-genutzte Provider)
            secrets[field.placeholder ?? field.key!] = value;
          } else if (prov.name === selectedProviderName) {
            // Config-Felder nur vom selektierten Provider → querchecker.yml
            config[field.key!] = value;
          }
        }
      }
    }

    return {
      secrets,
      config,
      searchProvider: selected['SEARCH'] ?? 'BRAVE',
      llmProvider: selected['LLM'] ?? 'GROQ',
    };
  }
}
