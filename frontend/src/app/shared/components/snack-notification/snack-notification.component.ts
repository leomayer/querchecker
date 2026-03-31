import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MAT_SNACK_BAR_DATA, MatSnackBarRef } from '@angular/material/snack-bar';

export interface SnackNotificationData {
  message: string;
  type: 'error' | 'success' | 'info';
  title?: string;
}

@Component({
  selector: 'app-snack-notification',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule],
  templateUrl: './snack-notification.component.html',
  styleUrls: ['./snack-notification.component.scss'],
})
export class SnackNotificationComponent {
  constructor(
    private snackBarRef: MatSnackBarRef<SnackNotificationComponent>,
    @Inject(MAT_SNACK_BAR_DATA) public data: SnackNotificationData,
  ) {}

  close(): void {
    this.snackBarRef.dismiss();
  }
}
