import { Chat } from '../../core/models/chat.models';

/**
 * Display name for a chat from the current user's perspective: group chats use their name; direct
 * chats show the other participant's username.
 */
export function chatDisplayName(chat: Chat, currentUserId: string | undefined): string {
  if (chat.type === 'GROUP') {
    return chat.name?.trim() || 'Unnamed group';
  }
  const other = chat.members.find((m) => m.userId !== currentUserId);
  return other?.username ?? 'Direct chat';
}

/** A short avatar initial for a chat. */
export function chatInitial(chat: Chat, currentUserId: string | undefined): string {
  return chatDisplayName(chat, currentUserId).charAt(0).toUpperCase();
}
