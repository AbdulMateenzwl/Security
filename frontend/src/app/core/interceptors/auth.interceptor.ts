import { HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, shareReplay, switchMap, throwError } from 'rxjs';
import { AuthResponse } from '../models/auth.models';
import { AuthService } from '../services/auth.service';
import { TokenStorageService } from '../services/token-storage.service';

/** Auth endpoints that must not carry a bearer token or trigger a refresh-retry loop. */
const isAuthEndpoint = (url: string) =>
  url.includes('/auth/login') || url.includes('/auth/register') || url.includes('/auth/refresh');

/**
 * A single in-flight refresh shared across all requests that 401 at once, so a burst of concurrent
 * calls triggers exactly one `/auth/refresh` and then all retry with the new token. Module-level so
 * it is shared across interceptor invocations; reset when the refresh settles.
 */
let refresh$: Observable<AuthResponse> | null = null;

/**
 * Attaches the Bearer access token to outgoing API calls. On a 401, it silently refreshes the access
 * token (using the stored refresh token) and retries the original request once; only if the refresh
 * itself fails does it clear the session and bounce to the login page.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const storage = inject(TokenStorageService);
  const auth = inject(AuthService);
  const router = inject(Router);

  const withAuth = (r: HttpRequest<unknown>) => {
    const token = storage.accessToken;
    return token && !isAuthEndpoint(r.url)
      ? r.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : r;
  };

  const bounceToLogin = () => {
    auth.clearSession();
    router.navigate(['/login']);
  };

  return next(withAuth(req)).pipe(
    catchError((err) => {
      const canRefresh =
        err.status === 401 && !isAuthEndpoint(req.url) && !!storage.refreshToken;
      if (!canRefresh) {
        if (err.status === 401 && !isAuthEndpoint(req.url)) {
          bounceToLogin();
        }
        return throwError(() => err);
      }
      // Kick off (or join) the shared refresh, then retry the original request with the new token.
      refresh$ ??= auth.refresh().pipe(
        shareReplay(1),
        finalize(() => (refresh$ = null)),
      );
      return refresh$.pipe(
        switchMap(() => next(withAuth(req))),
        catchError((refreshErr) => {
          bounceToLogin();
          return throwError(() => refreshErr);
        }),
      );
    }),
  );
};
