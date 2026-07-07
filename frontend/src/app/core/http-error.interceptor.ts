import {
  HttpErrorResponse,
  HttpEvent,
  HttpHandler,
  HttpInterceptor,
  HttpRequest,
} from '@angular/common/http';
import { Injectable, Injector, inject } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { HealthService } from './health.service';
import { API_URLS } from './api-urls';
import { SnackService } from '../shared/services/snack.service';
import { AuthService } from './auth.service';

/**
 * Detects server errors and network failures, then notifies the HealthService
 * which shows a reconnection banner instead of blindly reloading the page.
 *
 * HealthService is injected lazily via Injector to break the circular dependency:
 * HealthService → HttpClient → HTTP_INTERCEPTORS → ServerErrorInterceptor → HealthService
 */
@Injectable()
export class ServerErrorInterceptor implements HttpInterceptor {
  private readonly injector = inject(Injector);

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    return next.handle(req).pipe(
      catchError((error: HttpErrorResponse) => {
        // Don't recurse on health-check requests
        if (req.url === API_URLS.health) {
          return throwError(() => error);
        }
        if (error.status >= 500 || error.status === 0) {
          this.injector.get(HealthService).notifyServerError();
          if (error.status === 0) {
            this.injector.get(SnackService).error('Verbindung unterbrochen', 'Netzwerkfehler');
          } else {
            this.injector
              .get(SnackService)
              .error('Bitte später erneut versuchen', `Serverfehler (${error.status})`);
          }
        }
        // Session invalidiert/gesperrt (z.B. Key mid-session revoked) — Auth-State neu laden,
        // damit UI sofort auf Gast kippt statt eine stale "eingeloggt"-Anzeige + generischen
        // Fehler zu zeigen. /api/auth/** selbst ausgenommen (login-with-key/me/logout haben
        // eigene Fehlerbehandlung, kein Refresh-Rekursionsrisiko, aber unnötig).
        if (
          (error.status === 401 || error.status === 403) &&
          !req.url.startsWith('/api/auth/')
        ) {
          this.injector.get(AuthService).refresh();
        }
        return throwError(() => error);
      }),
    );
  }
}
