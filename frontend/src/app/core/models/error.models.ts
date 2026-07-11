/** Uniform error body returned by the backend for every failed request. */
export interface ApiError {
  error: string;
  message: string;
  timestamp: string;
  fieldErrors?: Record<string, string>;
}
