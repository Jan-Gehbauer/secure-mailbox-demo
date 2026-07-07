import { inject } from '@angular/core';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

const AUTH_ENDPOINT_PREFIX = '/api/auth/';

/**
 * Zwei Aufgaben:
 * 1. Hängt bei jedem Request (ausser den Auth-Endpunkten selbst) den
 *    aktuellen Access-Token als "Authorization: Bearer ..."-Header an.
 * 2. Fängt eine 401-Antwort ab (Access-Token abgelaufen), versucht GENAU
 *    EINMAL, über den Refresh-Endpunkt einen neuen Access-Token zu holen,
 *    und wiederholt danach den ursprünglichen Request mit dem neuen
 *    Token. Schlägt auch der Refresh fehl, wird der Fehler normal
 *    weitergereicht (der aufrufende Code/die Komponente entscheidet, was
 *    dann passiert, z.B. zurück zum Login).
 *
 * Funktionale Interceptors (statt Klassen mit HttpInterceptor-Interface)
 * sind seit Angular 15 der empfohlene Weg und lassen sich einfach per
 * DI-Funktion inject() mit Services verdrahten.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);

  const isAuthEndpoint = req.url.includes(AUTH_ENDPOINT_PREFIX);

  const token = authService.getAccessToken();
  const authorizedReq = (!isAuthEndpoint && token)
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authorizedReq).pipe(
    catchError((error: unknown) => {
      const is401 = error instanceof HttpErrorResponse && error.status === 401;

      // Kein Retry-Versuch für die Auth-Endpunkte selbst (sonst Endlosschleife,
      // falls z.B. /login mit falschem Passwort ohnehin 401 zurückgibt).
      if (!is401 || isAuthEndpoint) {
        return throwError(() => error);
      }

      return authService.refreshAccessToken().pipe(
        switchMap((refreshed) => {
          const retriedReq = req.clone({
            setHeaders: { Authorization: `Bearer ${refreshed.accessToken}` }
          });
          return next(retriedReq);
        }),
        catchError((refreshError: unknown) => {
          // Refresh-Token ebenfalls ungültig/abgelaufen -> Session endgültig beenden.
          authService.logout();
          return throwError(() => refreshError);
        })
      );
    })
  );
};
