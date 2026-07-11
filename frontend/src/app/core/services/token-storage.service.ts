import { Injectable } from '@angular/core';
import { AuthResponse, User } from '../models/auth.models';

const ACCESS_TOKEN_KEY = 'sc.accessToken';
const REFRESH_TOKEN_KEY = 'sc.refreshToken';
const USER_KEY = 'sc.user';

/**
 * Persists auth tokens and the current user in localStorage so a page reload keeps the session.
 * This is the single source of truth for stored credentials.
 */
@Injectable({ providedIn: 'root' })
export class TokenStorageService {
  get accessToken(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  }

  get refreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  }

  get user(): User | null {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? (JSON.parse(raw) as User) : null;
  }

  save(auth: AuthResponse): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, auth.accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, auth.refreshToken);
    localStorage.setItem(USER_KEY, JSON.stringify(auth.user));
  }

  updateTokens(accessToken: string, refreshToken: string): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  }

  clear(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }
}
