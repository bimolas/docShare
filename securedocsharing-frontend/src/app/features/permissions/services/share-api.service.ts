import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ShareLinkRequestDto, ShareLinkResponseDto, ShareLinkRevealResponseDto } from '../interfaces/permission.interfaces';

@Injectable({ providedIn: 'root' })
export class ShareApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/v1/shares`;

  /**
   * Creates a new ephemeral share link for a document or folder.
   * Endpoint: POST /api/v1/shares
   */
  createShareLink(request: ShareLinkRequestDto): Observable<ShareLinkResponseDto> {
    return this.http.post<ShareLinkResponseDto>(this.baseUrl, request);
  }

  /**
   * Reveals the bearer token and URL for an existing active share link.
   * Endpoint: POST /api/v1/shares/links/{permissionId}/reveal
   */
  revealShareLink(permissionId: number | string): Observable<ShareLinkRevealResponseDto> {
    return this.http.post<ShareLinkRevealResponseDto>(`${this.baseUrl}/links/${permissionId}/reveal`, {});
  }

  /**
   * Gets details for a share link token.
   * Endpoint: GET /api/v1/shares/{token}
   */
  getShareLinkInfo(token: string): Observable<ShareLinkResponseDto> {
    return this.http.get<ShareLinkResponseDto>(`${this.baseUrl}/${token}`);
  }

  /**
   * Revokes a share link.
   * Endpoint: DELETE /api/v1/shares/{token}
   */
  revokeShareLink(token: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${token}`);
  }
}
