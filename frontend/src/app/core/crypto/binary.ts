/**
 * Encoding helpers shared across the Signal layer.
 *
 * The wire format for every key/ciphertext is Base64 (Jackson maps the backend's `byte[]` to/from
 * Base64). The libsignal library works in `ArrayBuffer`s, and its cipher output/ input is a Latin-1
 * "binary string". These helpers bridge those representations.
 */

/** ArrayBuffer/typed-array → Base64 string. */
export function arrayBufferToBase64(buffer: ArrayBuffer | Uint8Array): string {
  const bytes = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

/** Base64 string → ArrayBuffer. */
export function base64ToArrayBuffer(base64: string): ArrayBuffer {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}

/** Latin-1 "binary string" (libsignal cipher body) → Uint8Array. */
export function binaryStringToBytes(binary: string): Uint8Array {
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i) & 0xff;
  }
  return bytes;
}

/** ArrayBuffer → Latin-1 "binary string" (what libsignal decrypt expects as input). */
export function arrayBufferToBinaryString(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return binary;
}

/** UTF-8 text → ArrayBuffer. */
export function utf8ToArrayBuffer(text: string): ArrayBuffer {
  return new TextEncoder().encode(text).buffer;
}

/** ArrayBuffer → UTF-8 text. */
export function arrayBufferToUtf8(buffer: ArrayBuffer): string {
  return new TextDecoder().decode(buffer);
}

/** Lowercase hex of a public key — matches the server's fingerprint (safety number) format. */
export function arrayBufferToHex(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let hex = '';
  for (let i = 0; i < bytes.length; i++) {
    hex += bytes[i].toString(16).padStart(2, '0');
  }
  return hex;
}
