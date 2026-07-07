import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Observable, map } from 'rxjs';
import { ConfirmDialog, ConfirmDialogData } from '../components/confirm-dialog/confirm-dialog';

@Injectable({ providedIn: 'root' })
export class ConfirmDialogService {
  private readonly dialog = inject(MatDialog);

  /** Emits true if confirmed, false if cancelled/dismissed. */
  confirm(data: ConfirmDialogData): Observable<boolean> {
    return this.dialog
      .open(ConfirmDialog, { width: '420px', data })
      .afterClosed()
      .pipe(map((result) => result === true));
  }
}
