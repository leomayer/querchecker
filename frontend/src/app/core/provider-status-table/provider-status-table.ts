import { Component, inject } from '@angular/core';
import { LowerCasePipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ProviderStatusStore, ProviderState } from '../provider-status.store';

@Component({
  selector: 'app-provider-status-table',
  standalone: true,
  imports: [MatIconModule, MatTooltipModule, LowerCasePipe],
  templateUrl: './provider-status-table.html',
  styleUrl: './provider-status-table.scss',
})
export class ProviderStatusTable {
  protected readonly store = inject(ProviderStatusStore);

  stateIcon(state: ProviderState): string {
    switch (state) {
      case 'VALID': return 'check_circle';
      case 'CONFIGURED': return 'pending';
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

  configuredIcon(state: ProviderState): string {
    return state === 'UNCONFIGURED' ? 'cancel' : 'check_circle';
  }

  configuredClass(state: ProviderState): string {
    return state === 'UNCONFIGURED' ? 'icon-error' : 'icon-ok';
  }

  connectedIcon(state: ProviderState): string {
    switch (state) {
      case 'VALID': return 'check_circle';
      case 'UNCONFIGURED': return 'cancel';
      default: return 'pending';
    }
  }

  connectedClass(state: ProviderState): string {
    switch (state) {
      case 'VALID': return 'icon-ok';
      case 'UNCONFIGURED': return 'icon-error';
      default: return 'icon-unknown';
    }
  }

  configuredTooltip(state: ProviderState): string {
    return state === 'UNCONFIGURED' ? 'API-Key fehlt' : 'API-Key ist hinterlegt';
  }

  connectedTooltip(state: ProviderState): string {
    switch (state) {
      case 'VALID': return 'Verbindung erfolgreich getestet';
      case 'UNCONFIGURED': return 'Verbindung nicht möglich (Key fehlt)';
      case 'CONFIGURED': return 'Verbindung noch nicht getestet';
      case 'UNREACHABLE': return 'Server nicht erreichbar';
      case 'UNAVAILABLE': return 'Authentifizierung fehlgeschlagen';
    }
  }
}
