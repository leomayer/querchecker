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
        }
        return throwError(() => error);
      }),
    );
  }
}
