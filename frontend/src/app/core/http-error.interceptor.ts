import { HttpErrorResponse, HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

/**
 * Detects 5xx server errors (likely mid-restart) and reloads the page to reconnect fresh.
 * This ensures the browser gets a new SSE connection with the restarted server.
 */
@Injectable()
export class ServerErrorInterceptor implements HttpInterceptor {
  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    return next.handle(req).pipe(
      catchError((error: HttpErrorResponse) => {
        if (error.status >= 500) {
          // Server error — likely mid-restart. Reload to pick up the new server.
          console.warn('Server error detected — reloading page', error.status, error.statusText);
          setTimeout(() => window.location.reload(), 500);
        }
        return throwError(() => error);
      })
    );
  }
}
