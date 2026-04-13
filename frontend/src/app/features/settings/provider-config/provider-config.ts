import { Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { ProviderStatusStore } from '../../../core/provider-status.store';
import { ProviderStatusTable } from '../../../core/provider-status-table/provider-status-table';
import { AppRoutePath } from '../../../core/app-route-paths';

@Component({
  selector: 'app-provider-config',
  imports: [MatButtonModule, MatIconModule, ProviderStatusTable],
  templateUrl: './provider-config.html',
  styleUrl: './provider-config.scss',
})
export class ProviderConfig {
  protected readonly store = inject(ProviderStatusStore);
  private readonly router = inject(Router);

  readonly canSetup = computed(() => {
    const s = this.store.status();
    if (!s) return false;
    const needsSetup = (state: string) => state === 'UNCONFIGURED' || state === 'UNAVAILABLE';
    return needsSetup(s.searchState) || needsSetup(s.llmState);
  });

  openSetupWizard(): void {
    this.router.navigate(['/', AppRoutePath.SETUP]);
  }
}
