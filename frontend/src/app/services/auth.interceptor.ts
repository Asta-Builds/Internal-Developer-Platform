import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { KeycloakService } from './keycloak.service';

/** Backend origin that should receive the platform bearer token. */
const API_ORIGIN = 'http://localhost:8088';

/**
 * Attaches the Keycloak access token to backend API calls.
 *
 * <p>Scoped deliberately: the token must not be sent to Keycloak's own token
 * endpoint (which authenticates with the client id and a code verifier) nor to any
 * third-party origin.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const keycloak = inject(KeycloakService);

  const isBackendCall = req.url.startsWith(API_ORIGIN) || req.url.startsWith('/api/');
  const token = keycloak.currentUserSignal().token;

  const outbound = isBackendCall && token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(outbound).pipe(
    catchError((error: HttpErrorResponse) => {
      if (isBackendCall && error.status === 401) {
        // The token was rejected: expired, or it maps to no active internal user.
        keycloak.logout();
      }
      return throwError(() => error);
    })
  );
};
