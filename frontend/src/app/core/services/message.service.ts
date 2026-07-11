import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Message, SendMessageRequest } from '../models/message.models';

/** Talks to the message endpoints under /api/chats/{chatId}/messages. */
@Injectable({ providedIn: 'root' })
export class MessageService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/chats`;

  /**
   * Message history, newest-first (backend order). Pass the oldest id you hold as `before` to page
   * backwards.
   */
  history(chatId: string, before?: string, limit = 30): Observable<Message[]> {
    let params = new HttpParams().set('limit', limit);
    if (before) {
      params = params.set('before', before);
    }
    return this.http.get<Message[]>(`${this.baseUrl}/${chatId}/messages`, { params });
  }

  send(chatId: string, request: SendMessageRequest): Observable<Message> {
    return this.http.post<Message>(`${this.baseUrl}/${chatId}/messages`, request);
  }
}
