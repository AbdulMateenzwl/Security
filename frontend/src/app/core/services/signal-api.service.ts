import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  IdentityKeyUploadRequest,
  PreKeyBundleDto,
  PreKeyCountResponse,
  PreKeyUploadRequest,
  SignedPreKeyDto,
} from '../models/signal.models';

/** Thin HTTP client for the Signal key-distribution endpoints (/api/signal). */
@Injectable({ providedIn: 'root' })
export class SignalApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/signal`;

  uploadIdentityKey(request: IdentityKeyUploadRequest): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/identity-key`, request);
  }

  uploadPreKeys(request: PreKeyUploadRequest): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/pre-keys`, request);
  }

  rotateSignedPreKey(request: SignedPreKeyDto): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/signed-pre-key`, request);
  }

  getPreKeyBundle(userId: string): Observable<PreKeyBundleDto> {
    return this.http.get<PreKeyBundleDto>(`${this.baseUrl}/pre-key-bundle/${userId}`);
  }

  getPreKeyCount(): Observable<PreKeyCountResponse> {
    return this.http.get<PreKeyCountResponse>(`${this.baseUrl}/pre-key-count`);
  }
}
