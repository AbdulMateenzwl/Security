import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  KeyHelper,
  SessionBuilder,
  SessionCipher,
  SignalProtocolAddress,
} from '@privacyresearch/libsignal-protocol-typescript';
import type { DeviceType } from '@privacyresearch/libsignal-protocol-typescript';
import {
  arrayBufferToBase64,
  arrayBufferToHex,
  arrayBufferToUtf8,
  base64ToArrayBuffer,
  binaryStringToBytes,
  utf8ToArrayBuffer,
} from '../crypto/binary';
import { SignalProtocolStore } from '../crypto/signal-store';
import { PreKeyDto } from '../models/signal.models';
import { AuthService } from './auth.service';
import { SignalApiService } from './signal-api.service';
import { UserService } from './user.service';

/** This project models one device per user. */
const DEVICE_ID = 1;
/** How many one-time pre-keys to publish per batch. */
const PREKEY_BATCH = 20;
/** Replenish the server's pool when it drops below this. */
export const PREKEY_LOW_WATERMARK = 5;

const META_NEXT_PREKEY_ID = 'nextPreKeyId';
const META_NEXT_SIGNED_ID = 'nextSignedPreKeyId';

export type ProvisionState = 'unknown' | 'provisioning' | 'ready' | 'error';

/** The ciphertext produced for a message, ready to POST to the backend. */
export interface EncryptedPayload {
  /** Base64 Signal ciphertext. */
  ciphertext: string;
  /** Signal message type: 1 = WhisperMessage, 3 = PreKeyWhisperMessage. */
  ciphertextType: number;
}

/**
 * Orchestrates the client side of the Signal protocol: device provisioning (key generation +
 * publishing the PUBLIC halves), pairwise session establishment, and message encrypt/decrypt.
 *
 * Private keys are generated here and persisted only in {@link SignalProtocolStore} (IndexedDB).
 * They never touch the network — the server is a blind relay that stores public keys and opaque
 * ciphertext only.
 */
@Injectable({ providedIn: 'root' })
export class SignalService {
  private readonly api = inject(SignalApiService);
  private readonly auth = inject(AuthService);
  private readonly users = inject(UserService);

  private store: SignalProtocolStore | null = null;
  private storeUserId: string | null = null;
  private provisioning: Promise<void> | null = null;

  readonly state = signal<ProvisionState>('unknown');

  /** The store for the currently authenticated user, created lazily and cached. */
  private getStore(): SignalProtocolStore {
    const userId = this.auth.user()?.id;
    if (!userId) {
      throw new Error('Cannot use Signal store without an authenticated user');
    }
    if (!this.store || this.storeUserId !== userId) {
      this.store = new SignalProtocolStore(userId);
      this.storeUserId = userId;
    }
    return this.store;
  }

  async isProvisioned(): Promise<boolean> {
    return this.getStore().isProvisioned();
  }

  /**
   * Ensure this device has published Signal keys. Idempotent and safe to call on every app start;
   * generation + upload only happens the first time (per user, per browser).
   */
  ensureProvisioned(): Promise<void> {
    if (this.provisioning) return this.provisioning;
    this.provisioning = this.doProvision().finally(() => (this.provisioning = null));
    return this.provisioning;
  }

