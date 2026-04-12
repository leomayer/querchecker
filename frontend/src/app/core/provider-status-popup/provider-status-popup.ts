import { Component, inject } from '@angular/core';
import { LowerCasePipe } from '@angular/common';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { ProviderStatusStore } from '../provider-status.store';
import { AppRoutePath } from '../app-route-paths';

@Component({
  selector: 'app-provider-status-popup',
  imports: [MatButtonModule, MatIconModule, LowerCasePipe],
  templateUrl: './provider-status-popup.html',
  styleUrl: './provider-status-popup.scss',
})
export class ProviderStatusPopup {
  private readonly router = inject(Router);
  protected readonly store = inject(ProviderStatusStore);

  setup(): void {
    this.store.acknowledge();
    this.router.navigate(['/', AppRoutePath.SETTINGS]);
  }

  dismiss(): void {
    this.store.acknowledge();
  }
}
