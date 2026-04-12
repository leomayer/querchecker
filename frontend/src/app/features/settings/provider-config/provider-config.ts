import { Component, computed, inject } from '@angular/core';
import { LowerCasePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { ProviderStatusStore, ProviderState } from '../../../core/provider-status.store';

@Component({
  selector: 'app-provider-config',
  imports: [MatButtonModule, MatIconModule, LowerCasePipe],
  templateUrl: './provider-config.html',
  styleUrl: './provider-config.scss',
})
export class ProviderConfig {
  protected readonly store = inject(ProviderStatusStore);

  readonly searchInfo = computed(() => {
    const s = this.store.status();
    if (!s) return null;
    return { state: s.searchState, provider: s.searchProvider, error: s.searchError };
  });

  readonly llmInfo = computed(() => {
    const s = this.store.status();
    if (!s) return null;
    return { state: s.llmState, provider: s.llmProvider, error: s.llmError };
  });

  readonly canSetup = computed(() => {
    const s = this.store.status();
    if (!s) return false;
    return s.searchState !== 'VALID' || s.llmState !== 'VALID';
  });

  stateIcon(state: ProviderState): string {
    switch (state) {
      case 'VALID': return 'check_circle';
      case 'CONFIGURED': return 'radio_button_unchecked';
      case 'UNCONFIGURED': return 'cancel';
      case 'UNREACHABLE': return 'signal_wifi_off';
      case 'UNAVAILABLE': return 'lock';
    }
  }

  stateLabel(state: ProviderState): string {
    switch (state) {
      case 'VALID': return 'Verbunden';
      case 'CONFIGURED': return 'Konfiguriert (nicht validiert)';
      case 'UNCONFIGURED': return 'Nicht konfiguriert';
      case 'UNREACHABLE': return 'Nicht erreichbar';
      case 'UNAVAILABLE': return 'Nicht verfügbar (Auth-Fehler)';
    }
  }

  stateClass(state: ProviderState): string {
    switch (state) {
      case 'VALID': return 'state-valid';
      case 'CONFIGURED': return 'state-configured';
      case 'UNCONFIGURED': return 'state-unconfigured';
      case 'UNREACHABLE': return 'state-error';
      case 'UNAVAILABLE': return 'state-error';
    }
  }
}
