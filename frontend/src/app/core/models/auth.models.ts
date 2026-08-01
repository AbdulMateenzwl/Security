/** Public, safe projection of a user — mirrors the backend UserDto. */
export interface User {
  id: string;
  username: string;
  email: string;
  githubUsername: string | null;
  createdAt: string;
}

/** Minimal user projection returned by search — mirrors the backend UserSummaryDto (no email). */
export interface UserSummary {
  id: string;
  username: string;
}

/** Successful authentication result — mirrors the backend AuthResponse. */
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInMs: number;
  user: User;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  githubUsername?: string | null;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RefreshRequest {
  refreshToken: string;
}
