import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ProviderStatusStore } from '../provider-status.store';
import { ProviderStatusTable } from '../provider-status-table/provider-status-table';
import { AppRoutePath } from '../app-route-paths';

@Component({
  selector: 'app-provider-status-popup',
  imports: [MatButtonModule, MatIconModule, ProviderStatusTable],
  templateUrl: './provider-status-popup.html',
  styleUrl: './provider-status-popup.scss',
})
export class ProviderStatusPopup {
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  protected readonly store = inject(ProviderStatusStore);

  setup(): void {
    this.store.acknowledge();
    this.snackBar.open(
      'Die secrets.yml muss im Backend-Verzeichnis abgelegt und der Server restartet werden.',
      'OK',
      { duration: 8000, horizontalPosition: 'center', verticalPosition: 'bottom' }
    );
    this.router.navigate(['/', AppRoutePath.SETTINGS]);
  }

  dismiss(): void {
    this.store.acknowledge();
  }
}
