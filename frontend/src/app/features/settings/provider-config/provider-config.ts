import { Component, computed, inject, output, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ProviderStatusStore } from '../../../core/provider-status.store';
import { ProviderStatusPayload } from '../../../core/sse-events';
import { ProviderStatusTable } from '../../../core/provider-status-table/provider-status-table';
import { AppRoutePath } from '../../../core/app-route-paths';
import { SnackService } from '../../../shared/services/snack.service';
import { finalize } from 'rxjs';

@Component({
  selector: 'app-provider-config',
  imports: [MatButtonModule, MatIconModule, MatProgressSpinnerModule, ProviderStatusTable],
  templateUrl: './provider-config.html',
  styleUrl: './provider-config.scss',
})
export class ProviderConfig {
  protected readonly store = inject(ProviderStatusStore);
  private readonly router = inject(Router);
  private readonly http = inject(HttpClient);
  private readonly snack = inject(SnackService);

  readonly canSetup = computed(() => {
    const s = this.store.status();
    if (!s) return false;
    const needsSetup = (state: string) => state === 'UNCONFIGURED' || state === 'UNAVAILABLE';
    return needsSetup(s.searchState) || needsSetup(s.llmState);
  });

  readonly isLocalUnavailable = computed(() => {
    const s = this.store.status();
    return s?.llmProvider === 'LOCAL' && s?.llmState === 'UNAVAILABLE';
  });

  readonly canTestSearch = computed(() => {
    const s = this.store.status();
    if (!s) return false;
    return s.searchState === 'CONFIGURED' || s.searchState === 'UNREACHABLE' || s.searchState === 'UNAVAILABLE';
  });

  readonly canTestLlm = computed(() => {
    const s = this.store.status();
    if (!s) return false;
    if (s.llmProvider === 'LOCAL') return false;
    return s.llmState === 'CONFIGURED' || s.llmState === 'UNREACHABLE' || s.llmState === 'UNAVAILABLE';
  });

  readonly testingSearch = signal(false);
  readonly testingLlm = signal(false);
  readonly testCompleted = output<void>();

  openSetupWizard(): void {
    this.router.navigate(['/', AppRoutePath.SETUP]);
  }

  testProvider(dimension: 'search' | 'llm'): void {
    const s = this.store.status();
    if (!s) return;
    const provider = dimension === 'search' ? s.searchProvider : s.llmProvider;
    if (!provider) return;

    const testing = dimension === 'search' ? this.testingSearch : this.testingLlm;
    const label = dimension === 'search' ? 'Web Search' : 'KI-Provider';

    testing.set(true);
    this.http
      .post<ProviderStatusPayload>('/api/provider-status/test', null, { params: { provider } })
      .pipe(finalize(() => testing.set(false)))
      .subscribe({
        next: (status) => {
          const state = dimension === 'search' ? status.searchState : status.llmState;
          if (state === 'VALID') {
            this.snack.success('Verbindung erfolgreich', label);
          } else if (state === 'UNREACHABLE') {
            this.snack.error('Server nicht erreichbar', label);
          } else if (state === 'UNAVAILABLE') {
            this.snack.error('API-Key ungültig', label);
          }
          this.testCompleted.emit();
        },
        error: () => {
          this.snack.error('Test fehlgeschlagen', label);
        },
      });
  }
}
