import { Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ProviderStatusStore } from '../../../core/provider-status.store';
import { ProviderStatusTable } from '../../../core/provider-status-table/provider-status-table';

@Component({
  selector: 'app-provider-config',
  imports: [MatButtonModule, MatIconModule, ProviderStatusTable],
  templateUrl: './provider-config.html',
  styleUrl: './provider-config.scss',
})
export class ProviderConfig {
  protected readonly store = inject(ProviderStatusStore);
  private readonly snackBar = inject(MatSnackBar);

  readonly canSetup = computed(() => {
    const s = this.store.status();
    if (!s) return false;
    return s.searchState !== 'VALID' || s.llmState !== 'VALID';
  });

  openSetupWizard(): void {
    this.snackBar.open(
      'Die secrets.yml muss im Backend-Verzeichnis abgelegt und der Server restartet werden.',
      'OK',
      { duration: 8000, horizontalPosition: 'center', verticalPosition: 'bottom' }
    );
  }
}
