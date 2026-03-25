import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

/**
 * Leitet den Browser-UA und die Accept-Language als Custom-Header weiter,
 * damit der Backend-Server diese für ausgehende Willhaben- und Scraping-Requests verwenden kann.
 */
@Injectable()
export class UaForwardingInterceptor implements HttpInterceptor {
  intercept(req: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    const cloned = req.clone({
      setHeaders: {
        'X-Querchecker-User-Agent': navigator.userAgent,
        'X-Querchecker-Accept-Language': navigator.language,
      },
    });
    return next.handle(cloned);
  }
}
