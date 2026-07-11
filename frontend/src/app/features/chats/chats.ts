import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { ChatService } from '../../core/services/chat.service';
import { Chat } from '../../core/models/chat.models';
import { extractErrorMessage } from '../../core/util/api-error';
import { chatDisplayName, chatInitial } from './chat-display';
import { CreateChatDialog } from './create-chat-dialog/create-chat-dialog';

@Component({
  selector: 'app-chats',
  imports: [CreateChatDialog],
  templateUrl: './chats.html',
  styleUrl: './chats.scss',
})
export class Chats {
  private readonly auth = inject(AuthService);
  private readonly chatService = inject(ChatService);
  private readonly router = inject(Router);

  readonly user = this.auth.user;

  readonly chats = signal<Chat[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly showCreate = signal(false);

  readonly hasChats = computed(() => this.chats().length > 0);

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.chatService.list().subscribe({
      next: (chats) => {
        this.chats.set(this.sortByRecent(chats));
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(extractErrorMessage(err, 'Could not load your chats.'));
        this.loading.set(false);
      },
    });
  }

  displayName(chat: Chat): string {
    return chatDisplayName(chat, this.user()?.id);
  }

  initial(chat: Chat): string {
    return chatInitial(chat, this.user()?.id);
  }

  subtitle(chat: Chat): string {
    if (chat.type === 'GROUP') {
      const count = chat.members.length;
      return `${count} member${count === 1 ? '' : 's'}`;
    }
    return 'Direct message';
  }

  open(chat: Chat): void {
    // Messaging view arrives in the next feature.
    this.router.navigate(['/chats', chat.id]);
  }

  onCreated(chat: Chat): void {
    this.showCreate.set(false);
    // Avoid duplicates if the chat already existed (e.g. reused direct chat).
    this.chats.update((list) => this.sortByRecent([chat, ...list.filter((c) => c.id !== chat.id)]));
  }

  logout(): void {
    this.auth.logout().subscribe({
      next: () => this.router.navigate(['/login']),
      error: () => this.router.navigate(['/login']),
    });
  }

  private sortByRecent(chats: Chat[]): Chat[] {
    return [...chats].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
  }
}
