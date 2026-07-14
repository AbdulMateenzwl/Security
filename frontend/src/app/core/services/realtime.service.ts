import { Injectable, inject, signal } from '@angular/core';
import { Client, StompSubscription } from '@stomp/stompjs';
import { Message } from '../models/message.models';
import { TaskUpdateEvent, TypingEvent } from '../models/realtime.models';
import { TokenStorageService } from './token-storage.service';

interface SubEntry {
  destination: string;
  onBody: (body: string) => void;
  sub?: StompSubscription;
}

/**
 * Single STOMP-over-WebSocket connection to the backend for realtime delivery.
 *
 * Connects to `/ws?token=<accessToken>` (the handshake auth the backend expects) and relays broker
 * messages to subscribers. Subscriptions are tracked so they can be re-established automatically
 * after a reconnect (stomp.js does not resubscribe on its own).
 *
 * Note: message *sending* still goes over REST (which returns the saved id synchronously so the
 * sender can cache its own plaintext); this socket is used for live *receiving* and typing.
 */
@Injectable({ providedIn: 'root' })
export class RealtimeService {
  private readonly storage = inject(TokenStorageService);

  private client: Client | null = null;
  private readonly subs = new Map<number, SubEntry>();
  private counter = 0;

  readonly connected = signal(false);

  /** Open the connection if it isn't already open. Idempotent. */
  connect(): void {
    if (this.client) return;
    const token = this.storage.accessToken;
    if (!token) return;

    const proto = location.protocol === 'https:' ? 'wss' : 'ws';
    const url = `${proto}://${location.host}/ws?token=${encodeURIComponent(token)}`;

    this.client = new Client({
      brokerURL: url,
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        this.connected.set(true);
        this.resubscribeAll();
      },
      onWebSocketClose: () => this.connected.set(false),
      onStompError: () => this.connected.set(false),
    });
    this.client.activate();
  }

  /** Tear down the connection and forget all subscriptions (e.g. on logout). */
  disconnect(): void {
    this.subs.clear();
    void this.client?.deactivate();
    this.client = null;
    this.connected.set(false);
  }

  /** Live new-message stream for a chat. Returns an unsubscribe function. */
  subscribeToChat(chatId: string, handler: (message: Message) => void): () => void {
    return this.addSubscription(`/topic/chat/${chatId}`, (body) => handler(JSON.parse(body) as Message));
  }

  /** Live typing-indicator stream for a chat. Returns an unsubscribe function. */
  subscribeToTyping(chatId: string, handler: (event: TypingEvent) => void): () => void {
    return this.addSubscription(`/topic/chat/${chatId}/typing`, (body) =>
      handler(JSON.parse(body) as TypingEvent),
    );
  }

  /** Broadcast that we started/stopped typing in a chat. */
  sendTyping(chatId: string, typing: boolean): void {
    if (this.client?.connected) {
      this.client.publish({ destination: '/app/chat.typing', body: JSON.stringify({ chatId, typing }) });
    }
  }

  /** Live task-change stream for a chat's board. Returns an unsubscribe function. */
  subscribeToTasks(chatId: string, handler: (event: TaskUpdateEvent) => void): () => void {
    return this.addSubscription(`/topic/tasks/${chatId}`, (body) =>
      handler(JSON.parse(body) as TaskUpdateEvent),
    );
  }

  /** Notify a chat's members that a task changed, so their boards refresh. */
  sendTaskUpdate(chatId: string, taskId: string, action: string): void {
    if (this.client?.connected) {
      this.client.publish({
        destination: '/app/task.update',
        body: JSON.stringify({ chatId, taskId, action }),
      });
    }
  }

  private addSubscription(destination: string, onBody: (body: string) => void): () => void {
    const id = ++this.counter;
    const entry: SubEntry = { destination, onBody };
    this.subs.set(id, entry);
    if (this.client?.connected) {
      entry.sub = this.client.subscribe(destination, (msg) => onBody(msg.body));
    }
    return () => {
      const e = this.subs.get(id);
      e?.sub?.unsubscribe();
      this.subs.delete(id);
    };
  }

  private resubscribeAll(): void {
    if (!this.client?.connected) return;
    for (const entry of this.subs.values()) {
      entry.sub = this.client.subscribe(entry.destination, (msg) => entry.onBody(msg.body));
    }
  }
}
