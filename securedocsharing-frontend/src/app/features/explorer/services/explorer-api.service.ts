import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  FolderContentsResponse,
  FolderMetadata,
  DocumentMetadata,
  DocumentVersion,
  CreateFolderRequest,
  MoveFolderRequest,
  FolderContentsRequest,
} from '../interfaces/explorer.interfaces';
import { PagedResponse } from '../../../core/models/api-response.model';

/**
 * FolderApiService: HTTP calls for folder operations.
 */
@Injectable({ providedIn: 'root' })
export class FolderApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/folders`;

  getFolderContents(request: FolderContentsRequest): Observable<FolderContentsResponse> {
    return this.http.get<FolderContentsResponse>(`${this.base}/contents`, { params: request as Record<string, string> });
  }

  getFolderById(id: string): Observable<FolderMetadata> {
    return this.http.get<FolderMetadata>(`${this.base}/${id}`);
  }

  createFolder(request: CreateFolderRequest): Observable<FolderMetadata> {
    return this.http.post<FolderMetadata>(this.base, request);
  }

  renameFolder(id: string, name: string): Observable<FolderMetadata> {
    return this.http.patch<FolderMetadata>(`${this.base}/${id}`, { name });
  }

  moveFolder(id: string, request: MoveFolderRequest): Observable<FolderMetadata> {
    return this.http.put<FolderMetadata>(`${this.base}/${id}/move`, request);
  }

  deleteFolder(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}

/**
 * DocumentApiService: HTTP calls for file/document operations.
 */
@Injectable({ providedIn: 'root' })
export class DocumentApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/documents`;

  getDocumentById(id: string): Observable<DocumentMetadata> {
    return this.http.get<DocumentMetadata>(`${this.base}/${id}`);
  }

  getDocumentVersions(id: string): Observable<DocumentVersion[]> {
    return this.http.get<DocumentVersion[]>(`${this.base}/${id}/versions`);
  }

  uploadDocument(folderId: string | null, spaceId: string, file: File): Observable<DocumentMetadata> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('spaceId', spaceId);
    if (folderId) formData.append('folderId', folderId);
    return this.http.post<DocumentMetadata>(`${this.base}/upload`, formData);
  }

  getDownloadUrl(id: string): Observable<{ url: string }> {
    return this.http.get<{ url: string }>(`${this.base}/${id}/download`);
  }

  deleteDocument(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  getDocuments(spaceId: string, page = 0, size = 20): Observable<PagedResponse<DocumentMetadata>> {
    return this.http.get<PagedResponse<DocumentMetadata>>(this.base, {
      params: { spaceId, page, size },
    });
  }
}
