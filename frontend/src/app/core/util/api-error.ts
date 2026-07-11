import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from '../models/error.models';

/** Pulls a user-friendly message out of a backend ErrorResponse, with sane fallbacks. */
export function extractErrorMessage(err: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) {
      return 'Cannot reach the server. Is the backend running?';
    }
    const body = err.error as ApiError | undefined;
    if (body?.fieldErrors) {
      const first = Object.values(body.fieldErrors)[0];
      if (first) return first;
    }
    if (body?.message) {
      return body.message;
    }
  }
  return fallback;
}
