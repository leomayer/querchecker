import { Component, inject, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { SetupWizard } from './setup-wizard';

/**
 * Route component that opens the setup wizard dialog.
 * Navigates back to search on close.
 */
@Component({
  selector: 'app-setup-wizard-route',
  standalone: true,
  template: '',
})
export class SetupWizardRoute implements OnInit {
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);

  ngOnInit(): void {
    this.dialog
      .open(SetupWizard, { width: '600px' })
      .afterClosed()
      .subscribe(() => {
        this.router.navigate(['']);
      });
  }
}