  private async doProvision(): Promise<void> {
    const store = this.getStore();
    if (await store.isProvisioned()) {
      // Locally provisioned — but the server must still agree with THIS device's identity. It can
      // drift if the keys were replaced from another browser/device or lost server-side; when that
      // happens our local sessions are unusable and every peer's session setup with us fails. Detect
      // the divergence and re-provision a fresh, consistent key set so messaging self-heals.
      if (await this.serverMatchesLocalIdentity()) {
        this.state.set('ready');
        return;
      }
      console.warn('[signal] server identity out of sync with this device — re-provisioning');
      await store.clearAll();
    }
    this.state.set('provisioning');
    try {
      const identityKeyPair = await KeyHelper.generateIdentityKeyPair();
      const registrationId = KeyHelper.generateRegistrationId();
      await store.putIdentityKeyPair(identityKeyPair);
      await store.putLocalRegistrationId(registrationId);

      // Signed pre-key (id 1 for the first one).
      const signedPreKey = await KeyHelper.generateSignedPreKey(identityKeyPair, 1);
      await store.storeSignedPreKey(signedPreKey.keyId, signedPreKey.keyPair);
      await store.setMetaNumber(META_NEXT_SIGNED_ID, 2);

      // One-time pre-keys.
      const preKeys = await this.generateAndStorePreKeys(PREKEY_BATCH);

      // Publish PUBLIC halves only: identity key first, then the pre-key batch + signed pre-key.
      await firstValueFrom(
        this.api.uploadIdentityKey({
          publicKey: arrayBufferToBase64(identityKeyPair.pubKey),
          registrationId,
        }),
      );
      await firstValueFrom(
        this.api.uploadPreKeys({
          preKeys,
          signedPreKey: {
            keyId: signedPreKey.keyId,
            publicKey: arrayBufferToBase64(signedPreKey.keyPair.pubKey),
            signature: arrayBufferToBase64(signedPreKey.signature),
          },
        }),
      );

      this.state.set('ready');
    } catch (err) {
      this.state.set('error');
      throw err;
    }
  }

  /**
   * Whether the server's published identity fingerprint for this user still matches the key held
   * locally. On any error (e.g. offline) we assume it matches, to avoid needlessly wiping keys.
   */
  private async serverMatchesLocalIdentity(): Promise<boolean> {
    try {
      const localFp = await this.getFingerprint();
      const userId = this.auth.user()?.id;
      if (!localFp || !userId) return false;
      const { fingerprint } = await firstValueFrom(this.users.fingerprint(userId));
      return fingerprint != null && fingerprint.toLowerCase() === localFp.toLowerCase();
    } catch {
      return true;
    }
  }

  /** Generate `count` fresh one-time pre-keys, persist their private halves, return public DTOs. */
  private async generateAndStorePreKeys(count: number): Promise<PreKeyDto[]> {
    const store = this.getStore();
    let nextId = (await store.getMetaNumber(META_NEXT_PREKEY_ID)) ?? 1;
    const dtos: PreKeyDto[] = [];
    for (let i = 0; i < count; i++) {
      const preKey = await KeyHelper.generatePreKey(nextId);
      await store.storePreKey(preKey.keyId, preKey.keyPair);
      dtos.push({ keyId: preKey.keyId, publicKey: arrayBufferToBase64(preKey.keyPair.pubKey) });
      nextId++;
    }
    await store.setMetaNumber(META_NEXT_PREKEY_ID, nextId);
    return dtos;
  }

  /** Top up the server's one-time pre-key pool with a fresh batch. */
  async replenishPreKeys(count = PREKEY_BATCH): Promise<void> {
    const preKeys = await this.generateAndStorePreKeys(count);
    await firstValueFrom(this.api.uploadPreKeys({ preKeys }));
  }

  /** This device's identity-key fingerprint (safety number), lowercase hex — matches the server. */
  async getFingerprint(): Promise<string | null> {
    const pair = await this.getStore().getIdentityKeyPair();
    return pair ? arrayBufferToHex(pair.pubKey) : null;
  }

  async getRegistrationId(): Promise<number | null> {
    return (await this.getStore().getLocalRegistrationId()) ?? null;
  }

  /** Remaining one-time pre-keys the server still holds for this user. */
  async remainingPreKeys(): Promise<number> {
    const res = await firstValueFrom(this.api.getPreKeyCount());
    return res.count;
  }

  /** Wipe local keys and re-provision from scratch (new identity → new safety number). */
  async resetDevice(): Promise<void> {
    await this.getStore().clearAll();
    this.state.set('unknown');
    await this.ensureProvisioned();
  }

