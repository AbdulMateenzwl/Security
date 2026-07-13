import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AuthResponse,
  LoginRequest,
  RegisterRequest,
  User,
} from '../models/auth.models';
import { TokenStorageService } from './token-storage.service';

/**
 * Owns authentication state. Talks to /api/auth and keeps the current user in a signal so the UI
 * reacts to login/logout. Tokens themselves live in TokenStorageService.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly storage = inject(TokenStorageService);
  private readonly baseUrl = `${environment.apiUrl}/auth`;

  private readonly currentUser = signal<User | null>(this.storage.user);

  readonly user = this.currentUser.asReadonly();
  readonly isAuthenticated = computed(() => this.currentUser() !== null);

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.baseUrl}/register`, request)
      .pipe(tap((res) => this.setSession(res)));
  }

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.baseUrl}/login`, request)
      .pipe(tap((res) => this.setSession(res)));
  }

  /**
   * Exchange the stored refresh token for a new access/refresh pair. Used by the auth interceptor to
   * silently recover from an expired access token instead of forcing a re-login.
   */
  refresh(): Observable<AuthResponse> {
    const refreshToken = this.storage.refreshToken;
    if (!refreshToken) {
      return throwError(() => new Error('No refresh token available'));
    }
    return this.http
      .post<AuthResponse>(`${this.baseUrl}/refresh`, { refreshToken })
      .pipe(tap((res) => this.setSession(res)));
  }

  /** Best-effort server-side logout, then always clears local state. */
  logout(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/logout`, {}).pipe(
      tap({
        next: () => this.clearSession(),
        error: () => this.clearSession(),
      }),
    );
  }

  private setSession(res: AuthResponse): void {
    this.storage.save(res);
    this.currentUser.set(res.user);
  }

  clearSession(): void {
    this.storage.clear();
    this.currentUser.set(null);
  }
}
