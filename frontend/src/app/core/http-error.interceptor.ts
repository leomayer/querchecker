import { HttpErrorResponse, HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { HealthService } from './health.service';
import { API_URLS } from './api-urls';

/**
 * Detects server errors and network failures, then notifies the HealthService
 * which shows a reconnection banner instead of blindly reloading the page.
 */
@Injectable()
export class ServerErrorInterceptor implements HttpInterceptor {
  private readonly health = inject(HealthService);

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    return next.handle(req).pipe(
      catchError((error: HttpErrorResponse) => {
        // Don't recurse on health-check requests
        if (req.url === API_URLS.health) {
          return throwError(() => error);
        }
        if (error.status >= 500 || error.status === 0) {
          this.health.notifyServerError();
        }
        return throwError(() => error);
      }),
    );
  }
}
