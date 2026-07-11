import type { Direction, KeyPairType, StorageType } from '@privacyresearch/libsignal-protocol-typescript';

/**
 * IndexedDB-backed {@link StorageType} for libsignal, namespaced per user id.
 *
 * This is where the device's PRIVATE key material lives — identity key pair, one-time and signed
 * pre-keys, and ratchet sessions. It is written and read only in the browser and is NEVER sent to
 * the server, which upholds the blind-relay guarantee: the backend only ever holds public keys and
 * opaque ciphertext.
 */

const DB_VERSION = 2;
const STORE_META = 'meta';
const STORE_PREKEYS = 'preKeys';
const STORE_SIGNED_PREKEYS = 'signedPreKeys';
const STORE_SESSIONS = 'sessions';
const STORE_IDENTITIES = 'identities';
const STORE_PLAINTEXT = 'plaintext';

const KEY_IDENTITY_PAIR = 'identityKeyPair';
const KEY_REGISTRATION_ID = 'registrationId';

function dbName(userId: string): string {
  return `securechat-signal-${userId}`;
}

function equalBuffers(a: ArrayBuffer, b: ArrayBuffer): boolean {
  if (a.byteLength !== b.byteLength) return false;
  const va = new Uint8Array(a);
  const vb = new Uint8Array(b);
  for (let i = 0; i < va.length; i++) {
    if (va[i] !== vb[i]) return false;
  }
  return true;
}

export class SignalProtocolStore implements StorageType {
  private db: IDBDatabase | null = null;

  constructor(private readonly userId: string) {}

  // --- IndexedDB plumbing ------------------------------------------------

  private open(): Promise<IDBDatabase> {
    if (this.db) return Promise.resolve(this.db);
    return new Promise((resolve, reject) => {
      const req = indexedDB.open(dbName(this.userId), DB_VERSION);
      req.onupgradeneeded = () => {
        const db = req.result;
        for (const name of [
          STORE_META,
          STORE_PREKEYS,
          STORE_SIGNED_PREKEYS,
          STORE_SESSIONS,
          STORE_IDENTITIES,
          STORE_PLAINTEXT,
        ]) {
          if (!db.objectStoreNames.contains(name)) {
            db.createObjectStore(name);
          }
        }
      };
      req.onsuccess = () => {
        this.db = req.result;
        resolve(req.result);
      };
      req.onerror = () => reject(req.error);
    });
  }

  private async get<T>(store: string, key: IDBValidKey): Promise<T | undefined> {
    const db = await this.open();
    return new Promise((resolve, reject) => {
      const req = db.transaction(store, 'readonly').objectStore(store).get(key);
      req.onsuccess = () => resolve(req.result as T | undefined);
      req.onerror = () => reject(req.error);
    });
  }

