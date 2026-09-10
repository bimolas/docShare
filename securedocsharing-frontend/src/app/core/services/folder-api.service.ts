import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  FolderResponseDto,
  FolderRequestDto,
  FolderContentResponseDto,
  FolderPeekResponseDto,
} from '../models/folder.model';

@Injectable({ providedIn: 'root' })
export class FolderApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/v1`;

  /**
   * Fetches personal subspaces (root folders for personal workspace).
   * Endpoint: GET /api/v1/personal/subspaces
   */
  getPersonalSubspaces(): Observable<FolderResponseDto[]> {
    return this.http.get<FolderResponseDto[]>(`${this.baseUrl}/personal/subspaces`);
  }

  /**
   * Fetches organization subspaces (root folders for an org workspace).
   * Endpoint: GET /api/v1/organizations/{orgCode}/subspaces
   */
  getOrganizationSubspaces(orgCode: string): Observable<FolderResponseDto[]> {
    return this.http.get<FolderResponseDto[]>(`${this.baseUrl}/organizations/${orgCode}/subspaces`);
  }

  /**
   * Creates a new personal subspace.
   * Endpoint: POST /api/v1/personal/subspaces
   */
  createPersonalSubspace(request: FolderRequestDto): Observable<FolderResponseDto> {
    return this.http.post<FolderResponseDto>(`${this.baseUrl}/personal/subspaces`, request);
  }

  /**
   * Creates a new organization subspace.
   * Endpoint: POST /api/v1/organizations/{orgCode}/subspaces
   */
  createOrganizationSubspace(orgCode: string | number, request: FolderRequestDto): Observable<FolderResponseDto> {
    return this.http.post<FolderResponseDto>(`${this.baseUrl}/organizations/${orgCode}/subspaces`, request);
  }

  /**
   * Fetches details of a specific folder by ID.
   * Endpoint: GET /api/v1/folders/{folderId}
   */
  getFolderById(folderId: number): Observable<FolderResponseDto> {
    return this.http.get<FolderResponseDto>(`${this.baseUrl}/folders/${folderId}`);
  }

  /**
   * Fetches contents (subfolders and documents) of a folder.
   * Endpoint: GET /api/v1/folders/{folderId}/contents
   */
  getFolderContents(folderId: number): Observable<FolderContentResponseDto> {
    return this.http.get<FolderContentResponseDto>(`${this.baseUrl}/folders/${folderId}/contents`);
  }

  /**
   * Gets quick peek summary for folder.
   * Endpoint: GET /api/v1/folders/{folderId}/peek
   */
  getFolderPeek(folderId: number): Observable<FolderPeekResponseDto> {
    return this.http.get<FolderPeekResponseDto>(`${this.baseUrl}/folders/${folderId}/peek`);
  }

  /**
   * Creates a new subfolder in an organization workspace.
   * Endpoint: POST /api/v1/organizations/{orgCode}/folders
   */
  createFolder(orgCode: string, request: FolderRequestDto): Observable<FolderResponseDto> {
    return this.http.post<FolderResponseDto>(`${this.baseUrl}/organizations/${orgCode}/folders`, request);
  }

  /**
   * Creates a new subfolder in personal workspace.
   * Endpoint: POST /api/v1/personal/folders
   */
  createPersonalFolder(request: FolderRequestDto): Observable<FolderResponseDto> {
    return this.http.post<FolderResponseDto>(`${this.baseUrl}/personal/folders`, request);
  }

  /**
   * Moves a folder to a new parent folder.
   * Endpoint: PATCH /api/v1/folders/{folderId}/move
   */
  moveFolder(folderId: number, targetFolderId: number | null): Observable<FolderResponseDto> {
    return this.http.patch<FolderResponseDto>(`${this.baseUrl}/folders/${folderId}/move`, { targetFolderId });
  }

  /**
   * Moves multiple folders to a new parent folder.
   * Endpoint: POST /api/v1/folders/bulk-move
   */
  bulkMoveFolders(ids: number[], targetFolderId: number | null): Observable<FolderResponseDto[]> {
    return this.http.post<FolderResponseDto[]>(`${this.baseUrl}/folders/bulk-move`, { ids, targetFolderId });
  }

  /**
   * Deletes a folder by ID.
   * Endpoint: DELETE /api/v1/folders/{folderId}
   */
  deleteFolder(folderId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/folders/${folderId}`);
  }

  /**
   * Renames a folder by ID.
   * Endpoint: PATCH /api/v1/folders/{folderId}
   */
  renameFolder(folderId: number, newName: string): Observable<FolderResponseDto> {
    return this.http.patch<FolderResponseDto>(`${this.baseUrl}/folders/${folderId}`, { name: newName });
  }

  /**
   * Downloads a folder and its contents as a ZIP archive.
   * Endpoint: GET /api/v1/folders/{folderId}/download
   */
  downloadFolder(folderId: number): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/folders/${folderId}/download`, { responseType: 'blob' });
  }
}
