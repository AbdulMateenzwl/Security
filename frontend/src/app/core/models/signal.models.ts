/**
 * Wire models for the /api/signal endpoints. Every `byte[]` on the backend is Base64 on the wire
 * (Jackson), so these are plain strings here.
 */

export interface IdentityKeyUploadRequest {
  /** Base64 public identity key. */
  publicKey: string;
  registrationId: number;
}

export interface PreKeyDto {
  keyId: number;
  /** Base64 public key. */
  publicKey: string;
}

export interface SignedPreKeyDto {
  keyId: number;
  /** Base64 public key. */
  publicKey: string;
  /** Base64 signature over the public key by the identity key. */
  signature: string;
}

export interface PreKeyUploadRequest {
  preKeys: PreKeyDto[];
  /** New signed pre-key to rotate in, or omitted to keep the current one. */
  signedPreKey?: SignedPreKeyDto;
}

export interface PreKeyBundleDto {
  userId: string;
  registrationId: number;
  /** Base64 public identity key. */
  identityKey: string;
  signedPreKey: SignedPreKeyDto;
  /** May be null if the peer has exhausted their one-time pre-keys. */
  oneTimePreKey: PreKeyDto | null;
}

export interface PreKeyCountResponse {
  count: number;
}
