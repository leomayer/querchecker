import { CdkDrag, CdkDragHandle } from '@angular/cdk/drag-drop';
import { Component, inject, OnInit, output, input } from '@angular/core';
import { MatIconButton } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogTitle } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { filter, take } from 'rxjs/operators';

@Component({
  selector: 'app-moveable-dialog-header',
  standalone: true,
  imports: [MatIcon, MatIconButton, CdkDrag, CdkDragHandle, MatDialogTitle],
  templateUrl: './moveable-dialog-header.html',
  styleUrl: './moveable-dialog-header.scss',
})
export class MoveableDialogHeaderComponent implements OnInit {
  showCloseButton = input(true);
  readonly dlgClose = output<boolean>();
  readonly interceptCloseDlg = input(false);

  private readonly dialogRef = inject(MatDialogRef<MoveableDialogHeaderComponent>);
  readonly data = inject<{ preventEscape?: boolean } | null>(MAT_DIALOG_DATA, { optional: true });

  ngOnInit(): void {
    this.dialogRef
      .keydownEvents()
      .pipe(
        filter((e: KeyboardEvent) => e.code === 'Escape'),
        take(1),
      )
      .subscribe(() => {
        if (this.data?.preventEscape) {
          return;
        }
        if (this.interceptCloseDlg()) {
          this.dlgClose.emit(true);
        } else {
          this.closeDialog();
        }
      });
  }

  closeDialog(): void {
    this.dialogRef.close();
  }
}
