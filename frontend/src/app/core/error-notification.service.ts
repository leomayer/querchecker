import { Injectable, OnDestroy, inject } from '@angular/core';
import { EventSourceServerService } from '../shared/utils/event-source-server';
import { SnackService } from '../shared/services/snack.service';
import { ErrorNotificationPayload, SseEvent } from './sse-events';

/**
 * Listens to SSE error-notification events and displays snackbars to the user.
 * Automatically registered as a singleton in the app.
 */
@Injectable({ providedIn: 'root' })
export class ErrorNotificationService implements OnDestroy {
  private readonly snackService = inject(SnackService);
  private readonly sseService = inject(EventSourceServerService);

  constructor() {
    this.sseService.addEventListener('error-notification', this.onErrorNotification.bind(this));
  }

  private onErrorNotification(event: SseEvent<ErrorNotificationPayload>): void {
    const { errorType, message, retryAfterSeconds } = event.payload;

    let displayMessage = message;
    if (retryAfterSeconds != null) {
      displayMessage = `${message} — Retry in ~${retryAfterSeconds}s`;
    }

    console.warn(`[ErrorNotificationService] Unbekannter Fehlertyp: ${errorType}`, {
      message,
      retryAfterSeconds,
    });

    const title = this.getErrorTitle(errorType);
    this.snackService.error(displayMessage, title, 0);
  }

  private getErrorTitle(errorType: string): string {
    switch (errorType) {
      case 'RATE_LIMITED':
        return 'Rate-Limit erreicht';
      case 'API_TIMEOUT':
        return 'Anfrage-Timeout';
      case 'EXTRACTION_FAILED':
        return 'Textanalyse fehlgeschlagen';
      case 'LOOKUP_FAILED':
        return 'Produktsuche fehlgeschlagen';
      case 'NETWORK_ERROR':
        return 'Netzwerkfehler';
      default:
        // Fallback: show errorType if unknown, or generic error
        return `Fehler${errorType ? ` (${errorType})` : ''}`;
    }
  }

  ngOnDestroy(): void {
    this.sseService.deleteEventListener('error-notification', this.onErrorNotification.bind(this));
  }
}
