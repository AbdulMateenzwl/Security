import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { ChatService } from '../../core/services/chat.service';
import { RealtimeService } from '../../core/services/realtime.service';
import { SignalService } from '../../core/services/signal.service';
import { Chat } from '../../core/models/chat.models';
import { Message } from '../../core/models/message.models';
import { extractErrorMessage } from '../../core/util/api-error';
import { chatDisplayName, chatInitial } from './chat-display';
import { CreateChatDialog } from './create-chat-dialog/create-chat-dialog';

@Component({
  selector: 'app-chats',
  imports: [CreateChatDialog, RouterLink],
  templateUrl: './chats.html',
  styleUrl: './chats.scss',
})
export class Chats implements OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly chatService = inject(ChatService);
  private readonly signalService = inject(SignalService);
  private readonly realtime = inject(RealtimeService);
  private readonly router = inject(Router);

  readonly user = this.auth.user;
  readonly encryptionState = this.signalService.state;

  readonly chats = signal<Chat[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly showCreate = signal(false);
  /** Ids of chats with a message that arrived (from someone else) while this list was open. */
  readonly unread = signal<ReadonlySet<string>>(new Set());

  readonly hasChats = computed(() => this.chats().length > 0);

  /** Unsubscribe callbacks for the per-chat live topics we're currently listening on. */
  private liveUnsubs: Array<() => void> = [];

  constructor() {
    this.load();
    // Provision this device's Signal keys on first sign-in so peers can start encrypted sessions.
    this.signalService.ensureProvisioned().catch(() => {
      /* state signal reflects the failure; the Security page lets the user retry */
    });
  }

  ngOnDestroy(): void {
    this.teardownLive();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.chatService.list().subscribe({
      next: (chats) => {
        this.chats.set(this.sortByRecent(chats));
        this.loading.set(false);
        // Listen for new messages across all chats so the list reorders/flags live.
        this.subscribeToAll();
      },
      error: (err) => {
        this.error.set(extractErrorMessage(err, 'Could not load your chats.'));
        this.loading.set(false);
      },
    });
  }

  isUnread(chat: Chat): boolean {
    return this.unread().has(chat.id);
  }

  displayName(chat: Chat): string {
    return chatDisplayName(chat, this.user()?.id);
  }

  initial(chat: Chat): string {
    return chatInitial(chat, this.user()?.id);
  }

  subtitle(_chat: Chat): string {
    return 'Direct message';
  }

  open(chat: Chat): void {
    this.clearUnread(chat.id);
    this.router.navigate(['/chats', chat.id]);
  }

  onCreated(chat: Chat): void {
    this.showCreate.set(false);
    // Avoid duplicates if the chat already existed (e.g. reused direct chat).
    this.chats.update((list) => this.sortByRecent([chat, ...list.filter((c) => c.id !== chat.id)]));
    // Pick up the new chat's live topic (a reused direct chat is already subscribed — harmless).
    this.subscribeToAll();
  }

  logout(): void {
    this.teardownLive();
    this.realtime.disconnect();
    this.auth.logout().subscribe({
      next: () => this.router.navigate(['/login']),
      error: () => this.router.navigate(['/login']),
    });
  }

  /** (Re)subscribe to every current chat's live message topic. */
  private subscribeToAll(): void {
    this.teardownLive();
    this.realtime.connect();
    for (const chat of this.chats()) {
      this.liveUnsubs.push(this.realtime.subscribeToChat(chat.id, (m) => this.onLiveMessage(m)));
    }
  }

  /** A new message landed in some chat: bump it to the top and flag it unread if it isn't ours. */
  private onLiveMessage(m: Message): void {
    this.bumpToTop(m.chatId, m.createdAt);
    if (m.senderId !== this.user()?.id) {
      this.markUnread(m.chatId);
    }
  }

  private bumpToTop(chatId: string, activityAt: string): void {
    this.chats.update((list) => {
      const chat = list.find((c) => c.id === chatId);
      if (!chat) return list;
      return [{ ...chat, updatedAt: activityAt }, ...list.filter((c) => c.id !== chatId)];
    });
  }

  private markUnread(chatId: string): void {
    this.unread.update((set) => {
      if (set.has(chatId)) return set;
      return new Set(set).add(chatId);
    });
  }

  private clearUnread(chatId: string): void {
    this.unread.update((set) => {
      if (!set.has(chatId)) return set;
      const next = new Set(set);
      next.delete(chatId);
      return next;
    });
  }

  private teardownLive(): void {
    for (const unsub of this.liveUnsubs) unsub();
    this.liveUnsubs = [];
  }

  private sortByRecent(chats: Chat[]): Chat[] {
    return [...chats].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
  }
}