  private async put(store: string, key: IDBValidKey, value: unknown): Promise<void> {
    const db = await this.open();
    return new Promise((resolve, reject) => {
      const tx = db.transaction(store, 'readwrite');
      tx.objectStore(store).put(value, key);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  }

  private async del(store: string, key: IDBValidKey): Promise<void> {
    const db = await this.open();
    return new Promise((resolve, reject) => {
      const tx = db.transaction(store, 'readwrite');
      tx.objectStore(store).delete(key);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  }

  private async count(store: string): Promise<number> {
    const db = await this.open();
    return new Promise((resolve, reject) => {
      const req = db.transaction(store, 'readonly').objectStore(store).count();
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  }

  // --- App-facing helpers (not part of StorageType) ----------------------

  /** Persist the freshly generated identity key pair (private material stays local). */
  async putIdentityKeyPair(pair: KeyPairType): Promise<void> {
    await this.put(STORE_META, KEY_IDENTITY_PAIR, pair);
  }

  async putLocalRegistrationId(registrationId: number): Promise<void> {
    await this.put(STORE_META, KEY_REGISTRATION_ID, registrationId);
  }

  /** Whether this device already has an identity key pair provisioned. */
  async isProvisioned(): Promise<boolean> {
    return (await this.getIdentityKeyPair()) !== undefined;
  }

  countPreKeys(): Promise<number> {
    return this.count(STORE_PREKEYS);
  }

  /** Read a small numeric counter from the meta store (e.g. the next pre-key id to allocate). */
  getMetaNumber(key: string): Promise<number | undefined> {
    return this.get<number>(STORE_META, key);
  }

  async setMetaNumber(key: string, value: number): Promise<void> {
    await this.put(STORE_META, key, value);
  }

  /**
   * Local plaintext cache keyed by message id. Signal ciphertext can only be decrypted once and in
   * order, and a sender cannot decrypt their own outgoing ciphertext — so the readable text of every
   * message is cached here (on this device only) for re-rendering on reload.
   */
  getPlaintext(messageId: string): Promise<string | undefined> {
    return this.get<string>(STORE_PLAINTEXT, messageId);
  }

  async putPlaintext(messageId: string, text: string): Promise<void> {
    await this.put(STORE_PLAINTEXT, messageId, text);
  }

  /** Wipe every store — used when the user resets their device keys. */
  async clearAll(): Promise<void> {
    const db = await this.open();
    return new Promise((resolve, reject) => {
      const stores = [
        STORE_META,
        STORE_PREKEYS,
        STORE_SIGNED_PREKEYS,
        STORE_SESSIONS,
        STORE_IDENTITIES,
        STORE_PLAINTEXT,
      ];
      const tx = db.transaction(stores, 'readwrite');
      for (const s of stores) tx.objectStore(s).clear();
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  }

  // --- StorageType implementation ----------------------------------------

  getIdentityKeyPair(): Promise<KeyPairType | undefined> {
    return this.get<KeyPairType>(STORE_META, KEY_IDENTITY_PAIR);
  }

  getLocalRegistrationId(): Promise<number | undefined> {
    return this.get<number>(STORE_META, KEY_REGISTRATION_ID);
  }

  async isTrustedIdentity(identifier: string, identityKey: ArrayBuffer, _direction: Direction): Promise<boolean> {
    const existing = await this.get<ArrayBuffer>(STORE_IDENTITIES, identifier);
    if (!existing) {
      // Trust-on-first-use: the safety-number check is the out-of-band MITM guard.
      return true;
    }
    return equalBuffers(existing, identityKey);
  }

  async saveIdentity(identifier: string, identityKey: ArrayBuffer): Promise<boolean> {
    const existing = await this.get<ArrayBuffer>(STORE_IDENTITIES, identifier);
    await this.put(STORE_IDENTITIES, identifier, identityKey);
    return existing !== undefined && !equalBuffers(existing, identityKey);
  }

  loadPreKey(keyId: string | number): Promise<KeyPairType | undefined> {
    return this.get<KeyPairType>(STORE_PREKEYS, String(keyId));
  }

  async storePreKey(keyId: string | number, keyPair: KeyPairType): Promise<void> {
    await this.put(STORE_PREKEYS, String(keyId), keyPair);
  }

  async removePreKey(keyId: string | number): Promise<void> {
    await this.del(STORE_PREKEYS, String(keyId));
  }

  loadSignedPreKey(keyId: string | number): Promise<KeyPairType | undefined> {
    return this.get<KeyPairType>(STORE_SIGNED_PREKEYS, String(keyId));
  }

  async storeSignedPreKey(keyId: string | number, keyPair: KeyPairType): Promise<void> {
    await this.put(STORE_SIGNED_PREKEYS, String(keyId), keyPair);
  }

  async removeSignedPreKey(keyId: string | number): Promise<void> {
    await this.del(STORE_SIGNED_PREKEYS, String(keyId));
  }

  loadSession(identifier: string): Promise<string | undefined> {
    return this.get<string>(STORE_SESSIONS, identifier);
  }

  async storeSession(identifier: string, record: string): Promise<void> {
    await this.put(STORE_SESSIONS, identifier, record);
  }
}
