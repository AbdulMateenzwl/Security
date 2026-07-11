import { DatePipe } from '@angular/common';
import { Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { ChatService } from '../../../core/services/chat.service';
import { MessageService } from '../../../core/services/message.service';
import { SignalService } from '../../../core/services/signal.service';
import { Chat, ChatMember } from '../../../core/models/chat.models';
import { Message } from '../../../core/models/message.models';
import { extractErrorMessage } from '../../../core/util/api-error';
import { chatDisplayName } from '../chat-display';

/** A message prepared for display: decrypted text (or a placeholder) plus render metadata. */
interface RenderedMessage {
  id: string;
  mine: boolean;
  text: string;
  failed: boolean;
  createdAt: string;
}

@Component({
  selector: 'app-conversation',
  imports: [ReactiveFormsModule, RouterLink, DatePipe],
  templateUrl: './conversation.html',
  styleUrl: './conversation.scss',
})
export class Conversation {
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly chatService = inject(ChatService);
  private readonly messageService = inject(MessageService);
  private readonly signalService = inject(SignalService);

  private readonly scrollContainer = viewChild<ElementRef<HTMLElement>>('scrollContainer');

  readonly chat = signal<Chat | null>(null);
  readonly peer = signal<ChatMember | null>(null);
  readonly messages = signal<RenderedMessage[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly sending = signal(false);
  readonly unsupportedGroup = signal(false);

  readonly draft = new FormControl('', { nonNullable: true });

  private get myId(): string | undefined {
    return this.auth.user()?.id;
  }

  constructor() {
    this.route.paramMap.subscribe((params) => {
      const id = params.get('id');
      if (id) {
        this.loadConversation(id);
      }
    });
  }

  title(): string {
    const chat = this.chat();
    return chat ? chatDisplayName(chat, this.myId) : 'Chat';
  }

  private async loadConversation(chatId: string): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    this.unsupportedGroup.set(false);
    this.messages.set([]);
    try {
      await this.signalService.ensureProvisioned();
      const chat = await firstValueFrom(this.chatService.get(chatId));
      this.chat.set(chat);

      if (chat.type === 'GROUP') {
        // Group E2E (Signal "sender keys") isn't implemented — direct chats only for now.
        this.unsupportedGroup.set(true);
        this.loading.set(false);
        return;
      }

      const peer = chat.members.find((m) => m.userId !== this.myId) ?? null;
      this.peer.set(peer);
      if (!peer) {
        this.error.set('This direct chat has no other participant.');
        this.loading.set(false);
        return;
      }

      const history = await firstValueFrom(this.messageService.history(chatId));
      // Backend returns newest-first; decrypt oldest-first to respect ratchet ordering.
      const chronological = [...history].reverse();
      const rendered: RenderedMessage[] = [];
      for (const m of chronological) {
        rendered.push(await this.render(m, peer.userId));
      }
      this.messages.set(rendered);
      this.loading.set(false);
      this.scrollToBottomSoon();
    } catch (err) {
      this.error.set(extractErrorMessage(err, 'Could not open this conversation.'));
      this.loading.set(false);
    }
  }

  /** Refresh to pull in messages that arrived since the last load. */
  async refresh(): Promise<void> {
    const chat = this.chat();
    const peer = this.peer();
    if (!chat || !peer) return;
    try {
      const history = await firstValueFrom(this.messageService.history(chat.id));
      const chronological = [...history].reverse();
      const rendered: RenderedMessage[] = [];
      for (const m of chronological) {
        rendered.push(await this.render(m, peer.userId));
      }
      this.messages.set(rendered);
      this.scrollToBottomSoon();
    } catch (err) {
      this.error.set(extractErrorMessage(err, 'Could not refresh messages.'));
    }
  }

  /** Turn a stored message into a display row, decrypting incoming ones (cached, once, in order). */
  private async render(m: Message, peerUserId: string): Promise<RenderedMessage> {
    const mine = m.senderId === this.myId;
    if (mine) {
      const cached = await this.signalService.getCachedPlaintext(m.id);
      return {
        id: m.id,
        mine: true,
        text: cached ?? '🔒 Encrypted (sent from another device)',
        failed: cached === undefined,
        createdAt: m.createdAt,
      };
    }
    try {
      const text = await this.signalService.decryptAndCache(peerUserId, m.id, m.ciphertext, m.ciphertextType);
      return { id: m.id, mine: false, text, failed: false, createdAt: m.createdAt };
    } catch {
      return { id: m.id, mine: false, text: '🔒 Unable to decrypt', failed: true, createdAt: m.createdAt };
    }
  }

  async send(): Promise<void> {
    const text = this.draft.value.trim();
    const chat = this.chat();
    const peer = this.peer();
    if (!text || !chat || !peer || this.sending()) return;

    this.sending.set(true);
    this.error.set(null);
    try {
      const { ciphertext, ciphertextType } = await this.signalService.encrypt(peer.userId, text);
      const saved = await firstValueFrom(this.messageService.send(chat.id, { ciphertext, ciphertextType }));
      await this.signalService.cacheSentPlaintext(saved.id, text);
      this.messages.update((list) => [
        ...list,
        { id: saved.id, mine: true, text, failed: false, createdAt: saved.createdAt },
      ]);
      this.draft.setValue('');
      this.scrollToBottomSoon();
    } catch (err) {
      this.error.set(extractErrorMessage(err, 'Could not send your message.'));
    } finally {
      this.sending.set(false);
    }
  }

  private scrollToBottomSoon(): void {
    setTimeout(() => {
      const el = this.scrollContainer()?.nativeElement;
      if (el) {
        el.scrollTop = el.scrollHeight;
      }
    });
  }
}
