import {
  Component, ChangeDetectionStrategy, input, output, signal, inject, effect, OnInit, OnDestroy, computed, HostListener
} from '@angular/core';

import { HttpEventType } from '@angular/common/http';
import { DocumentApiService } from '../../../core/services/document-api.service';
import { WorkspaceContextStore } from '../../../state/workspace/workspace-context.store';
import * as pdfjsLib from 'pdfjs-dist';

export interface FileUploadItem {
  file: File;
  id: string;
  name: string;
  size: number;
  type: string;
  hash?: string;
  progress: number;
  status: 'pending' | 'hashing' | 'initiating' | 'uploading' | 'confirming' | 'completed' | 'error';
  errorMessage?: string;
  previewUrl?: string;
}

@Component({
  selector: 'app-upload-file-modal',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (isOpen()) {
      <div class="modal-backdrop" (click)="onClose()">
        <div class="modal-card cloudy-border-surface" (click)="$event.stopPropagation()">
          
          <!-- Modal Header -->
          <div class="modal-header">
            <div class="header-title-group">
              <div class="upload-icon-circle">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                  <polyline points="17 8 12 3 7 8"></polyline>
                  <line x1="12" y1="3" x2="12" y2="15"></line>
                </svg>
              </div>
              <div>
                <div class="title-row">
                  <h3>Pre-Upload File Preview</h3>
                  @if (uploadList().length > 0) {
                    <span class="file-count-badge">
                      {{ uploadList().length }} file{{ uploadList().length > 1 ? 's' : '' }} • {{ formattedTotalSize() }}
                    </span>
                  }
                </div>
                <p class="subtitle">Review and verify your files in grid view before uploading to Vault</p>
              </div>
            </div>

            <div class="header-actions">
              @if (!isUploading() && uploadList().length > 0 && !allCompleted()) {
                <button class="btn-text-action" (click)="clearAllFiles()" title="Remove all files">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="3 6 5 6 21 6"></polyline>
                    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                  </svg>
                  <span>Clear All</span>
                </button>
              }
              <button class="close-btn" (click)="onClose()" [disabled]="isUploading()" title="Close">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18"></line>
                  <line x1="6" y1="6" x2="18" y2="18"></line>
                </svg>
              </button>
            </div>
          </div>

          <!-- Modal Body (Drag-and-Drop Surface) -->
          <div class="modal-body"
               (dragover)="onDragOver($event)"
               (dragleave)="onDragLeave($event)"
               (drop)="onDrop($event)"
               [class.drag-over]="isDragOver()">
            
            <input
              #fileInput
              type="file"
              multiple
              class="hidden-file-input"
              (change)="onFileSelected($event)"
            />

            <!-- Empty State / Drop Area when 0 files -->
            @if (uploadList().length === 0) {
              <div class="drop-zone-empty" (click)="fileInput.click()">
                <div class="cloud-icon">
                  <div class="aura-pulse"></div>
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                    <path d="M17.5 19C19.9853 19 22 16.9853 22 14.5C22 12.1564 20.206 10.2312 17.9001 10.0306C17.426 6.5928 14.4754 4 10.9 4C7.03401 4 3.9 7.13401 3.9 11C3.9 11.2359 3.91168 11.4691 3.93452 11.6987C2.26189 12.4419 1.1 14.084 1.1 16C1.1 18.4853 3.11472 20.5 5.6 20.5"></path>
                    <polyline points="12 12 12 18"></polyline>
                    <polyline points="9 15 12 12 15 15"></polyline>
                  </svg>
                </div>
                <p class="drop-primary-text">Drag and drop files here, or <span class="browse-link">browse your device</span></p>
                <p class="drop-secondary-text">Instant thumbnail preview generated for images, PDFs, Office docs, code, and media</p>
              </div>
            } @else {
              <!-- Rich Grid View of File Cards -->
              <div class="preview-grid">
                @for (item of uploadList(); track item.id) {
                  <div class="file-card" 
                       [class.state-hashing]="item.status === 'hashing'" 
                       [class.state-uploading]="item.status === 'uploading'" 
                       [class.state-completed]="item.status === 'completed'" 
                       [class.state-error]="item.status === 'error'">
                    
                    <!-- Quick Card Actions Overlay -->
                    <div class="card-action-bar">
                      <!-- Lightbox Inspect Button -->
                      @if (item.previewUrl || isPdf(item.type, item.name)) {
                        <button class="card-action-btn inspect-btn" (click)="openInspectLightbox(item); $event.stopPropagation()" title="Inspect file preview">
                          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                            <circle cx="12" cy="12" r="3"></circle>
                          </svg>
                        </button>
                      }

                      <!-- Remove File Button -->
                      @if (!isUploading() && item.status === 'pending') {
                        <button class="card-action-btn remove-btn" (click)="removeFile(item.id); $event.stopPropagation()" title="Remove file">
                          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                            <line x1="18" y1="6" x2="6" y2="18"></line>
                            <line x1="6" y1="6" x2="18" y2="18"></line>
                          </svg>
                        </button>
                      }
                    </div>

                    <!-- File Thumbnail / Preview Graphic Container -->
                    <div class="preview-container" (click)="onCardClick(item)">
                      @if (item.previewUrl) {
                        <!-- Real Image or Rendered PDF Canvas Thumbnail -->
                        <img [src]="item.previewUrl" class="preview-image" [alt]="item.name" />
                      } @else if (isPdf(item.type, item.name)) {
                        <!-- PDF Graphic Card (while rendering or fallback) -->
                        <div class="mock-preview mock-pdf">
                          <div class="mock-accent-bar"></div>
                          <svg class="mock-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.85">
                            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                            <polyline points="14 2 14 8 20 8"></polyline>
                          </svg>
                          <span class="mock-ext">PDF</span>
                        </div>
                      } @else if (isPpt(item.type, item.name)) {
                        <!-- PowerPoint Graphic Card -->
                        <div class="mock-preview mock-ppt">
                          <div class="mock-accent-bar"></div>
                          <svg class="mock-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.85">
                            <polygon points="12 2 2 7 12 12 22 7 12 2"></polygon>
                            <polyline points="2 17 12 22 22 17"></polyline>
                            <polyline points="2 12 12 17 22 12"></polyline>
                          </svg>
                          <span class="mock-ext">PPTX</span>
                        </div>
                      } @else if (isDoc(item.type, item.name)) {
                        <!-- Word Graphic Card -->
                        <div class="mock-preview mock-doc">
                          <div class="mock-accent-bar"></div>
                          <div class="mock-lines">
                            <div class="line short"></div>
                            <div class="line long"></div>
                            <div class="line medium"></div>
                          </div>
                          <svg class="mock-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.85">
                            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                            <polyline points="14 2 14 8 20 8"></polyline>
                            <line x1="16" y1="13" x2="8" y2="13"></line>
                          </svg>
                          <span class="mock-ext">{{ getExtension(item.name) || 'DOCX' }}</span>
                        </div>
                      } @else if (isExcel(item.type, item.name)) {
                        <!-- Excel Graphic Card -->
                        <div class="mock-preview mock-sheet">
                          <div class="mock-accent-bar"></div>
                          <div class="mock-grid">
                            <div class="grid-cell"></div><div class="grid-cell"></div><div class="grid-cell"></div>
                            <div class="grid-cell"></div><div class="grid-cell"></div><div class="grid-cell"></div>
                          </div>
                          <svg class="mock-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.85">
                            <rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect>
                            <line x1="9" y1="3" x2="9" y2="21"></line>
                            <line x1="3" y1="9" x2="21" y2="9"></line>
                          </svg>
                          <span class="mock-ext">{{ getExtension(item.name) || 'XLSX' }}</span>
                        </div>
                      } @else if (isCode(item.type, item.name)) {
                        <!-- Code / Data File Graphic Card -->
                        <div class="mock-preview mock-code">
                          <div class="mock-accent-bar"></div>
                          <svg class="mock-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.85">
                            <polyline points="16 18 22 12 16 6"></polyline>
                            <polyline points="8 6 2 12 8 18"></polyline>
                          </svg>
                          <span class="mock-ext">{{ getExtension(item.name) || 'CODE' }}</span>
                        </div>
                      } @else if (isMedia(item.type, item.name)) {
                        <!-- Media File Graphic Card -->
                        <div class="mock-preview mock-media">
                          <div class="mock-accent-bar"></div>
                          <svg class="mock-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.85">
                            <polygon points="5 3 19 12 5 21 5 3"></polygon>
                          </svg>
                          <span class="mock-ext">{{ getExtension(item.name) || 'MEDIA' }}</span>
                        </div>
                      } @else if (isArchive(item.type, item.name)) {
                        <!-- Archive Graphic Card -->
                        <div class="mock-preview mock-archive">
                          <div class="mock-accent-bar"></div>
                          <svg class="mock-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.85">
                            <path d="M21 8v13H3V8"></path>
                            <path d="M1 3h22v5H1z"></path>
                            <line x1="10" y1="12" x2="14" y2="12"></line>
                          </svg>
                          <span class="mock-ext">{{ getExtension(item.name) || 'ZIP' }}</span>
                        </div>
                      } @else {
                        <!-- Generic File Graphic Card -->
                        <div class="mock-preview mock-default">
                          <div class="mock-accent-bar"></div>
                          <svg class="mock-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.85">
                            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                            <polyline points="14 2 14 8 20 8"></polyline>
                          </svg>
                          <span class="mock-ext">{{ getExtension(item.name) || 'FILE' }}</span>
                        </div>
                      }

                      <!-- Active Card Upload Progress Overlay -->
                      @if (item.status === 'hashing' || item.status === 'initiating' || item.status === 'uploading' || item.status === 'confirming') {
                        <div class="card-progress-overlay">
                          <div class="progress-ring-mini">
                            <svg viewBox="0 0 36 36" class="ring-svg">
                              <circle class="ring-bg" cx="18" cy="18" r="15.91549430918954"></circle>
                              <circle class="ring-bar" cx="18" cy="18" r="15.91549430918954" [style.stroke-dashoffset]="100 - item.progress"></circle>
                            </svg>
                            <span class="progress-val">{{ item.progress }}%</span>
                          </div>
                          <span class="progress-phase">{{ getStatusText(item.status) }}</span>
                        </div>
                      }

                      <!-- Completed Overlay -->
                      @if (item.status === 'completed') {
                        <div class="card-completed-overlay">
                          <div class="checkmark-circle">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
                              <polyline points="20 6 9 17 4 12"></polyline>
                            </svg>
                          </div>
                          <span class="completed-label">Ready</span>
                        </div>
                      }

                      <!-- Error Overlay -->
                      @if (item.status === 'error') {
                        <div class="card-error-overlay">
                          <div class="error-circle">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
                              <line x1="18" y1="6" x2="6" y2="18"></line>
                              <line x1="6" y1="6" x2="18" y2="18"></line>
                            </svg>
                          </div>
                          <span class="error-label">Failed</span>
                          <span class="error-details" [title]="item.errorMessage || 'Upload failed'">
                            {{ item.errorMessage || 'Upload failed' }}
                          </span>
                        </div>
                      }
                    </div>

                    <!-- Footer Info Area -->
                    <div class="card-footer-info">
                      <span class="file-name-text" [title]="item.name">{{ item.name }}</span>
                      <div class="file-meta-row">
                        <span class="file-size-badge">{{ formatBytes(item.size) }}</span>
                        <span class="file-type-pill">{{ getFileTypeCategory(item.type, item.name) }}</span>
                      </div>
                    </div>
                  </div>
                }

                <!-- Add More Files Tile -->
                @if (!isUploading() && !allCompleted()) {
                  <div class="file-card add-more-card" (click)="fileInput.click()">
                    <div class="add-more-content">
                      <div class="add-plus-icon">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                          <line x1="12" y1="5" x2="12" y2="19"></line>
                          <line x1="5" y1="12" x2="19" y2="12"></line>
                        </svg>
                      </div>
                      <span class="add-more-title">Add Files</span>
                      <span class="add-more-sub">Drop or click</span>
                    </div>
                  </div>
                }
              </div>
            }
          </div>

          <!-- Modal Footer Bar -->
          <div class="modal-footer">
            @if (allCompleted()) {
              <button class="btn btn-secondary btn-upload-more" (click)="clearCompletedAndKeepModalOpen()">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="12" y1="5" x2="12" y2="19"></line>
                  <line x1="5" y1="12" x2="19" y2="12"></line>
                </svg>
                <span>Upload More Files</span>
              </button>
              <button class="btn btn-primary" (click)="onClose()">
                Done
              </button>
            } @else {
              <button class="btn btn-secondary" (click)="onClose()" [disabled]="isUploading()">
                Cancel
              </button>
              <button
                class="btn btn-primary btn-confirm-upload"
                (click)="startUploads()"
                [disabled]="isUploading() || uploadList().length === 0"
              >
                @if (isUploading()) {
                  <div class="custom-portal-spinner">
                    <div class="ring outer"></div>
                    <div class="ring middle"></div>
                    <div class="ring inner"></div>
                  </div>
                  <span>Uploading {{ uploadList().length }} file{{ uploadList().length > 1 ? 's' : '' }}...</span>
                } @else {
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                    <polyline points="17 8 12 3 7 8"></polyline>
                    <line x1="12" y1="3" x2="12" y2="15"></line>
                  </svg>
                  <span>Confirm Upload ({{ uploadList().length }})</span>
                }
              </button>
            }
          </div>
        </div>

        <!-- Inline File Inspection Lightbox Popup -->
        @if (inspectedFile(); as fileItem) {
          <div class="lightbox-overlay" (click)="closeInspectLightbox()">
            <div class="lightbox-dialog" (click)="$event.stopPropagation()">
              <div class="lightbox-header">
                <div class="lh-info">
                  <span class="lh-filename" [title]="fileItem.name">{{ fileItem.name }}</span>
                  <span class="lh-meta">{{ formatBytes(fileItem.size) }} • {{ fileItem.type || 'Unknown Type' }}</span>
                </div>
                <button class="lh-close-btn" (click)="closeInspectLightbox()" title="Close Inspection">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <line x1="18" y1="6" x2="6" y2="18"></line>
                    <line x1="6" y1="6" x2="18" y2="18"></line>
                  </svg>
                </button>
              </div>

              <div class="lightbox-preview-area">
                @if (fileItem.previewUrl) {
                  <img [src]="fileItem.previewUrl" class="lh-full-image" [alt]="fileItem.name" />
                } @else {
                  <div class="lh-document-placeholder">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                      <polyline points="14 2 14 8 20 8"></polyline>
                    </svg>
                    <span>Full document preview generated upon upload completion</span>
                  </div>
                }
              </div>

              <div class="lightbox-footer">
                <button class="btn btn-secondary" (click)="closeInspectLightbox()">Close</button>
                @if (!isUploading() && fileItem.status === 'pending') {
                  <button class="btn btn-danger" (click)="removeFile(fileItem.id); closeInspectLightbox()">
                    Remove File
                  </button>
                }
              </div>
            </div>
          </div>
        }
      </div>
    }
  `,
  styles: [`
    .modal-backdrop {
      position: fixed;
      inset: 0;
      background-color: rgba(15, 23, 42, 0.55);
      backdrop-filter: blur(12px);
      -webkit-backdrop-filter: blur(12px);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 9999;
      padding: 1rem;
      animation: fadeIn 0.25s cubic-bezier(0.16, 1, 0.3, 1);
    }
    @keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }

    .modal-card {
      background: #ffffff;
      border-radius: 1.5rem;
      box-shadow: 0 30px 60px -12px rgba(15, 23, 42, 0.25), 0 0 40px rgba(255, 85, 0, 0.08);
      width: 100%;
      max-width: 760px;
      overflow: hidden;
      display: flex;
      flex-direction: column;
      max-height: 88vh;
      animation: scaleUp 0.32s cubic-bezier(0.34, 1.56, 0.64, 1);
      border: 1px solid rgba(226, 232, 240, 0.9);
      position: relative;
    }
    @keyframes scaleUp { from { transform: scale(0.92); opacity: 0; } to { transform: scale(1); opacity: 1; } }

    /* Modal Header */
    .modal-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 1.25rem 1.75rem;
      border-bottom: 1px solid #f1f5f9;
      background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
    }
    .header-title-group {
      display: flex;
      align-items: center;
      gap: 1rem;
    }
    .upload-icon-circle {
      width: 2.875rem;
      height: 2.875rem;
      border-radius: 0.875rem;
      background: linear-gradient(135deg, #fff7ed 0%, #ffedd5 100%);
      color: #ff5500;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 4px 12px rgba(255, 85, 0, 0.12);
      flex-shrink: 0;
    }
    .upload-icon-circle svg {
      width: 1.5rem;
      height: 1.5rem;
    }
    .title-row {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }
    .modal-header h3 {
      font-size: 1.15rem;
      font-weight: 800;
      color: #0f172a;
      margin: 0;
      letter-spacing: -0.02em;
    }
    .file-count-badge {
      font-size: 0.725rem;
      font-weight: 700;
      color: #ff5500;
      background: #fff7ed;
      border: 1px solid rgba(255, 85, 0, 0.2);
      padding: 0.15rem 0.5rem;
      border-radius: 9999px;
    }
    .subtitle {
      font-size: 0.78125rem;
      color: #64748b;
      margin-top: 0.2rem;
      font-weight: 500;
    }

    .header-actions {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }
    .btn-text-action {
      background: none;
      border: none;
      color: #ef4444;
      font-size: 0.78125rem;
      font-weight: 700;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 0.35rem;
      padding: 0.4rem 0.65rem;
      border-radius: 0.5rem;
      transition: all 0.2s ease;
    }
    .btn-text-action:hover {
      background-color: #fee2e2;
    }
    .btn-text-action svg {
      width: 0.9375rem;
      height: 0.9375rem;
    }

    .close-btn {
      background: none;
      border: none;
      color: #94a3b8;
      cursor: pointer;
      padding: 0.5rem;
      border-radius: 50%;
      transition: all 0.2s;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .close-btn:hover:not(:disabled) {
      background-color: #f1f5f9;
      color: #0f172a;
      transform: rotate(90deg);
    }
    .close-btn:disabled { opacity: 0.35; cursor: not-allowed; }
    .close-btn svg { width: 1.25rem; height: 1.25rem; }

    /* Modal Body */
    .modal-body {
      padding: 1.5rem;
      overflow-y: auto;
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
      min-height: 280px;
      transition: background-color 0.2s ease;
      background: #fdfdfe;
    }
    .modal-body.drag-over {
      background-color: #fff7ed;
    }

    /* Empty Drop Zone */
    .drop-zone-empty {
      border: 2.5px dashed #cbd5e1;
      border-radius: 1.25rem;
      padding: 4rem 2rem;
      text-align: center;
      background-color: #f8fafc;
      cursor: pointer;
      transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      position: relative;
      overflow: hidden;
    }
    .drop-zone-empty:hover {
      border-color: #ff5500;
      background-color: #fff7ed;
      transform: scale(0.992);
      box-shadow: 0 12px 24px -6px rgba(255, 85, 0, 0.08);
    }
    .hidden-file-input { display: none; }
    
    .cloud-icon {
      position: relative;
      color: #ff5500;
      margin-bottom: 1.25rem;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .aura-pulse {
      position: absolute;
      width: 4.5rem;
      height: 4.5rem;
      border-radius: 50%;
      background: rgba(255, 85, 0, 0.15);
      animation: pulse-aura 2.2s infinite cubic-bezier(0.4, 0, 0.6, 1);
    }
    @keyframes pulse-aura {
      0% { transform: scale(0.8); opacity: 0.8; }
      100% { transform: scale(1.6); opacity: 0; }
    }
    .cloud-icon svg { width: 3.5rem; height: 3.5rem; position: relative; z-index: 2; }
    
    .drop-primary-text {
      font-size: 1.05rem;
      font-weight: 700;
      color: #1e293b;
    }
    .browse-link { color: #ff5500; text-decoration: underline; font-weight: 800; }
    .drop-secondary-text {
      font-size: 0.8125rem;
      color: #64748b;
      margin-top: 0.4rem;
      font-weight: 500;
    }

    /* File Grid */
    .preview-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(145px, 1fr));
      gap: 1.25rem;
      padding: 0.25rem;
      max-height: 480px;
      overflow-y: auto;
    }

    /* Individual File Card */
    .file-card {
      position: relative;
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 0.875rem;
      overflow: hidden;
      display: flex;
      flex-direction: column;
      box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.03), 0 2px 4px -1px rgba(0, 0, 0, 0.02);
      transition: all 0.28s cubic-bezier(0.4, 0, 0.2, 1);
      aspect-ratio: 0.9;
    }
    .file-card:hover {
      transform: translateY(-4px);
      box-shadow: 0 12px 24px -4px rgba(255, 85, 0, 0.12), 0 4px 8px -2px rgba(0, 0, 0, 0.04);
      border-color: rgba(255, 85, 0, 0.35);
    }
    
    .file-card.state-hashing {
      animation: pulse-border-orange 1.5s infinite alternate;
      border-color: #ea580c;
    }
    .file-card.state-uploading {
      border-color: #ff5500;
    }
    .file-card.state-completed {
      border-color: #22c55e;
    }
    .file-card.state-error {
      border-color: #ef4444;
    }

    @keyframes pulse-border-orange {
      from { box-shadow: 0 0 0 1px rgba(234, 88, 12, 0.2); }
      to { box-shadow: 0 0 0 4px rgba(234, 88, 12, 0.45); }
    }

    /* Action bar on card hover */
    .card-action-bar {
      position: absolute;
      top: 0.5rem;
      left: 0.5rem;
      right: 0.5rem;
      display: flex;
      align-items: center;
      justify-content: space-between;
      z-index: 10;
      pointer-events: none;
    }
    .card-action-btn {
      pointer-events: auto;
      background: rgba(15, 23, 42, 0.72);
      backdrop-filter: blur(4px);
      border: none;
      color: #ffffff;
      width: 1.75rem;
      height: 1.75rem;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      opacity: 0;
      transition: all 0.2s ease;
    }
    .file-card:hover .card-action-btn {
      opacity: 1;
    }
    .remove-btn:hover {
      background: #ef4444;
      transform: scale(1.1);
    }
    .inspect-btn:hover {
      background: #ff5500;
      transform: scale(1.1);
    }
    .card-action-btn svg {
      width: 0.875rem;
      height: 0.875rem;
    }

    /* Thumbnail Preview Surface */
    .preview-container {
      position: relative;
      width: 100%;
      height: 68%;
      background-color: #f8fafc;
      overflow: hidden;
      border-bottom: 1px solid #f1f5f9;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
    }
    .preview-image {
      width: 100%;
      height: 100%;
      object-fit: cover;
      transition: transform 0.35s ease;
    }
    .file-card:hover .preview-image {
      transform: scale(1.06);
    }

    /* Graphic Mock Previews for Non-Image Files */
    .mock-preview {
      position: absolute;
      inset: 0;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      overflow: hidden;
      user-select: none;
    }
    .mock-accent-bar {
      position: absolute;
      top: 0; left: 0; right: 0; height: 5px;
      background: currentColor;
    }
    .mock-pdf { background: linear-gradient(135deg, #fff5f5 0%, #fee2e2 100%); color: #dc2626; }
    .mock-ppt { background: linear-gradient(135deg, #fff7ed 0%, #ffedd5 100%); color: #ea580c; }
    .mock-doc { background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%); color: #2563eb; }
    .mock-sheet { background: linear-gradient(135deg, #f0fdf4 0%, #dcfce7 100%); color: #16a34a; }
    .mock-code { background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%); color: #0284c7; }
    .mock-media { background: linear-gradient(135deg, #faf5ff 0%, #f3e8ff 100%); color: #9333ea; }
    .mock-archive { background: linear-gradient(135deg, #fffbe3 0%, #fef08a 100%); color: #ca8a04; }
    .mock-default { background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%); color: #64748b; }
    
    .mock-icon {
      width: 2.35rem;
      height: 2.35rem;
      margin-bottom: 0.375rem;
      z-index: 2;
      filter: drop-shadow(0 2px 4px rgba(0,0,0,0.05));
    }
    .mock-ext {
      font-size: 0.625rem;
      font-weight: 900;
      letter-spacing: 0.08em;
      z-index: 2;
      text-transform: uppercase;
      background: rgba(255, 255, 255, 0.9);
      padding: 0.125rem 0.5rem;
      border-radius: 9999px;
      box-shadow: 0 1px 3px rgba(0,0,0,0.06);
    }
    .mock-lines {
      position: absolute;
      inset: 0.875rem;
      display: flex;
      flex-direction: column;
      gap: 0.3125rem;
      opacity: 0.14;
    }
    .mock-lines .line { height: 3px; background: currentColor; border-radius: 1.5px; }
    .mock-lines .line.short { width: 35%; }
    .mock-lines .line.medium { width: 65%; }
    .mock-lines .line.long { width: 85%; }

    .mock-grid {
      position: absolute;
      inset: 0;
      display: grid;
      grid-template-columns: 1fr 1fr 1fr;
      grid-template-rows: 1fr 1fr 1fr;
      gap: 1px;
      background: rgba(22, 163, 74, 0.1);
      opacity: 0.15;
    }
    .grid-cell { background: #ffffff; }

    /* Progress Overlay */
    .card-progress-overlay {
      position: absolute;
      inset: 0;
      background: rgba(15, 23, 42, 0.82);
      backdrop-filter: blur(3px);
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      color: #ffffff;
      z-index: 5;
      gap: 0.5rem;
    }
    .progress-ring-mini {
      position: relative;
      width: 3.25rem;
      height: 3.25rem;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .ring-svg { width: 100%; height: 100%; transform: rotate(-90deg); }
    .ring-bg { fill: none; stroke: rgba(255, 255, 255, 0.2); stroke-width: 3.2; }
    .ring-bar {
      fill: none;
      stroke: #ff5500;
      stroke-width: 3.2;
      stroke-linecap: round;
      stroke-dasharray: 100;
      stroke-dashoffset: 100;
      transition: stroke-dashoffset 0.2s ease;
    }
    .progress-val { position: absolute; font-size: 0.725rem; font-weight: 800; letter-spacing: -0.02em; }
    .progress-phase {
      font-size: 0.625rem;
      font-weight: 800;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      color: rgba(255, 255, 255, 0.85);
      text-align: center;
      max-width: 90%;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .card-completed-overlay {
      position: absolute;
      inset: 0;
      background: rgba(240, 253, 244, 0.95);
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      color: #16a34a;
      z-index: 5;
      gap: 0.375rem;
      animation: scaleInOverlay 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
    }
    @keyframes scaleInOverlay {
      from { transform: scale(0.92); opacity: 0; }
      to { transform: scale(1); opacity: 1; }
    }
    .checkmark-circle {
      width: 2.25rem;
      height: 2.25rem;
      border-radius: 50%;
      background: #dcfce7;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 4px 8px -2px rgba(22, 163, 74, 0.15);
    }
    .checkmark-circle svg { width: 1.125rem; height: 1.125rem; }
    .completed-label { font-size: 0.7rem; font-weight: 800; text-transform: uppercase; letter-spacing: 0.06em; }

    .card-error-overlay {
      position: absolute;
      inset: 0;
      background: rgba(255, 242, 242, 0.96);
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      color: #dc2626;
      z-index: 5;
      gap: 0.25rem;
      padding: 0.5rem;
      text-align: center;
      animation: scaleInOverlay 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
    }
    .error-circle {
      width: 2rem;
      height: 2rem;
      border-radius: 50%;
      background: #fee2e2;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .error-circle svg { width: 1rem; height: 1rem; }
    .error-label { font-size: 0.7rem; font-weight: 800; text-transform: uppercase; letter-spacing: 0.06em; }
    .error-details {
      font-size: 0.625rem;
      color: #991b1b;
      overflow: hidden;
      text-overflow: ellipsis;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      line-height: 1.2;
      font-weight: 500;
      max-width: 95%;
    }

    /* Footer Info Section of Card */
    .card-footer-info {
      padding: 0.5rem 0.65rem;
      display: flex;
      flex-direction: column;
      justify-content: center;
      gap: 0.2rem;
      background: #ffffff;
      flex: 1;
      box-sizing: border-box;
    }
    .file-name-text {
      font-size: 0.75rem;
      font-weight: 700;
      color: #1e293b;
      display: block;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .file-meta-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .file-size-badge {
      font-size: 0.6875rem;
      color: #64748b;
      font-weight: 600;
    }
    .file-type-pill {
      font-size: 0.5875rem;
      font-weight: 800;
      color: #ff5500;
      background: #fff7ed;
      padding: 0.05rem 0.35rem;
      border-radius: 0.25rem;
      text-transform: uppercase;
    }

    /* Add More Tile */
    .add-more-card {
      border: 2px dashed #cbd5e1;
      background: #f8fafc;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      aspect-ratio: 0.9;
      transition: all 0.25s ease;
    }
    .add-more-card:hover {
      border-color: #ff5500;
      background: #fff7ed;
      transform: translateY(-2px);
    }
    .add-more-content {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.35rem;
      color: #64748b;
    }
    .add-more-card:hover .add-more-content { color: #ff5500; }
    .add-plus-icon {
      width: 2.25rem;
      height: 2.25rem;
      border-radius: 50%;
      background: #f1f5f9;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s ease;
    }
    .add-more-card:hover .add-plus-icon { background: #ffedd5; transform: scale(1.1); }
    .add-plus-icon svg { width: 1.25rem; height: 1.25rem; }
    .add-more-title { font-size: 0.8125rem; font-weight: 700; }
    .add-more-sub { font-size: 0.6875rem; opacity: 0.7; }

    /* Footer Controls */
    .modal-footer {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 0.75rem;
      padding: 1.15rem 1.75rem;
      border-top: 1px solid #f1f5f9;
      background-color: #f8fafc;
    }
    .btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      padding: 0.65rem 1.35rem;
      font-size: 0.875rem;
      font-weight: 700;
      border-radius: 0.75rem;
      cursor: pointer;
      border: none;
      transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
    }
    .btn svg { width: 1.125rem; height: 1.125rem; }
    .btn-secondary {
      background-color: #ffffff;
      color: #475569;
      border: 1px solid #cbd5e1;
    }
    .btn-secondary:hover:not(:disabled) {
      background-color: #f8fafc;
      border-color: #94a3b8;
    }
    .btn-primary {
      background: linear-gradient(135deg, #ff5500 0%, #e04b00 100%);
      color: #ffffff;
      box-shadow: 0 4px 14px rgba(255, 85, 0, 0.25);
    }
    .btn-primary:hover:not(:disabled) {
      background: linear-gradient(135deg, #ff661a 0%, #cc4400 100%);
      box-shadow: 0 6px 18px rgba(255, 85, 0, 0.35);
      transform: translateY(-1px);
    }
    .btn-danger {
      background-color: #ef4444;
      color: #ffffff;
    }
    .btn-danger:hover {
      background-color: #dc2626;
    }
    .btn:disabled { opacity: 0.55; cursor: not-allowed; transform: none !important; box-shadow: none !important; }

    /* Custom Spinner */
    .custom-portal-spinner {
      position: relative;
      width: 1.25rem;
      height: 1.25rem;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .custom-portal-spinner .ring {
      position: absolute;
      border: 2px solid transparent;
      border-radius: 50%;
    }
    .custom-portal-spinner .ring.outer {
      width: 1.25rem; height: 1.25rem;
      border-top-color: #ffffff;
      border-bottom-color: rgba(255, 255, 255, 0.2);
      animation: spin-cw 1s linear infinite;
    }
    .custom-portal-spinner .ring.middle {
      width: 0.9375rem; height: 0.9375rem;
      border-left-color: #fdba74;
      border-right-color: rgba(253, 186, 116, 0.2);
      animation: spin-ccw 0.75s linear infinite;
    }
    .custom-portal-spinner .ring.inner {
      width: 0.625rem; height: 0.625rem;
      border-top-color: #ffffff;
      animation: spin-cw 0.5s linear infinite;
    }

    @keyframes spin-cw { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
    @keyframes spin-ccw { from { transform: rotate(360deg); } to { transform: rotate(0deg); } }

    /* Lightbox Inspection Dialog */
    .lightbox-overlay {
      position: fixed;
      inset: 0;
      background: rgba(15, 23, 42, 0.85);
      backdrop-filter: blur(8px);
      z-index: 10000;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 1.5rem;
      animation: fadeIn 0.2s ease;
    }
    .lightbox-dialog {
      background: #ffffff;
      border-radius: 1.25rem;
      max-width: 640px;
      width: 100%;
      max-height: 85vh;
      display: flex;
      flex-direction: column;
      overflow: hidden;
      box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
      animation: scaleUp 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
    }
    .lightbox-header {
      padding: 1rem 1.25rem;
      display: flex;
      align-items: center;
      justify-content: space-between;
      border-bottom: 1px solid #f1f5f9;
    }
    .lh-info { display: flex; flex-direction: column; gap: 0.1rem; overflow: hidden; }
    .lh-filename { font-size: 0.9375rem; font-weight: 800; color: #0f172a; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .lh-meta { font-size: 0.75rem; color: #64748b; font-weight: 500; }
    .lh-close-btn {
      background: none; border: none; color: #64748b; cursor: pointer; padding: 0.4rem; border-radius: 50%; display: flex; align-items: center; justify-content: center;
    }
    .lh-close-btn:hover { background: #f1f5f9; color: #0f172a; }
    .lh-close-btn svg { width: 1.25rem; height: 1.25rem; }

    .lightbox-preview-area {
      flex: 1;
      padding: 1rem;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #0f172a;
      min-height: 280px;
      max-height: 500px;
      overflow: hidden;
    }
    .lh-full-image {
      max-width: 100%;
      max-height: 480px;
      object-fit: contain;
      border-radius: 0.5rem;
    }
    .lh-document-placeholder {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 1rem;
      color: #94a3b8;
      font-size: 0.875rem;
      text-align: center;
      padding: 2rem;
    }
    .lh-document-placeholder svg { width: 4rem; height: 4rem; stroke-width: 1.2; }

    .lightbox-footer {
      padding: 1rem 1.25rem;
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 0.75rem;
      border-top: 1px solid #f1f5f9;
      background: #f8fafc;
    }
  `],
})
export class UploadFileModalComponent implements OnInit, OnDestroy {
  private readonly documentApiService = inject(DocumentApiService);
  private readonly workspaceContextStore = inject(WorkspaceContextStore);

  isOpen = input<boolean>(false);
  initialFiles = input<File[]>([]);
  currentFolderId = input<number | null>(null);

  closed = output<void>();
  uploadCompleted = output<void>();

  isDragOver = signal<boolean>(false);
  uploadList = signal<FileUploadItem[]>([]);
  isUploading = signal<boolean>(false);
  inspectedFile = signal<FileUploadItem | null>(null);

  totalSizeBytes = computed(() => {
    return this.uploadList().reduce((acc, curr) => acc + curr.size, 0);
  });

  formattedTotalSize = computed(() => {
    return this.formatBytes(this.totalSizeBytes());
  });

  constructor() {
    effect(() => {
      if (this.isOpen()) {
        const initial = this.initialFiles();
        if (initial && initial.length > 0) {
          this.addFiles(initial);
        } else {
          this.uploadList.set([]);
        }
        this.isUploading.set(false);
        this.isDragOver.set(false);
        this.inspectedFile.set(null);
      }
    });
  }

  ngOnInit(): void {
    this.initPdfWorker();
  }

  ngOnDestroy(): void {
    this.cleanupPreviews();
  }

  @HostListener('window:keydown.escape')
  handleEscape(): void {
    if (this.inspectedFile()) {
      this.closeInspectLightbox();
    } else if (this.isOpen() && !this.isUploading()) {
      this.onClose();
    }
  }

  private initPdfWorker(): void {
    try {
      const pdfjs = pdfjsLib as any;
      if (pdfjs && pdfjs.GlobalWorkerOptions) {
        const ver = pdfjs.version || '6.2.108';
        pdfjs.GlobalWorkerOptions.workerSrc = `https://unpkg.com/pdfjs-dist@${ver}/build/pdf.worker.min.mjs`;
      }
    } catch (e) {
      console.warn('Could not initialize PDF.js worker:', e);
    }
  }

  private cleanupPreviews(): void {
    this.uploadList().forEach(item => {
      if (item.previewUrl && item.previewUrl.startsWith('blob:')) {
        URL.revokeObjectURL(item.previewUrl);
      }
    });
  }

  onClose(): void {
    if (!this.isUploading()) {
      this.cleanupPreviews();
      this.closed.emit();
    }
  }

  onDragOver(e: DragEvent): void {
    e.preventDefault();
    e.stopPropagation();
    this.isDragOver.set(true);
  }

  onDragLeave(e: DragEvent): void {
    e.preventDefault();
    e.stopPropagation();
    this.isDragOver.set(false);
  }

  onDrop(e: DragEvent): void {
    e.preventDefault();
    e.stopPropagation();
    this.isDragOver.set(false);

    if (e.dataTransfer?.files && e.dataTransfer.files.length > 0) {
      this.addFiles(Array.from(e.dataTransfer.files));
    }
  }

  onFileSelected(e: Event): void {
    const input = e.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.addFiles(Array.from(input.files));
      input.value = '';
    }
  }

  public async addFiles(files: File[]): Promise<void> {
    const initialItems = files.map((file) => {
      const id = Math.random().toString(36).substring(2, 9);
      return {
        file,
        id,
        name: file.name,
        size: file.size,
        type: file.type || 'application/octet-stream',
        progress: 0,
        status: 'pending' as const,
        previewUrl: ''
      };
    });

    this.uploadList.update((curr) => [...curr, ...initialItems]);

    // Generate previews asynchronously in background
    for (const item of initialItems) {
      if (item.file.type.startsWith('image/')) {
        const previewUrl = URL.createObjectURL(item.file);
        this.updateItem(item.id, { previewUrl });
      } else if (item.file.type === 'application/pdf' || item.file.name.toLowerCase().endsWith('.pdf')) {
        try {
          const previewUrl = await this.generatePdfThumbnail(item.file);
          this.updateItem(item.id, { previewUrl });
        } catch (e) {
          console.warn('PDF thumbnail failed', e);
        }
      }
    }
  }

  private async generatePdfThumbnail(file: File): Promise<string> {
    try {
      const arrayBuffer = await file.arrayBuffer();
      const loadingTask = pdfjsLib.getDocument({ data: arrayBuffer });
      const pdf = await loadingTask.promise;
      const page = await pdf.getPage(1);
      
      const viewport = page.getViewport({ scale: 0.35 });
      const canvas = document.createElement('canvas');
      const context = canvas.getContext('2d');
      if (!context) throw new Error('2D context failed');
      
      canvas.width = viewport.width;
      canvas.height = viewport.height;
      
      await page.render({
        canvasContext: context,
        viewport: viewport,
        canvas: canvas
      }).promise;
      
      return canvas.toDataURL('image/jpeg', 0.85);
    } catch (e) {
      console.warn('PDF rendering error:', e);
      throw e;
    }
  }

  removeFile(id: string): void {
    const item = this.uploadList().find(it => it.id === id);
    if (item && item.previewUrl && item.previewUrl.startsWith('blob:')) {
      URL.revokeObjectURL(item.previewUrl);
    }
    this.uploadList.update((curr) => curr.filter((it) => it.id !== id));
    if (this.inspectedFile()?.id === id) {
      this.inspectedFile.set(null);
    }
  }

  clearAllFiles(): void {
    this.cleanupPreviews();
    this.uploadList.set([]);
    this.inspectedFile.set(null);
  }

  clearCompletedAndKeepModalOpen(): void {
    this.cleanupPreviews();
    this.uploadList.set([]);
    this.isUploading.set(false);
    this.inspectedFile.set(null);
  }

  openInspectLightbox(item: FileUploadItem): void {
    this.inspectedFile.set(item);
  }

  closeInspectLightbox(): void {
    this.inspectedFile.set(null);
  }

  onCardClick(item: FileUploadItem): void {
    if (item.previewUrl || this.isPdf(item.type, item.name)) {
      this.openInspectLightbox(item);
    }
  }

  async startUploads(): Promise<void> {
    if (this.isUploading() || this.uploadList().length === 0) return;
    this.isUploading.set(true);

    const activeWs = this.workspaceContextStore.activeWorkspace();
    const folderId = this.currentFolderId();
    const orgCode = activeWs?.type === 'ORGANIZATION' ? activeWs.orgCode : null;

    const list = [...this.uploadList()];

    for (let i = 0; i < list.length; i++) {
      const item = list[i];
      if (item.status === 'completed') continue;

      try {
        // Step 1: Hashing
        this.updateItem(item.id, { status: 'hashing', progress: 10 });
        const hash = await this.calculateSha256(item.file);
        this.updateItem(item.id, { hash, progress: 25 });

        // Step 2: Initiate Upload
        this.updateItem(item.id, { status: 'initiating', progress: 35 });
        const initRes = await this.documentApiService.initiateUpload({
          fileName: item.name,
          contentType: item.type,
          fileSize: item.size,
          fileHash: hash,
          folderId: folderId,
          orgCode: orgCode,
        }).toPromise();

        if (!initRes || !initRes.presignedUploadUrl) {
          throw new Error('Failed to obtain presigned upload URL.');
        }

        // Step 3: Direct PUT to S3
        this.updateItem(item.id, { status: 'uploading', progress: 45 });
        await new Promise<void>((resolve, reject) => {
          this.documentApiService.uploadToS3(initRes.presignedUploadUrl, item.file, item.type).subscribe({
            next: (event) => {
              if (event.type === HttpEventType.UploadProgress && event.total) {
                const percent = Math.round(45 + (event.loaded / event.total) * 45);
                this.updateItem(item.id, { progress: percent });
              }
            },
            error: (err) => reject(err),
            complete: () => resolve(),
          });
        });

        // Step 4: Confirm Upload
        this.updateItem(item.id, { status: 'confirming', progress: 95 });
        await this.documentApiService.confirmUpload(initRes.documentId, {
          clientHash: hash,
          objectKey: initRes.objectKey,
          fileName: item.name,
          contentType: item.type,
          fileSize: item.size,
        }).toPromise();

        this.updateItem(item.id, { status: 'completed', progress: 100 });
      } catch (err: any) {
        console.error(`Upload error for file ${item.name}:`, err);
        this.updateItem(item.id, {
          status: 'error',
          errorMessage: this.extractErrorMessage(err),
        });
      }
    }

    this.isUploading.set(false);
    this.uploadCompleted.emit();
  }

  private updateItem(id: string, patch: Partial<FileUploadItem>): void {
    this.uploadList.update((items) =>
      items.map((it) => (it.id === id ? { ...it, ...patch } : it))
    );
  }

  private extractErrorMessage(err: any): string {
    if (err?.error && typeof err.error === 'object' && !(err.error instanceof SyntaxError) && err.error.message) {
      return err.error.message;
    }
    if (err?.error instanceof SyntaxError || (err?.message && err.message.startsWith('Http failure during parsing'))) {
      if (err?.status === 0) return 'Network error — check your connection.';
      if (err?.status >= 500) return 'Server error — please try again later.';
      if (err?.status === 403) return 'Access denied.';
      if (err?.status === 404) return 'Resource not found.';
      return `Upload failed (HTTP ${err?.status ?? 'unknown'}).`;
    }
    if (err?.message) return err.message;
    return 'Upload failed. Please try again.';
  }

  private async calculateSha256(file: File): Promise<string> {
    const arrayBuffer = await file.arrayBuffer();
    const hashBuffer = await crypto.subtle.digest('SHA-256', arrayBuffer);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    return hashArray.map((b) => b.toString(16).padStart(2, '0')).join('');
  }

  allCompleted(): boolean {
    const list = this.uploadList();
    return list.length > 0 && list.every((it) => it.status === 'completed');
  }

  getStatusText(status: FileUploadItem['status']): string {
    switch (status) {
      case 'pending': return 'Pending';
      case 'hashing': return 'Hashing...';
      case 'initiating': return 'Initiating...';
      case 'uploading': return 'Uploading...';
      case 'confirming': return 'Confirming...';
      case 'completed': return 'Completed';
      case 'error': return 'Error';
    }
  }

  formatBytes(bytes: number): string {
    if (!bytes || bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  }

  getExtension(filename: string): string {
    if (!filename) return '';
    return filename.split('.').pop()?.toUpperCase() ?? '';
  }

  getFileTypeCategory(type: string, filename: string): string {
    if (type.startsWith('image/')) return 'Image';
    if (this.isPdf(type, filename)) return 'PDF';
    if (this.isDoc(type, filename)) return 'Word';
    if (this.isExcel(type, filename)) return 'Excel';
    if (this.isPpt(type, filename)) return 'PPT';
    if (this.isCode(type, filename)) return 'Code';
    if (this.isMedia(type, filename)) return 'Media';
    if (this.isArchive(type, filename)) return 'Archive';
    return this.getExtension(filename) || 'File';
  }

  isPdf(type: string, filename: string): boolean {
    return type === 'application/pdf' || filename.toLowerCase().endsWith('.pdf');
  }

  isDoc(type: string, filename: string): boolean {
    const ext = filename.split('.').pop()?.toLowerCase();
    return type.includes('word') || ['doc', 'docx', 'txt', 'rtf', 'odt', 'pages'].includes(ext || '');
  }

  isPpt(type: string, filename: string): boolean {
    const ext = filename.split('.').pop()?.toLowerCase();
    return type.includes('presentation') || type.includes('powerpoint') || ['ppt', 'pptx', 'key'].includes(ext || '');
  }

  isExcel(type: string, filename: string): boolean {
    const ext = filename.split('.').pop()?.toLowerCase();
    return type.includes('sheet') || type.includes('excel') || ['xls', 'xlsx', 'csv', 'numbers'].includes(ext || '');
  }

  isCode(type: string, filename: string): boolean {
    const ext = filename.split('.').pop()?.toLowerCase();
    return ['js', 'ts', 'html', 'css', 'json', 'xml', 'py', 'java', 'cpp', 'c', 'sh', 'sql', 'yaml', 'yml'].includes(ext || '');
  }

  isMedia(type: string, filename: string): boolean {
    return type.startsWith('audio/') || type.startsWith('video/') || ['mp3', 'wav', 'mp4', 'mov', 'avi', 'mkv', 'webm', 'flac'].includes(filename.split('.').pop()?.toLowerCase() || '');
  }

  isArchive(type: string, filename: string): boolean {
    const ext = filename.split('.').pop()?.toLowerCase();
    return type.includes('zip') || type.includes('compressed') || ['zip', 'rar', '7z', 'tar', 'gz'].includes(ext || '');
  }
}
