import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { UserSummary } from '../models/auth.models';

/** Talks to /api/users — currently username search for starting chats. */
@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/users`;

  search(query: string): Observable<UserSummary[]> {
    const params = new HttpParams().set('q', query);
    return this.http.get<UserSummary[]>(`${this.baseUrl}/search`, { params });
  }

  /** A user's identity-key fingerprint (safety number), or null if they've published none. */
  fingerprint(userId: string): Observable<{ fingerprint: string | null }> {
    return this.http.get<{ fingerprint: string | null }>(`${this.baseUrl}/${userId}/fingerprint`);
  }
}
