export type MessageStatus = 'SENT' | 'DELIVERED' | 'READ';

/** A message as returned by the backend. `ciphertext` is Base64 Signal ciphertext. */
export interface Message {
  id: string;
  chatId: string;
  senderId: string;
  ciphertext: string;
  ciphertextType: number;
  replyToMessageId: string | null;
  status: MessageStatus;
  expiresAt: string | null;
  createdAt: string;
}

/** Send an encrypted message. `ciphertext` is Base64. */
export interface SendMessageRequest {
  ciphertext: string;
  ciphertextType: number;
  replyToMessageId?: string | null;
}
