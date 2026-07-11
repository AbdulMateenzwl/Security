import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Chat, CreateChatRequest } from '../models/chat.models';

/** Talks to /api/chats — list and create chats (more operations added per feature). */
@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/chats`;

  list(): Observable<Chat[]> {
    return this.http.get<Chat[]>(this.baseUrl);
  }

  get(chatId: string): Observable<Chat> {
    return this.http.get<Chat>(`${this.baseUrl}/${chatId}`);
  }

  create(request: CreateChatRequest): Observable<Chat> {
    return this.http.post<Chat>(this.baseUrl, request);
  }
}
