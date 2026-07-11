import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { TokenStorageService } from '../services/token-storage.service';

/**
 * Attaches the Bearer access token to outgoing API calls and, on a 401, clears the session and
 * bounces to the login page. (Silent refresh can be layered on later.)
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const storage = inject(TokenStorageService);
  const router = inject(Router);
  const token = storage.accessToken;

  const authReq =
    token && !req.url.includes('/auth/login') && !req.url.includes('/auth/register')
      ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : req;

  return next(authReq).pipe(
    catchError((err) => {
      if (err.status === 401 && !req.url.includes('/auth/')) {
        storage.clear();
        router.navigate(['/login']);
      }
      return throwError(() => err);
    }),
  );
};
