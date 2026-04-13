import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import {
  SnackNotificationComponent,
  SnackNotificationData,
} from '../components/snack-notification/snack-notification';

@Injectable({ providedIn: 'root' })
export class SnackService {
  private readonly snackBar = inject(MatSnackBar);

  /**
   * Show an error notification
   */
  error(message: string, title?: string, duration?: number): void {
    this.show({ message, type: 'error', title }, duration ?? 0);
  }

  /**
   * Show a success notification
   */
  success(message: string, title?: string, duration?: number): void {
    this.show({ message, type: 'success', title }, duration ?? 3000);
  }

  /**
   * Show an info notification
   */
  info(message: string, title?: string, duration?: number): void {
    this.show({ message, type: 'info', title }, duration ?? 3000);
  }

  /**
   * Show a notification with custom configuration
   */
  private show(data: SnackNotificationData, duration: number): void {
    this.snackBar.openFromComponent(SnackNotificationComponent, {
      data,
      duration,
      horizontalPosition: 'center',
      verticalPosition: 'bottom',
      panelClass: ['snack-notification-panel'],
    });
  }
}
