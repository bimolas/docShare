import {
  Component, ChangeDetectionStrategy, input, output, signal, computed, effect, inject, HostListener
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { DocumentApiService } from '../../../core/services/document-api.service';
import { FolderApiService } from '../../../core/services/folder-api.service';
import { FolderPeekResponseDto } from '../../../core/models/folder.model';

export type FileCategory = 'image' | 'pdf' | 'office' | 'video' | 'audio' | 'code' | 'spreadsheet' | 'default';

@Component({
  selector: 'app-file-preview-modal',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (isOpen() && item()) {
      <div class="preview-overlay" (click)="closed.emit()">
        <!-- Floating Nav Arrows -->
        @if (hasPrevious()) {
          <button type="button" class="nav-arrow nav-arrow-prev" (click)="$event.stopPropagation(); previous.emit()" title="Previous Item (Left Arrow)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="15 18 9 12 15 6"/></svg>
          </button>
        }
        @if (hasNext()) {
          <button type="button" class="nav-arrow nav-arrow-next" (click)="$event.stopPropagation(); next.emit()" title="Next Item (Right Arrow)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="9 18 15 12 9 6"/></svg>
          </button>
        }

        <div class="preview-window" [class.is-fullscreen]="isFullscreen()" (click)="$event.stopPropagation()">
        <!-- Header Bar -->
        <header class="preview-header">
          <div class="header-left">
            <div class="item-icon-badge" [ngClass]="getCategoryClass()">
              @if (item()?.type === 'folder') {
                <svg viewBox="0 0 24 24" fill="currentColor"><path d="M20 6h-8l-2-2H4C2.9 4 2 4.9 2 6v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2z"/></svg>
              } @else if (getFileCategory() === 'image') {
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/></svg>
              } @else if (getFileCategory() === 'pdf' || getFileCategory() === 'office') {
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><line x1="10" y1="9" x2="8" y2="9"/></svg>
              } @else if (getFileCategory() === 'video') {
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="23 7 16 12 23 17 23 7"/><rect x="1" y="5" width="15" height="14" rx="2" ry="2"/></svg>
              } @else if (getFileCategory() === 'audio') {
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/></svg>
              } @else if (getFileCategory() === 'code') {
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/></svg>
              } @else {
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
              }
            </div>
            <div class="item-title-meta">
              <h2 class="item-title" [title]="getItemName()">{{ getItemName() }}</h2>
              <div class="meta-pills">
                <span class="meta-pill category">{{ item()?.type === 'folder' ? 'Folder' : getCategoryLabel() }}</span>
                @if (item()?.type === 'document') {
                  <span class="meta-pill size">{{ formatBytes(getItemSize()) }}</span>
                  @if (!effectiveCanDownload()) {
                    <span class="meta-pill access-pill view-only">View Only</span>
                  } @else {
                    <span class="meta-pill access-pill can-download">Can Download</span>
                  }
                } @else if (folderPeek()) {
                  <span class="meta-pill count">{{ folderPeek()?.totalDocuments }} files</span>
                  <span class="meta-pill size">{{ formatBytes(folderPeek()?.totalSizeBytes || 0) }}</span>
                }
              </div>
            </div>
          </div>

          <!-- Middle Controls: Image Zoom or Code Toolbar -->
          <div class="header-center">
            @if (item()?.type === 'document' && getFileCategory() === 'image' && previewUrl()) {
              <div class="zoom-toolbar">
                <button type="button" class="zoom-btn" (click)="zoomOut()" [disabled]="zoomLevel() <= 0.25" title="Zoom Out (-)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/><line x1="8" y1="11" x2="14" y2="11"/></svg>
                </button>
                <span class="zoom-value" (click)="resetZoom()" title="Click to Reset Zoom">{{ Math.round(zoomLevel() * 100) }}%</span>
                <button type="button" class="zoom-btn" (click)="zoomIn()" [disabled]="zoomLevel() >= 4.0" title="Zoom In (+)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/><line x1="11" y1="8" x2="11" y2="14"/><line x1="8" y1="11" x2="14" y2="11"/></svg>
                </button>
                <button type="button" class="zoom-reset-btn" (click)="resetZoom()" title="Reset Zoom">Fit</button>
              </div>
            } @else if (item()?.type === 'document' && getFileCategory() === 'code' && codeTextContent() !== null) {
              <div class="code-toolbar">
                <button type="button" class="code-tool-btn" (click)="toggleWordWrap()" [class.active]="isWordWrap()" title="Toggle Word Wrap">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 6h18"/><path d="M3 12h15a3 3 0 1 1 0 6h-4"/><polyline points="16 16 14 18 16 20"/><line x1="3" y1="18" x2="9" y2="18"/></svg>
                  <span>Wrap</span>
                </button>
                <button type="button" class="code-tool-btn" (click)="copyCodeText()" title="Copy File Content">
                  @if (isCopied()) {
                    <svg viewBox="0 0 24 24" fill="none" stroke="#22c55e" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
                    <span style="color: #22c55e;">Copied!</span>
                  } @else {
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
                    <span>Copy</span>
                  }
                </button>
              </div>
            }
          </div>

          <!-- Header Right Actions -->
          <div class="header-right">
            <button type="button" class="action-btn icon-only-btn" (click)="toggleFullscreen()" title="Toggle Fullscreen">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                @if (isFullscreen()) {
                  <path d="M8 3v3a2 2 0 0 1-2 2H3m18 0h-3a2 2 0 0 1-2-2V3m0 18v-3a2 2 0 0 1 2-2h3M3 16h3a2 2 0 0 1 2 2v3"/>
                } @else {
                  <path d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3"/>
                }
              </svg>
            </button>

            @if (item()?.type === 'document' && effectiveCanDownload()) {
              <button type="button" class="action-btn download-btn" (click)="download.emit(item()?.data)" title="Download File">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                <span>Download</span>
              </button>
            }
            <button type="button" class="action-btn close-btn" (click)="closed.emit()" title="Close Preview (Esc)">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
            </button>
          </div>
        </header>


        <!-- Preview Canvas -->
        <main class="preview-canvas" (click)="$event.stopPropagation()">
          @if (isLoading()) {
            <div class="canvas-state loading-state">
              <div class="spinner"></div>
              <span>Generating secure preview...</span>
            </div>
          } @else if (errorMessage()) {
            <div class="canvas-state error-state">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
              <p>{{ errorMessage() }}</p>
              @if (item()?.type === 'document' && effectiveCanDownload()) {
                <button type="button" class="btn-primary" (click)="download.emit(item()?.data)">Download Instead</button>
              }
            </div>
          } @else if (item()?.type === 'folder') {
            <!-- Folder Summary Card -->
            <div class="folder-preview-card">
              <div class="folder-preview-header">
                <div class="folder-icon-large">
                  <svg viewBox="0 0 24 24" fill="currentColor"><path d="M20 6h-8l-2-2H4C2.9 4 2 4.9 2 6v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2z"/></svg>
                </div>
                <h3 class="folder-card-name">{{ getItemName() }}</h3>
                <p class="folder-card-sub">Folder Overview & Storage Metrics</p>
              </div>

              <div class="folder-stats-grid">
                <div class="stat-box">
                  <span class="stat-label">Files</span>
                  <span class="stat-value">{{ folderPeek()?.totalDocuments ?? '—' }}</span>
                </div>
                <div class="stat-box">
                  <span class="stat-label">Subfolders</span>
                  <span class="stat-value">{{ folderPeek()?.totalSubfolders ?? '—' }}</span>
                </div>
                <div class="stat-box">
                  <span class="stat-label">Total Size</span>
                  <span class="stat-value">{{ formatBytes(folderPeek()?.totalSizeBytes || 0) }}</span>
                </div>
              </div>

              <div class="folder-card-actions">
                <button type="button" class="btn-primary" (click)="onExploreFolder()">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>
                  <span>Open Folder</span>
                </button>
              </div>
            </div>
          } @else if (item()?.type === 'document') {
            <!-- Document Previews -->
            @if (getFileCategory() === 'image') {
              <div class="image-viewer-container">
                <div class="image-wrapper" [style.transform]="'scale(' + zoomLevel() + ')'">
                  <img [src]="previewUrl()" [alt]="getItemName()" class="preview-image" />
                </div>
              </div>
            } @else if (getFileCategory() === 'pdf' || getFileCategory() === 'office') {
              <div class="pdf-viewer-container">
                @if (sanitizedUrl()) {
                  <iframe [src]="sanitizedUrl()" class="pdf-iframe" [title]="getFileCategory() === 'office' ? 'Office Document Preview' : 'PDF Document Preview'"></iframe>
                }
              </div>
            } @else if (getFileCategory() === 'video') {
              <div class="media-viewer-container">
                @if (previewUrl()) {
                  <video [src]="previewUrl()" controls autoplay class="media-video"></video>
                }
              </div>
            } @else if (getFileCategory() === 'audio') {
              <div class="audio-viewer-card">
                <div class="audio-art">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/></svg>
                </div>
                <h3 class="audio-title">{{ getItemName() }}</h3>
                <p class="audio-sub">{{ formatBytes(getItemSize()) }} • Audio Track</p>
                @if (previewUrl()) {
                  <audio [src]="previewUrl()" controls autoplay class="audio-player"></audio>
                }
              </div>
            } @else if (getFileCategory() === 'code') {
              <div class="code-viewer-container">
                <div class="code-viewer-header">
                  <span class="code-lang-label">{{ getFileExtension() }}</span>
                  <span class="code-stats-label">{{ codeLines().length }} lines • {{ (codeTextContent() || '').length }} characters</span>
                </div>
                <div class="code-content-wrapper" [class.word-wrap]="isWordWrap()">
                  <div class="code-line-numbers">
                    @for (line of codeLines(); track $index) {
                      <span class="line-num">{{ $index + 1 }}</span>
                    }
                  </div>
                  <pre class="code-pre"><code>{{ codeTextContent() }}</code></pre>
                </div>
              </div>
            } @else {
              <!-- Fallback Document Card -->
              <div class="doc-fallback-card">
                <div class="doc-icon-large" [ngClass]="getCategoryClass()">
                  @if (getFileCategory() === 'spreadsheet') {
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><path d="M8 13h8"/><path d="M8 17h8"/><path d="M10 9h4"/></svg>
                  } @else {
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><line x1="10" y1="9" x2="8" y2="9"/></svg>
                  }
                </div>
                <div class="fallback-badge">{{ getFileExtension() }}</div>
                <h3 class="doc-card-title">{{ getItemName() }}</h3>
                <p class="doc-card-sub">{{ getCategoryLabel() }} • {{ formatBytes(getItemSize()) }}</p>

                <div class="doc-meta-box">
                  <div class="meta-row">
                    <span class="meta-key">Format:</span>
                    <span class="meta-val">{{ getItemType() || 'Binary File' }}</span>
                  </div>
                  @if (getItemHash()) {
                    <div class="meta-row">
                      <span class="meta-key">Checksum (SHA-256):</span>
                      <span class="meta-val hash-text">{{ getItemHash() }}</span>
                    </div>
                  }
                  <div class="meta-row">
                    <span class="meta-key">Uploaded:</span>
                    <span class="meta-val">{{ formatDate(getItemDate()) }}</span>
                  </div>
                </div>

                <div class="fallback-actions">
                  @if (effectiveCanDownload()) {
                    <button type="button" class="btn-primary" (click)="download.emit(item()?.data)">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                      <span>Download File to View</span>
                    </button>
                  } @else {
                    <div class="view-only-notice">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
                      <span>View-Only Access — Download Restricted</span>
                    </div>
                  }
                </div>
              </div>
            }
          }
        </main>

        <!-- Footer / Secondary Area -->
        <footer class="preview-footer">
          <div class="footer-left">
            @if (item()?.type === 'document') {
              @if (!effectiveCanDownload()) {
                <div class="view-only-indicator">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
                  <span>View-Only Mode — Downloads restricted for this document</span>
                </div>
              } @else {
                <span class="footer-meta-info">
                  {{ formatBytes(getItemSize()) }} • {{ getCategoryLabel() }}
                </span>
              }
            }
          </div>

          <div class="footer-right">
            <button
              type="button"
              class="btn-preview-details"
              (click)="onDetailsClicked()"
              title="Inspect details and metadata"
              aria-label="Inspect details and metadata"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <line x1="12" y1="16" x2="12" y2="12"/>
                <line x1="12" y1="8" x2="12.01" y2="8"/>
              </svg>
              <span>Details</span>
            </button>
          </div>
        </footer>
      </div><!-- /preview-window -->
      </div><!-- /preview-overlay -->
    }
  `,
  styles: [`
    /* ── Overlay scrim ── */
    .preview-overlay {
      position: fixed;
      inset: 0;
      z-index: 1000;
      background: rgba(15, 23, 42, 0.65);
      backdrop-filter: blur(4px);
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 1rem;
      animation: overlayIn 0.18s ease-out;
    }
    @keyframes overlayIn {
      from { opacity: 0; }
      to   { opacity: 1; }
    }

    /* ── Floating window ── */
    .preview-window {
      position: relative;
      display: flex;
      flex-direction: column;
      width: 100%;
      max-width: 1160px;
      height: min(92vh, 800px);
      background: #ffffff;
      border-radius: 0.625rem;
      border: 1px solid #cbd5e1;
      box-shadow: 0 10px 25px -5px rgba(15, 23, 42, 0.12), 0 8px 10px -6px rgba(15, 23, 42, 0.08);
      overflow: hidden;
      animation: windowIn 0.2s cubic-bezier(0.16, 1, 0.3, 1);
    }
    @keyframes windowIn {
      from { opacity: 0; transform: scale(0.97) translateY(6px); }
      to   { opacity: 1; transform: scale(1) translateY(0); }
    }
    .preview-window.is-fullscreen {
      max-width: 100%;
      height: 100vh;
      border-radius: 0;
      border: none;
    }

    /* ── Header ── */
    .preview-header {
      height: 3.75rem;
      padding: 0 1.25rem;
      display: flex;
      align-items: center;
      justify-content: space-between;
      border-bottom: 1px solid #e2e8f0;
      background: #ffffff;
      flex-shrink: 0;
    }

    .header-left {
      display: flex;
      align-items: center;
      gap: 0.875rem;
      min-width: 0;
      flex: 1;
    }

    .item-icon-badge {
      width: 2.25rem;
      height: 2.25rem;
      border-radius: 0.5rem;
      background: #fff7ed;
      color: #ea580c;
      border: 1px solid #ffedd5;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
    .item-icon-badge svg { width: 1.25rem; height: 1.25rem; }
    .cat-folder      { background: #fff7ed; color: #ea580c; border-color: #ffedd5; }
    .cat-image       { background: #f8fafc; color: #0284c7; border-color: #e0f2fe; }
    .cat-pdf         { background: #fef2f2; color: #dc2626; border-color: #fee2e2; }
    .cat-video       { background: #fff7ed; color: #ea580c; border-color: #ffedd5; }
    .cat-audio       { background: #fff7ed; color: #d97706; border-color: #fef3c7; }
    .cat-spreadsheet { background: #f0fdf4; color: #16a34a; border-color: #dcfce7; }
    .cat-code        { background: #f8fafc; color: #475569; border-color: #e2e8f0; }
    .cat-default     { background: #f8fafc; color: #475569; border-color: #e2e8f0; }

    .access-pill {
      font-size: 0.6875rem;
      font-weight: 600;
      padding: 0.0625rem 0.4375rem;
      border-radius: 9999px;
    }
    .access-pill.view-only {
      background: #fef2f2;
      color: #dc2626;
      border: 1px solid #fee2e2;
    }
    .access-pill.can-download {
      background: #f0fdf4;
      color: #16a34a;
      border: 1px solid #dcfce7;
    }

    /* ── Footer / Secondary Area ── */
    .preview-footer {
      display: flex;
      align-items: center;
      justify-content: space-between;
      height: 3.25rem;
      padding: 0 1.25rem;
      background: #ffffff;
      border-top: 1px solid #e2e8f0;
      flex-shrink: 0;
    }

    .footer-left {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }

    .view-only-indicator {
      display: inline-flex;
      align-items: center;
      gap: 0.4375rem;
      font-size: 0.8125rem;
      font-weight: 500;
      color: #b91c1c;
      background: #fef2f2;
      padding: 0.25rem 0.625rem;
      border-radius: 0.375rem;
      border: 1px solid #fecaca;
    }
    .view-only-indicator svg {
      width: 0.875rem;
      height: 0.875rem;
    }

    .footer-meta-info {
      font-size: 0.8125rem;
      color: #64748b;
    }

    .btn-preview-details {
      display: inline-flex;
      align-items: center;
      gap: 0.375rem;
      padding: 0.375rem 0.875rem;
      font-size: 0.8125rem;
      font-weight: 600;
      color: #475569;
      background: #f8fafc;
      border: 1px solid #cbd5e1;
      border-radius: 0.375rem;
      cursor: pointer;
      transition: all 0.15s ease;
    }
    .btn-preview-details:hover {
      background: #f1f5f9;
      color: #0f172a;
      border-color: #94a3b8;
    }
    .btn-preview-details svg {
      width: 0.875rem;
      height: 0.875rem;
    }

    .item-title-meta { display: flex; flex-direction: column; min-width: 0; }
    .item-title {
      font-size: 0.9375rem;
      font-weight: 700;
      color: #0f172a;
      margin: 0 0 0.25rem;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .meta-pills { display: flex; align-items: center; gap: 0.375rem; }
    .meta-pill {
      font-size: 0.6875rem;
      font-weight: 600;
      padding: 0.1rem 0.5rem;
      border-radius: 9999px;
    }
    .meta-pill.category { background: #f8fafc; color: #64748b; border: 1px solid #e2e8f0; }
    .meta-pill.size, .meta-pill.count { background: rgba(255, 85, 0, 0.15); color: #ff884d; }

    /* Middle Controls (Zoom / Code Toolbar) */
    .header-center {
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .zoom-toolbar, .code-toolbar {
      display: flex;
      align-items: center;
      gap: 0.375rem;
      background: #f8fafc;
      padding: 0.25rem 0.5rem;
      border-radius: 0.625rem;
      border: 1px solid #e2e8f0;
    }
    .zoom-btn, .zoom-reset-btn, .code-tool-btn {
      background: transparent;
      border: none;
      color: #94a3b8;
      cursor: pointer;
      padding: 0.25rem 0.5rem;
      border-radius: 0.375rem;
      font-size: 0.75rem;
      font-weight: 600;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.375rem;
      transition: all 0.15s;
    }
    .zoom-btn:hover:not(:disabled), .zoom-reset-btn:hover, .code-tool-btn:hover, .code-tool-btn.active {
      background: #e2e8f0;
      color: #0f172a;
    }
    .zoom-btn:disabled { opacity: 0.3; cursor: not-allowed; }
    .zoom-btn svg, .code-tool-btn svg { width: 0.875rem; height: 0.875rem; }
    .zoom-value {
      font-size: 0.75rem;
      font-weight: 700;
      color: #ff5500;
      padding: 0 0.375rem;
      min-width: 2.75rem;
      text-align: center;
      cursor: pointer;
    }

    .header-right { display: flex; align-items: center; gap: 0.625rem; }
    .action-btn {
      display: flex;
      align-items: center;
      gap: 0.375rem;
      padding: 0.5rem 0.875rem;
      border-radius: 0.5rem;
      font-size: 0.8125rem;
      font-weight: 600;
      border: none;
      cursor: pointer;
      transition: all 0.15s;
    }
    .icon-only-btn {
      background: #f8fafc;
      color: #64748b;
      padding: 0.5rem;
    }
    .icon-only-btn:hover { background: #e2e8f0; color: #0f172a; }
    .icon-only-btn svg { width: 1.125rem; height: 1.125rem; }

    .download-btn {
      background: #ff5500;
      color: #ffffff;
    }
    .download-btn:hover { background: #e04b00; }
    .download-btn svg { width: 1rem; height: 1rem; }

    .close-btn {
      background: #f8fafc;
      color: #64748b;
      padding: 0.5rem;
    }
    .close-btn:hover { background: #e2e8f0; color: #0f172a; }
    .close-btn svg { width: 1.25rem; height: 1.25rem; }

    /* ── Floating Nav Arrows ── */
    .nav-arrow {
      position: absolute;
      top: 50%;
      transform: translateY(-50%);
      z-index: 110;
      width: 3.5rem;
      height: 3.5rem;
      border-radius: 50%;
      background: #ffffff;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
      border: 1px solid #e2e8f0;
      color: #475569;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
    }
    .nav-arrow:hover {
      background: #f8fafc;
      color: #ff5500;
      transform: translateY(-50%) scale(1.1);
      box-shadow: 0 6px 16px rgba(0, 0, 0, 0.2);
    }
    .nav-arrow svg { width: 1.75rem; height: 1.75rem; }
    .nav-arrow-prev { left: 1.5rem; }
    .nav-arrow-next { right: 1.5rem; }

    /* ── Fullscreen Mode ── */
    .is-fullscreen .preview-canvas {
      padding: 0;
    }
    .is-fullscreen .pdf-viewer-container, .is-fullscreen .code-viewer-container, .is-fullscreen .media-viewer-container {
      max-width: 100%;
      height: 100%;
      border-radius: 0;
    }
    .is-fullscreen .pdf-iframe {
      border-radius: 0;
    }

    /* ── Canvas ── */
    .preview-canvas {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
      overflow: hidden;
      position: relative;
      padding: 1.5rem;
    }

    .canvas-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 1rem;
      color: #64748b;
      font-size: 0.875rem;
      font-weight: 500;
    }
    .spinner {
      width: 2.5rem;
      height: 2.5rem;
      border: 3px solid #334155;
      border-top-color: #ff5500;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }
    @keyframes spin { to { transform: rotate(360deg); } }

    .error-state svg { width: 3rem; height: 3rem; color: #ef4444; }

    /* Image Viewer */
    .image-viewer-container {
      width: 100%;
      height: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
      overflow: auto;
    }
    .image-wrapper {
      transition: transform 0.15s cubic-bezier(0.2, 0, 0, 1);
      display: flex;
      align-items: center;
      justify-content: center;
      width: 100%;
      height: 100%;
    }
    .preview-image {
      max-width: 100%;
      max-height: 100%;
      object-fit: contain;
      border-radius: 0.5rem;
      box-shadow: 0 10px 30px rgba(0,0,0,0.1);
    }

    /* PDF Viewer */
    .pdf-viewer-container {
      width: 100%;
      height: 100%;
      max-width: 960px;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .pdf-iframe {
      width: 100%;
      height: 100%;
      border: none;
      border-radius: 0.75rem;
      box-shadow: 0 20px 40px rgba(0,0,0,0.5);
      background: #ffffff;
    }

    /* ── Video Player ── */
    .media-viewer-container {
      width: 100%;
      height: 100%;
      max-width: 1000px;
      max-height: 80vh;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .media-video {
      width: 100%;
      max-height: 80vh;
      border-radius: 0.75rem;
      outline: none;
      box-shadow: 0 20px 40px rgba(0, 0, 0, 0.6);
      background: #000000;
    }

    /* ── Audio Player ── */
    .audio-viewer-card {
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 1rem;
      padding: 2.5rem;
      max-width: 440px;
      width: 100%;
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
      box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
    }
    .audio-art {
      width: 5rem;
      height: 5rem;
      border-radius: 1rem;
      background: linear-gradient(135deg, rgba(255, 85, 0, 0.2), rgba(124, 58, 237, 0.2));
      color: #ff5500;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 1.25rem;
    }
    .audio-art svg { width: 2.5rem; height: 2.5rem; }
    .audio-title { font-size: 1.125rem; font-weight: 700; color: #0f172a; margin: 0 0 0.375rem; word-break: break-all; }
    .audio-sub { font-size: 0.8125rem; color: #64748b; margin: 0 0 1.5rem; }
    .audio-player { width: 100%; outline: none; border-radius: 0.5rem; }

    /* ── Code / Text Viewer ── */
    .code-viewer-container {
      width: 100%;
      height: 100%;
      max-width: 1100px;
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 0.75rem;
      display: flex;
      flex-direction: column;
      overflow: hidden;
      box-shadow: 0 20px 40px rgba(0,0,0,0.6);
    }
    .code-viewer-header {
      padding: 0.625rem 1rem;
      background: #f8fafc;
      border-bottom: 1px solid #334155;
      display: flex;
      align-items: center;
      justify-content: space-between;
      font-size: 0.75rem;
    }
    .code-lang-label { font-weight: 800; color: #ff5500; text-transform: uppercase; letter-spacing: 0.05em; }
    .code-stats-label { color: #64748b; font-weight: 500; }

    .code-content-wrapper {
      flex: 1;
      display: flex;
      overflow: auto;
      font-family: 'JetBrains Mono', 'Fira Code', Consolas, Monaco, monospace;
      font-size: 0.8125rem;
      line-height: 1.6;
    }
    .code-line-numbers {
      padding: 1rem 0.75rem;
      background: #f1f5f9;
      border-right: 1px solid #e2e8f0;
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      user-select: none;
      color: #94a3b8;
    }
    .line-num { height: 1.3rem; line-height: 1.3rem; font-size: 0.75rem; }

    .code-pre {
      margin: 0;
      padding: 1rem 1.25rem;
      color: #0f172a;
      flex: 1;
      overflow: visible;
      white-space: pre;
    }
    .code-content-wrapper.word-wrap .code-pre {
      white-space: pre-wrap;
      word-break: break-all;
    }

    /* Cards (Folder & Fallback Document) */
    .folder-preview-card, .doc-fallback-card {
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 1rem;
      padding: 2.5rem;
      max-width: 480px;
      width: 100%;
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
      box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
      position: relative;
    }

    .folder-icon-large, .doc-icon-large {
      width: 4.5rem;
      height: 4.5rem;
      border-radius: 1rem;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 1.25rem;
    }
    .folder-icon-large { background: rgba(255, 85, 0, 0.15); color: #ff5500; }
    .folder-icon-large svg, .doc-icon-large svg { width: 2.5rem; height: 2.5rem; }

    .folder-card-name, .doc-card-title {
      font-size: 1.25rem;
      font-weight: 700;
      color: #0f172a;
      margin: 0 0 0.375rem;
      word-break: break-all;
    }
    .folder-card-sub, .doc-card-sub {
      font-size: 0.8125rem;
      color: #64748b;
      margin: 0 0 1.5rem;
    }

    .fallback-badge {
      font-size: 0.6875rem;
      font-weight: 800;
      letter-spacing: 0.05em;
      text-transform: uppercase;
      background: #ff5500;
      color: #ffffff;
      padding: 0.15rem 0.6rem;
      border-radius: 0.375rem;
      margin-bottom: 0.75rem;
    }

    .folder-stats-grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 0.75rem;
      width: 100%;
      margin-bottom: 1.75rem;
    }
    .stat-box {
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 0.625rem;
      padding: 0.75rem 0.5rem;
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }
    .stat-label { font-size: 0.6875rem; font-weight: 600; color: #64748b; text-transform: uppercase; }
    .stat-value { font-size: 1.125rem; font-weight: 700; color: #0f172a; }

    .doc-meta-box {
      width: 100%;
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 0.625rem;
      padding: 1rem;
      margin-bottom: 1.5rem;
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      text-align: left;
    }
    .meta-row { display: flex; justify-content: space-between; font-size: 0.75rem; gap: 0.5rem; }
    .meta-key { color: #64748b; font-weight: 500; }
    .meta-val { color: #94a3b8; font-weight: 600; text-align: right; word-break: break-all; }
    .hash-text { font-family: monospace; font-size: 0.6875rem; color: #ff884d; }

    .btn-primary {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      background: #ff5500;
      color: #ffffff;
      font-size: 0.875rem;
      font-weight: 600;
      padding: 0.75rem 1.5rem;
      border-radius: 0.625rem;
      border: none;
      cursor: pointer;
      transition: background 0.15s;
      width: 100%;
    }
    .btn-primary:hover { background: #e04b00; }
    .btn-primary svg { width: 1.125rem; height: 1.125rem; }

    .view-only-notice {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      background: #f1f5f9;
      color: #64748b;
      font-size: 0.8125rem;
      font-weight: 600;
      padding: 0.75rem 1rem;
      border-radius: 0.625rem;
      border: 1px solid #e2e8f0;
      width: 100%;
    }
    .view-only-notice svg { width: 1rem; height: 1rem; color: #94a3b8; }
  `],
})
export class FilePreviewModalComponent {
  private readonly documentApiService = inject(DocumentApiService);
  private readonly folderApiService   = inject(FolderApiService);
  private readonly sanitizer          = inject(DomSanitizer);
  private readonly http               = inject(HttpClient);

  isOpen      = input<boolean>(false);
  item        = input<{ type: 'folder' | 'document'; data: any } | null>(null);
  canDownload = input<boolean | null>(null);
  hasPrevious = input<boolean>(false);
  hasNext     = input<boolean>(false);

  readonly effectiveCanDownload = computed(() => {
    const explicit = this.canDownload();
    if (explicit !== null) return explicit;
    const current = this.item();
    if (!current || current.type !== 'document') return false;
    const data = current.data;
    if (!data) return false;
    if (data.isLocked) return false;
    if (data.permissionLevel) {
      return data.permissionLevel === 'DOWNLOAD' || data.permissionLevel === 'EDIT' || data.permissionLevel === 'ADMIN' || data.permissionLevel === 'OWNER';
    }
    return true;
  });

  closed     = output<void>();
  download   = output<any>();
  openFolder = output<number>();
  previous   = output<void>();
  next       = output<void>();
  openDetails = output<{ type: 'folder' | 'document'; data: any }>();

  onDetailsClicked(): void {
    const current = this.item();
    if (current) {
      this.openDetails.emit(current);
      this.closed.emit();
    }
  }

  zoomLevel       = signal<number>(1.0);
  isFullscreen    = signal<boolean>(false);
  isLoading       = signal<boolean>(false);
  errorMessage    = signal<string | null>(null);
  previewUrl      = signal<string | null>(null);
  sanitizedUrl    = signal<SafeResourceUrl | null>(null);
  folderPeek      = signal<FolderPeekResponseDto | null>(null);
  codeTextContent = signal<string | null>(null);
  isWordWrap      = signal<boolean>(true);
  isCopied        = signal<boolean>(false);

  codeLines = computed(() => (this.codeTextContent() ?? '').split('\n'));

  Math = Math;

  constructor() {
    effect(() => {
      const open = this.isOpen();
      const current = this.item();
      if (open && current) {
        this.resetZoom();
        this.loadPreviewData(current);
      } else {
        this.previewUrl.set(null);
        this.sanitizedUrl.set(null);
        this.folderPeek.set(null);
        this.codeTextContent.set(null);
        this.errorMessage.set(null);
        this.isFullscreen.set(false);
      }
    });
  }

  @HostListener('window:keydown', ['$event'])
  onKeyDown(event: KeyboardEvent): void {
    if (!this.isOpen()) return;
    if (event.key === 'Escape') {
      this.closed.emit();
    } else if (event.key === 'ArrowLeft' && this.hasPrevious()) {
      this.previous.emit();
    } else if (event.key === 'ArrowRight' && this.hasNext()) {
      this.next.emit();
    } else if (this.item()?.type === 'document' && this.getFileCategory() === 'image') {
      if (event.key === '+' || event.key === '=') {
        this.zoomIn();
      } else if (event.key === '-' || event.key === '_') {
        this.zoomOut();
      }
    }
  }

  private loadPreviewData(item: { type: 'folder' | 'document'; data: any }): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.previewUrl.set(null);
    this.sanitizedUrl.set(null);
    this.folderPeek.set(null);
    this.codeTextContent.set(null);

    if (item.type === 'folder') {
      const folderId = +item.data.id;
      this.folderApiService.getFolderPeek(folderId).subscribe({
        next: (peek) => {
          this.folderPeek.set(peek);
          this.isLoading.set(false);
        },
        error: () => {
          this.isLoading.set(false);
        }
      });
    } else if (item.type === 'document') {
      const docId = +item.data.id;
      const category = this.getFileCategory();

      if (category === 'image' || category === 'pdf' || category === 'office' || category === 'video' || category === 'audio' || category === 'code') {
        this.documentApiService.getPreviewUrl(docId).subscribe({
          next: (res) => {
            if (res && res.previewUrl) {
              this.previewUrl.set(res.previewUrl);
              
              if (category === 'office') {
                const officeUrl = 'https://view.officeapps.live.com/op/embed.aspx?src=' + encodeURIComponent(res.previewUrl);
                this.sanitizedUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(officeUrl));
                this.isLoading.set(false);
              } else if (category === 'pdf') {
                this.http.get(res.previewUrl, { responseType: 'blob' }).subscribe({
                  next: (blob) => {
                    const objectUrl = URL.createObjectURL(blob);
                    this.sanitizedUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(objectUrl));
                    this.isLoading.set(false);
                  },
                  error: () => {
                    this.errorMessage.set('Could not load PDF content.');
                    this.isLoading.set(false);
                  }
                });
              } else if (category === 'code') {
                this.sanitizedUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(res.previewUrl));
                this.http.get(res.previewUrl, { responseType: 'text' }).subscribe({
                  next: (text) => {
                    this.codeTextContent.set(text);
                    this.isLoading.set(false);
                  },
                  error: () => {
                    this.errorMessage.set('Could not load text/code content.');
                    this.isLoading.set(false);
                  }
                });
              } else {
                this.sanitizedUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(res.previewUrl));
                this.isLoading.set(false);
              }
            } else {
              this.errorMessage.set('Could not fetch presigned preview URL.');
              this.isLoading.set(false);
            }
          },
          error: () => {
            this.errorMessage.set('Failed to generate preview URL from storage.');
            this.isLoading.set(false);
          }
        });
      } else {
        // Fallback document presentation
        this.isLoading.set(false);
      }
    }
  }

  zoomIn(): void {
    this.zoomLevel.update(z => Math.min(4.0, z + 0.25));
  }

  zoomOut(): void {
    this.zoomLevel.update(z => Math.max(0.25, z - 0.25));
  }

  resetZoom(): void {
    this.zoomLevel.set(1.0);
  }

  toggleFullscreen(): void {
    this.isFullscreen.update(f => !f);
  }

  toggleWordWrap(): void {
    this.isWordWrap.update(w => !w);
  }

  copyCodeText(): void {
    const text = this.codeTextContent();
    if (!text) return;
    navigator.clipboard.writeText(text).then(() => {
      this.isCopied.set(true);
      setTimeout(() => this.isCopied.set(false), 2000);
    });
  }

  onExploreFolder(): void {
    const current = this.item();
    if (current && current.type === 'folder') {
      this.openFolder.emit(+current.data.id);
      this.closed.emit();
    }
  }

  getItemName(): string {
    const d = this.item()?.data;
    return d?.fileName || d?.name || 'Untitled';
  }

  getItemType(): string {
    const d = this.item()?.data;
    return d?.contentType || d?.mimeType || '';
  }

  getItemSize(): number {
    const d = this.item()?.data;
    return d?.fileSize ?? d?.fileSizeBytes ?? 0;
  }

  getItemDate(): string {
    const d = this.item()?.data;
    return d?.uploadedAt || d?.createdAt || '';
  }

  getItemHash(): string {
    const d = this.item()?.data;
    return d?.fileHash || '';
  }

  getFileExtension(): string {
    const name = this.getItemName();
    return name.split('.').pop()?.toUpperCase() ?? 'FILE';
  }

  getFileCategory(): FileCategory {
    const mime = (this.getItemType() || '').toLowerCase();
    const ext  = (this.getItemName() || '').split('.').pop()?.toLowerCase() ?? '';

    if (mime.includes('pdf') || ext === 'pdf') return 'pdf';
    if (['doc', 'docx', 'ppt', 'pptx', 'xls', 'xlsx'].includes(ext)) return 'office';
    if (mime.includes('image') || ['png','jpg','jpeg','svg','webp','gif','bmp','ico'].includes(ext)) return 'image';
    if (mime.includes('video') || ['mp4','webm','ogg','mov','mkv'].includes(ext)) return 'video';
    if (mime.includes('audio') || ['mp3','wav','aac','flac','m4a'].includes(ext)) return 'audio';
    if (
      ['js','ts','html','css','json','java','py','sh','txt','md','csv','xml','yaml','yml','log','sql','rs','go','c','cpp','h','hpp','tsx','jsx'].includes(ext) ||
      mime.includes('text') || mime.includes('json') || mime.includes('xml') || mime.includes('javascript')
    ) {
      return 'code';
    }
    if (mime.includes('spreadsheet') || mime.includes('excel') || ['xlsx','xls'].includes(ext)) return 'spreadsheet';

    return 'default';
  }

  getCategoryClass(): string {
    if (this.item()?.type === 'folder') return 'cat-folder';
    const cat = this.getFileCategory();
    return `cat-${cat}`;
  }

  getCategoryLabel(): string {
    const mime = (this.getItemType() || '').toLowerCase();
    const ext  = this.getFileExtension();
    const cat  = this.getFileCategory();

    if (cat === 'pdf') return 'PDF Document';
    if (cat === 'image') return 'Image File';
    if (cat === 'video') return 'Video File';
    if (cat === 'audio') return 'Audio Recording';
    if (cat === 'code') return 'Text / Source Code';
    if (cat === 'spreadsheet') return 'Spreadsheet';
    if (cat === 'office') {
      if (['DOC','DOCX'].includes(ext.toUpperCase())) return 'Word Document';
      if (['PPT','PPTX'].includes(ext.toUpperCase())) return 'PowerPoint Presentation';
      if (['XLS','XLSX'].includes(ext.toUpperCase())) return 'Excel Spreadsheet';
      return 'Office Document';
    }

    return `${ext.toUpperCase()} File`;
  }

  formatBytes(bytes: number): string {
    if (!bytes) return '0 B';
    const k = 1024, s = ['B','KB','MB','GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + s[i];
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return 'N/A';
    return new Date(dateStr).toLocaleDateString(undefined, {
      month: 'short', day: 'numeric', year: 'numeric',
    });
  }
}