  // --- Sessions & message crypto (used by the messaging feature) ----------

  /** Establish a pairwise session with a peer if one doesn't already exist. */
  async ensureSession(peerUserId: string): Promise<void> {
    const store = this.getStore();
    const address = new SignalProtocolAddress(peerUserId, DEVICE_ID);
    if (await store.loadSession(address.toString())) {
      return;
    }
    const bundle = await firstValueFrom(this.api.getPreKeyBundle(peerUserId));
    const device: DeviceType = {
      identityKey: base64ToArrayBuffer(bundle.identityKey),
      registrationId: bundle.registrationId,
      signedPreKey: {
        keyId: bundle.signedPreKey.keyId,
        publicKey: base64ToArrayBuffer(bundle.signedPreKey.publicKey),
        signature: base64ToArrayBuffer(bundle.signedPreKey.signature),
      },
      preKey: bundle.oneTimePreKey
        ? {
            keyId: bundle.oneTimePreKey.keyId,
            publicKey: base64ToArrayBuffer(bundle.oneTimePreKey.publicKey),
          }
        : undefined,
    };
    const builder = new SessionBuilder(store, address);
    await builder.processPreKey(device);
  }

  /** Encrypt plaintext for a peer, returning the ciphertext ready to send to the backend. */
  async encrypt(peerUserId: string, plaintext: string): Promise<EncryptedPayload> {
    await this.ensureSession(peerUserId);
    const address = new SignalProtocolAddress(peerUserId, DEVICE_ID);
    const cipher = new SessionCipher(this.getStore(), address);
    const message = await cipher.encrypt(utf8ToArrayBuffer(plaintext));
    // `body` is a Latin-1 binary string; convert to bytes → Base64 for JSON transport.
    const bytes = binaryStringToBytes(message.body ?? '');
    return { ciphertext: arrayBufferToBase64(bytes), ciphertextType: message.type };
  }

  /** Cache the readable text of a message we sent (see {@link SignalProtocolStore.putPlaintext}). */
  async cacheSentPlaintext(messageId: string, text: string): Promise<void> {
    await this.getStore().putPlaintext(messageId, text);
  }

  /** The locally cached plaintext for a message id, if we have it. */
  getCachedPlaintext(messageId: string): Promise<string | undefined> {
    return this.getStore().getPlaintext(messageId);
  }

  /**
   * Decrypt an incoming message exactly once and cache the result. On a second call for the same id
   * (e.g. after a reload) it returns the cached plaintext without touching the ratchet — decrypting
   * the same ciphertext twice would fail because the message key has already been consumed.
   */
  async decryptAndCache(
    peerUserId: string,
    messageId: string,
    ciphertextBase64: string,
    ciphertextType: number,
  ): Promise<string> {
    const cached = await this.getStore().getPlaintext(messageId);
    if (cached !== undefined) {
      return cached;
    }
    const text = await this.decrypt(peerUserId, ciphertextBase64, ciphertextType);
    await this.getStore().putPlaintext(messageId, text);
    return text;
  }

  /** Decrypt a peer's ciphertext (Base64) back to plaintext. */
  async decrypt(peerUserId: string, ciphertextBase64: string, ciphertextType: number): Promise<string> {
    const address = new SignalProtocolAddress(peerUserId, DEVICE_ID);
    const cipher = new SessionCipher(this.getStore(), address);
    const buffer = base64ToArrayBuffer(ciphertextBase64);
    // Type 3 = PreKeyWhisperMessage establishes the session as a side effect.
    const plaintext =
      ciphertextType === 3
        ? await cipher.decryptPreKeyWhisperMessage(buffer)
        : await cipher.decryptWhisperMessage(buffer);
    return arrayBufferToUtf8(plaintext);
  }

  /** Format a hex fingerprint into readable 5-char groups (safety-number style). */
  static formatFingerprint(hex: string): string {
    return (hex.match(/.{1,5}/g) ?? []).join(' ');
  }
}
