import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { ProviderStatusStore } from '../provider-status.store';
import { AppRoutePath } from '../app-route-paths';

@Component({
  selector: 'app-provider-status-popup',
  imports: [MatButtonModule, MatIconModule],
  templateUrl: './provider-status-popup.html',
  styleUrl: './provider-status-popup.scss',
})
export class ProviderStatusPopup {
  private readonly router = inject(Router);
  protected readonly store = inject(ProviderStatusStore);

  openSettings(): void {
    this.store.acknowledge();
    // Navigate to home first to avoid conflicts with previous routes, then to setup
    this.router.navigate(['/']).then(() => {
      this.router.navigate(['/', AppRoutePath.SETUP]);
    });
  }

  dismiss(): void {
    this.store.acknowledge();
  }
}
