export type ChatType = 'DIRECT' | 'GROUP';
export type MemberRole = 'ADMIN' | 'MEMBER';

/** A member of a chat — mirrors the backend ChatMemberDto. */
export interface ChatMember {
  userId: string;
  username: string;
  role: MemberRole;
  muted: boolean;
  joinedAt: string;
}

/** A chat and its participants — mirrors the backend ChatDto. */
export interface Chat {
  id: string;
  type: ChatType;
  name: string | null;
  avatarUrl: string | null;
  disappearingMessageTtl: number | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  members: ChatMember[];
}

export interface CreateChatRequest {
  type: ChatType;
  name?: string | null;
  memberIds: string[];
}
