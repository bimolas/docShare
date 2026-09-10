import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpEvent, HttpEventType, HttpHeaders, HttpRequest } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  InitiateUploadRequest,
  InitiateUploadResponse,
  ConfirmUploadRequest,
  DocumentResponse,
  DownloadUrlResponse,
  PreviewUrlResponse,
  DocumentVersionResponse,
  DocumentMoveRequestDto,
} from '../models/document.model';

@Injectable({ providedIn: 'root' })
export class DocumentApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/v1/documents`;

  /**
   * Initiates S3 direct upload workflow.
   * Endpoint: POST /api/v1/documents/upload/initiate
   */
  initiateUpload(request: InitiateUploadRequest): Observable<InitiateUploadResponse> {
    return this.http.post<InitiateUploadResponse>(`${this.baseUrl}/upload/initiate`, request);
  }

  /**
   * Direct HTTP PUT upload to presigned S3 URL with progress reporting.
   */
  uploadToS3(presignedUrl: string, file: File, contentType: string): Observable<HttpEvent<any>> {
    const headers = new HttpHeaders({
      'Content-Type': contentType,
    });
    const req = new HttpRequest('PUT', presignedUrl, file, {
      headers,
      reportProgress: true,
      responseType: 'text', // S3 returns XML/empty on success — NOT JSON
    });
    return this.http.request<any>(req);
  }

  /**
   * Confirms upload after S3 transfer is complete.
   * Endpoint: POST /api/v1/documents/{id}/upload/confirm
   */
  confirmUpload(documentId: number, request?: ConfirmUploadRequest): Observable<DocumentResponse> {
    return this.http.post<DocumentResponse>(
      `${this.baseUrl}/${documentId}/upload/confirm`,
      request || {}
    );
  }

  /**
   * Generates presigned download URL for a document.
   * Endpoint: GET /api/v1/documents/{id}/download
   */
  getDownloadUrl(documentId: number): Observable<DownloadUrlResponse> {
    return this.http.get<DownloadUrlResponse>(`${this.baseUrl}/${documentId}/download`);
  }

  /**
   * Generates presigned preview URL for a document.
   * Endpoint: GET /api/v1/documents/{id}/preview
   */
  getPreviewUrl(documentId: number): Observable<PreviewUrlResponse> {
    return this.http.get<PreviewUrlResponse>(`${this.baseUrl}/${documentId}/preview`);
  }

  /**
   * Gets document details by ID.
   * Endpoint: GET /api/v1/documents/{id}
   */
  getDocumentDetails(documentId: number): Observable<DocumentResponse> {
    return this.http.get<DocumentResponse>(`${this.baseUrl}/${documentId}`);
  }

  /**
   * Deletes a document.
   * Endpoint: DELETE /api/v1/documents/{id}
   */
  deleteDocument(documentId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${documentId}`);
  }

  /**
   * Moves a document to a target folder.
   * Endpoint: PATCH /api/v1/documents/{id}/move
   */
  moveDocument(documentId: number, targetFolderId: number | null): Observable<DocumentResponse> {
    const body: DocumentMoveRequestDto = { targetFolderId };
    return this.http.patch<DocumentResponse>(`${this.baseUrl}/${documentId}/move`, body);
  }

  /**
   * Moves multiple documents to a target folder.
   * Endpoint: POST /api/v1/documents/bulk-move
   */
  bulkMoveDocuments(ids: number[], targetFolderId: number | null): Observable<DocumentResponse[]> {
    return this.http.post<DocumentResponse[]>(`${this.baseUrl}/bulk-move`, { ids, targetFolderId });
  }

  /**
   * Gets version history for a document.
   * Endpoint: GET /api/v1/documents/{id}/versions
   */
  getDocumentVersions(documentId: number): Observable<DocumentVersionResponse[]> {
    return this.http.get<DocumentVersionResponse[]>(`${this.baseUrl}/${documentId}/versions`);
  }

  /**
   * Generates download URL for a specific version.
   * Endpoint: GET /api/v1/documents/{id}/versions/{versionNumber}/download
   */
  getVersionDownloadUrl(documentId: number, versionNumber: number): Observable<DownloadUrlResponse> {
    return this.http.get<DownloadUrlResponse>(
      `${this.baseUrl}/${documentId}/versions/${versionNumber}/download`
    );
  }

  /**
   * Updates document metadata (e.g. rename).
   * Endpoint: PATCH /api/v1/documents/{id}/metadata
   */
  updateMetadata(documentId: number, fileName: string, folderId?: number | null): Observable<DocumentResponse> {
    return this.http.patch<DocumentResponse>(`${this.baseUrl}/${documentId}/metadata`, { fileName, folderId });
  }
}
