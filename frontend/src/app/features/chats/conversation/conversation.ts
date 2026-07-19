import { DatePipe } from '@angular/common';
import { Component, ElementRef, OnDestroy, inject, signal, viewChild } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { ChatService } from '../../../core/services/chat.service';
import { MessageService } from '../../../core/services/message.service';
import { RealtimeService } from '../../../core/services/realtime.service';
import { SignalService } from '../../../core/services/signal.service';
import { Chat, ChatMember } from '../../../core/models/chat.models';
import { Message } from '../../../core/models/message.models';
import { TypingEvent } from '../../../core/models/realtime.models';
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
export class Conversation implements OnDestroy {
  /** History page size — mirrors the backend's default `limit`. */
  private static readonly PAGE_SIZE = 30;

  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly chatService = inject(ChatService);
  private readonly messageService = inject(MessageService);
  private readonly signalService = inject(SignalService);
  private readonly realtime = inject(RealtimeService);

  private readonly scrollContainer = viewChild<ElementRef<HTMLElement>>('scrollContainer');

  readonly chat = signal<Chat | null>(null);
  readonly peer = signal<ChatMember | null>(null);
  readonly messages = signal<RenderedMessage[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly sending = signal(false);
  readonly unsupportedGroup = signal(false);
  readonly peerTyping = signal(false);
  /** Whether older history pages may still exist (last page came back full). */
  readonly hasMore = signal(false);
  /** A backward page is in flight (drives the top spinner and guards re-entry). */
  readonly loadingOlder = signal(false);
  /** True when some messages predate this device and were hidden (shows an explanatory divider). */
  readonly hasHiddenHistory = signal(false);
  readonly connected = this.realtime.connected;

  /** Epoch-ms this device provisioned; messages older than this can't be decrypted here (0 = show all). */
  private provisionedAt = 0;

  readonly draft = new FormControl('', { nonNullable: true });
  /** Whether the composer is empty — a signal so the Send button stays reactive in this zoneless app. */
  readonly draftEmpty = signal(true);

  /** Unsubscribe callbacks for the current chat's live topics. */
  private liveUnsubs: Array<() => void> = [];
  private typingStopTimer?: ReturnType<typeof setTimeout>;
  private peerTypingTimer?: ReturnType<typeof setTimeout>;
  private iAmTyping = false;

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

  ngOnDestroy(): void {
    this.teardownLive();
  }

  title(): string {
    const chat = this.chat();
    return chat ? chatDisplayName(chat, this.myId) : 'Chat';
  }

  /** True when the peer's identity (safety number) changed — a device switch, or a possible MITM. */
  safetyNumberChanged(): boolean {
    const peer = this.peer();
    return peer ? this.signalService.identityChanged(peer.userId) : false;
  }

  dismissSafetyWarning(): void {
    const peer = this.peer();
    if (peer) {
      this.signalService.acknowledgeIdentityChange(peer.userId);
    }
  }

  private async loadConversation(chatId: string): Promise<void> {
    this.teardownLive();
    this.loading.set(true);
    this.error.set(null);
    this.unsupportedGroup.set(false);
    this.peerTyping.set(false);
    this.hasMore.set(false);
    this.loadingOlder.set(false);
    this.hasHiddenHistory.set(false);
    this.messages.set([]);
    try {
      await this.signalService.ensureProvisioned();
      // Messages older than this device can't be decrypted here — hide them (WhatsApp-style).
      this.provisionedAt = (await this.signalService.getProvisionedAt()) ?? 0;
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

      const history = await firstValueFrom(
        this.messageService.history(chatId, undefined, Conversation.PAGE_SIZE),
      );
      // A full page back means there may be older history to page in on scroll-up.
      this.hasMore.set(history.length >= Conversation.PAGE_SIZE);
      this.messages.set(await this.renderPage(history, peer.userId));
      this.loading.set(false);
      this.scrollToBottomSoon();

      // Go live: receive new messages and typing indicators over the WebSocket.
      this.realtime.connect();
      this.liveUnsubs.push(
        this.realtime.subscribeToChat(chatId, (m) => this.onLiveMessage(m, peer.userId)),
        this.realtime.subscribeToTyping(chatId, (e) => this.onTyping(e)),
      );
    } catch (err) {
      this.error.set(extractErrorMessage(err, 'Could not open this conversation.'));
      this.loading.set(false);
    }
  }

  /** A message pushed over the socket. Peer messages only — our own are shown optimistically. */
  private async onLiveMessage(m: Message, peerUserId: string): Promise<void> {
    // Our own messages are appended optimistically by send(); ignore the server's echo of them.
    // This also avoids a race where the echo arrives before send() has cached our plaintext, which
    // would otherwise render our own message as an undecryptable placeholder.
    if (m.senderId === this.myId) return;
    if (this.messages().some((x) => x.id === m.id)) {
      return; // already shown, or a duplicate delivery
    }
    const rendered = await this.render(m, peerUserId);
    this.appendUnique(rendered);
    this.peerTyping.set(false);
    this.scrollToBottomSoon();
  }

  /** Append a rendered message unless one with the same id is already present (atomic dedup). */
  private appendUnique(msg: RenderedMessage): void {
    this.messages.update((list) => (list.some((m) => m.id === msg.id) ? list : [...list, msg]));
  }

  private onTyping(event: TypingEvent): void {
    if (event.userId === this.myId) return;
    this.peerTyping.set(event.typing);
    clearTimeout(this.peerTypingTimer);
    if (event.typing) {
      // Safety net in case the "stopped typing" event is missed.
      this.peerTypingTimer = setTimeout(() => this.peerTyping.set(false), 5000);
    }
  }

  /** Notify the peer we're typing; schedule a "stopped" signal after a short idle. */
  onInput(): void {
    this.draftEmpty.set(!this.draft.value.trim());
    const chat = this.chat();
    if (!chat || this.unsupportedGroup()) return;
    if (!this.iAmTyping) {
      this.iAmTyping = true;
      this.realtime.sendTyping(chat.id, true);
    }
    clearTimeout(this.typingStopTimer);
    this.typingStopTimer = setTimeout(() => this.stopTyping(), 2000);
  }

  private stopTyping(): void {
    const chat = this.chat();
    if (this.iAmTyping && chat) {
      this.iAmTyping = false;
      this.realtime.sendTyping(chat.id, false);
    }
  }

  private teardownLive(): void {
    this.stopTyping();
    clearTimeout(this.typingStopTimer);
    clearTimeout(this.peerTypingTimer);
    for (const unsub of this.liveUnsubs) unsub();
    this.liveUnsubs = [];
  }

  /** Refresh to pull in messages that arrived since the last load (resets to the newest page). */
  async refresh(): Promise<void> {
    const chat = this.chat();
    const peer = this.peer();
    if (!chat || !peer) return;
    try {
      const history = await firstValueFrom(
        this.messageService.history(chat.id, undefined, Conversation.PAGE_SIZE),
      );
      this.hasMore.set(history.length >= Conversation.PAGE_SIZE);
      this.messages.set(await this.renderPage(history, peer.userId));
      this.scrollToBottomSoon();
    } catch (err) {
      this.error.set(extractErrorMessage(err, 'Could not refresh messages.'));
    }
  }

  /** Near the top of the scroll area: pull in the previous page of history. */
  onScroll(): void {
    const el = this.scrollContainer()?.nativeElement;
    if (el && el.scrollTop < 80 && this.hasMore() && !this.loadingOlder() && !this.loading()) {
      void this.loadOlder();
    }
  }

  /** Fetch the page of messages older than the oldest one we hold and prepend it. */
  private async loadOlder(): Promise<void> {
    const chat = this.chat();
    const peer = this.peer();
    const oldest = this.messages()[0];
    if (!chat || !peer || !oldest || this.loadingOlder() || !this.hasMore()) return;

    // Capture height before anything renders so we can keep the viewport anchored after prepend.
    const prevHeight = this.scrollContainer()?.nativeElement.scrollHeight ?? 0;
    this.loadingOlder.set(true);
    try {
      const older = await firstValueFrom(
        this.messageService.history(chat.id, oldest.id, Conversation.PAGE_SIZE),
      );
      this.hasMore.set(older.length >= Conversation.PAGE_SIZE);
      const rendered = await this.renderPage(older, peer.userId);
      if (rendered.length) {
        this.messages.update((list) => [...rendered, ...list]);
        this.anchorAfterPrepend(prevHeight);
      }
    } catch (err) {
      this.error.set(extractErrorMessage(err, 'Could not load earlier messages.'));
    } finally {
      this.loadingOlder.set(false);
    }
  }

  /**
   * Turn a backend page (newest-first) into display rows. Messages older than this device were
   * encrypted to a previous device and can't be shown here, so they're filtered out (and a divider
   * is flagged). The rest are decrypted oldest-first so the ratchet advances in order.
   */
  private async renderPage(page: Message[], peerUserId: string): Promise<RenderedMessage[]> {
    const visible = page.filter((m) => new Date(m.createdAt).getTime() >= this.provisionedAt);
    if (visible.length < page.length) {
      this.hasHiddenHistory.set(true);
    }
    const chronological = [...visible].reverse();
    const rendered: RenderedMessage[] = [];
    for (const m of chronological) {
      rendered.push(await this.render(m, peerUserId));
    }
    return rendered;
  }

  /** After prepending older messages, shift scrollTop so the user stays on the same message. */
  private anchorAfterPrepend(prevHeight: number): void {
    setTimeout(() => {
      const el = this.scrollContainer()?.nativeElement;
      if (el) {
        el.scrollTop += el.scrollHeight - prevHeight;
      }
    });
  }

  /**
   * Turn a stored message into a display row. Messages that this device can't read are shown as a
   * direction-aware placeholder rather than raw text: one we sent but have no local plaintext for
   * was "Sent from another device"; an incoming one we can't decrypt was "Received on another
   * device" (it was part of another device's session). (History from before this device even
   * existed is filtered out earlier by `renderPage`, so these placeholders only cover messages that
   * happened on a *different* device while this one was signed out.)
   */
  private async render(m: Message, peerUserId: string): Promise<RenderedMessage> {
    const mine = m.senderId === this.myId;
    if (mine) {
      const cached = await this.signalService.getCachedPlaintext(m.id);
      return {
        id: m.id,
        mine: true,
        text: cached ?? '🔒 Sent from another device',
        failed: cached === undefined,
        createdAt: m.createdAt,
      };
    }
    try {
      const text = await this.signalService.decryptAndCache(peerUserId, m.id, m.ciphertext, m.ciphertextType);
      return { id: m.id, mine: false, text, failed: false, createdAt: m.createdAt };
    } catch {
      return { id: m.id, mine: false, text: '🔒 Received on another device', failed: true, createdAt: m.createdAt };
    }
  }

  /** Native form submit (Enter key or the Send button). Prevent the browser's default page
   *  navigation — without an NgForm/[formGroup] on the form, (ngSubmit) never fires, so we handle
   *  the raw submit event ourselves. */
  onSubmit(event: Event): void {
    event.preventDefault();
    void this.send();
  }

  async send(): Promise<void> {
    const text = this.draft.value.trim();
    const chat = this.chat();
    const peer = this.peer();
    if (!text || !chat || !peer || this.sending()) return;

    this.sending.set(true);
    this.error.set(null);
    this.stopTyping();
    try {
      const { ciphertext, ciphertextType } = await this.signalService.encrypt(peer.userId, text);
      const saved = await firstValueFrom(this.messageService.send(chat.id, { ciphertext, ciphertextType }));
      await this.signalService.cacheSentPlaintext(saved.id, text);
      this.appendUnique({ id: saved.id, mine: true, text, failed: false, createdAt: saved.createdAt });
      this.draft.setValue('');
      this.draftEmpty.set(true);
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
