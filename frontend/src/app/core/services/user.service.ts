import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { User } from '../models/auth.models';

/** Talks to /api/users — currently username search for starting chats. */
@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/users`;

  search(query: string): Observable<User[]> {
    const params = new HttpParams().set('q', query);
    return this.http.get<User[]>(`${this.baseUrl}/search`, { params });
  }
}
