import { Component, ChangeDetectionStrategy, input, output, computed } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface ItemToMovePreview {
  type: 'folder' | 'document';
  id: number | string;
  name: string;
  size?: number;
  mimeType?: string;
  contentType?: string;
  previewUrl?: string;
}

export interface TargetLocation {
  id: number | null;
  name: string;
  path?: string;
}

@Component({
  selector: 'app-move-confirmation-modal',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
@if (isOpen()) {
<div class="overlay" (click)="onClose()">
<div class="sheet" (click)="$event.stopPropagation()">

  <!-- ░░ HEADER ░░ -->
  <div class="hd">
    <div class="hd-text">
      <div class="hd-row">
        <span class="hd-title">Move Items</span>
        <span class="badge">{{ items().length }} {{ items().length === 1 ? 'item' : 'items' }} &bull; {{ fmtTotal() }}</span>
      </div>
      <span class="hd-sub">Confirm target folder or remove items before moving</span>
    </div>
    <button class="x-btn" (click)="onClose()" [disabled]="isMoving()">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
    </button>
  </div>

  <!-- ░░ ROUTE ░░ -->
  <div class="route">
    <div class="r-card r-from">
      <div class="r-ico src-ico">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>
      </div>
      <div class="r-info">
        <span class="r-lbl">From</span>
        <span class="r-val" [title]="sourceLocation() || 'Current folder'">{{ sourceLocation() || 'Current folder' }}</span>
      </div>
    </div>

    <div class="r-arrow">
      <div class="arr-track"></div>
      <div class="arr-chip">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="5" y1="12" x2="19" y2="12"/><polyline points="12,5 19,12 12,19"/></svg>
      </div>
    </div>

    <div class="r-card r-to">
      <div class="r-ico dst-ico">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>
      </div>
      <div class="r-info">
        <span class="r-lbl">To</span>
        <span class="r-val r-val-dst" [title]="targetLocation()?.name || 'Destination'">{{ targetLocation()?.name || 'Select destination' }}</span>
      </div>
    </div>
  </div>

  <!-- ░░ ITEMS ░░ -->
  <div class="body">
    <div class="grid">
      @for (item of items(); track item.id; let i = $index) {
        <div class="card" [style.animation-delay]="i * 30 + 'ms'">
          <button class="rm" (click)="removeItem(item.id)" title="Remove item from move">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>

          <!-- Preview area matching Grid View -->
          <div class="thumb" [ngClass]="'t-' + cat(item)">
            @if (item.previewUrl) {
              <img [src]="item.previewUrl" class="t-img" [alt]="item.name" />
            } @else {
              <div class="t-mock">
                @switch (cat(item)) {
                  @case ('folder') {
                    <svg class="t-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
                    </svg>
                  }
                  @case ('pdf') {
                    <svg class="t-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                      <polyline points="14 2 14 8 20 8"/>
                      <line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/>
                    </svg>
                  }
                  @case ('image') {
                    <svg class="t-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                      <rect x="3" y="3" width="18" height="18" rx="2"/>
                      <circle cx="8.5" cy="8.5" r="1.5"/>
                      <polyline points="21 15 16 10 5 21"/>
                    </svg>
                  }
                  @case ('spreadsheet') {
                    <svg class="t-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                      <polyline points="14 2 14 8 20 8"/>
                      <line x1="8" y1="13" x2="16" y2="13"/><line x1="8" y1="17" x2="16" y2="17"/>
                    </svg>
                  }
                  @case ('word') {
                    <svg class="t-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                      <polyline points="14 2 14 8 20 8"/>
                      <line x1="16" y1="13" x2="8" y2="13"/>
                    </svg>
                  }
                  @case ('code') {
                    <svg class="t-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                      <polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/>
                    </svg>
                  }
                  @default {
                    <svg class="t-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                      <polyline points="14 2 14 8 20 8"/>
                    </svg>
                  }
                }
                <span class="t-badge" [ngClass]="'b-' + cat(item)">{{ badgeLabel(item) }}</span>
              </div>
            }
          </div>

          <!-- Info -->
          <div class="c-info">
            <span class="c-name" [title]="item.name">{{ item.name }}</span>
            <span class="c-meta">{{ item.type === 'folder' ? 'Folder' : fmtSize(item.size) }}</span>
          </div>
        </div>
      }
    </div>
  </div>

  <!-- ░░ FOOTER ░░ -->
  <div class="ft">
    <button class="btn-sec" (click)="onClose()" [disabled]="isMoving()">Cancel</button>
    <button class="btn-pri" (click)="onConfirm()" [disabled]="isMoving() || items().length === 0">
      @if (isMoving()) {
        <span class="spin"></span>Moving...
      } @else {
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:1rem;height:1rem"><line x1="5" y1="12" x2="19" y2="12"/><polyline points="12,5 19,12 12,19"/></svg>
        Move {{ items().length }} {{ items().length === 1 ? 'Item' : 'Items' }}
      }
    </button>
  </div>

</div>
</div>
}
  `,
  styles: [`
:host { font-family: 'Inter', system-ui, sans-serif; }

/* OVERLAY */
.overlay {
  position: fixed; inset: 0;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  display: flex; align-items: center; justify-content: center;
  z-index: 9999; padding: 1rem;
  animation: ov-in .18s ease-out;
}
@keyframes ov-in { from{opacity:0} to{opacity:1} }

/* SHEET */
.sheet {
  background: #ffffff;
  border-radius: 1rem;
  width: 100%; max-width: 640px; max-height: 85vh;
  display: flex; flex-direction: column; overflow: hidden;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1), 0 0 0 1px rgba(0, 0, 0, 0.05);
  animation: sh-in .22s cubic-bezier(.16, 1, .3, 1);
}
@keyframes sh-in { from{transform:scale(.96) translateY(8px);opacity:0} to{transform:scale(1) translateY(0);opacity:1} }

/* HEADER */
.hd {
  display: flex; align-items: center; justify-content: space-between;
  padding: 1.25rem 1.5rem 1rem;
  border-bottom: 1px solid #f1f5f9;
}
.hd-text { flex: 1; min-width: 0; }
.hd-row { display: flex; align-items: center; gap: .625rem; flex-wrap: wrap; }
.hd-title { font-size: 1.0625rem; font-weight: 700; color: #0f172a; letter-spacing: -.01em; }
.badge {
  font-size: .7rem; font-weight: 600;
  background: rgba(249, 115, 22, 0.08); color: #ea580c;
  border: 1px solid rgba(249, 115, 22, 0.2);
  padding: .15rem .55rem; border-radius: 99px;
}
.hd-sub { font-size: 0.8125rem; color: #64748b; margin-top: .15rem; display: block; }
.x-btn {
  width: 2rem; height: 2rem; border-radius: 0.375rem; flex-shrink: 0;
  border: none; background: transparent; color: #94a3b8;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer; transition: background .15s, color .15s;
}
.x-btn svg { width: 1.1rem; height: 1.1rem; }
.x-btn:hover:not(:disabled) { background: #f1f5f9; color: #334155; }
.x-btn:disabled { opacity: .4; cursor: not-allowed; }

/* ROUTE BANNER */
.route {
  display: grid; grid-template-columns: 1fr auto 1fr;
  align-items: center; gap: .75rem;
  padding: 0.875rem 1.5rem;
  background: #f8fafc;
  border-bottom: 1px solid #f1f5f9;
}
.r-card {
  display: flex; align-items: center; gap: 0.625rem;
  background: #ffffff; border: 1px solid #e2e8f0;
  border-radius: 0.625rem; padding: 0.5rem 0.75rem;
  box-shadow: 0 1px 2px rgba(0,0,0,0.03);
  min-width: 0; overflow: hidden;
}
.r-ico {
  width: 2rem; height: 2rem; flex-shrink: 0;
  border-radius: 0.375rem;
  display: flex; align-items: center; justify-content: center;
}
.r-ico svg { width: 1rem; height: 1rem; }
.src-ico { background: #f1f5f9; color: #64748b; }
.dst-ico { background: rgba(249, 115, 22, 0.1); color: #f97316; }
.r-info { display: flex; flex-direction: column; min-width: 0; }
.r-lbl { font-size: .6rem; font-weight: 700; text-transform: uppercase; letter-spacing: .05em; color: #94a3b8; }
.r-val { font-size: .8125rem; font-weight: 600; color: #1e293b; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.r-val-dst { color: #ea580c; font-weight: 600; }

.r-arrow { display: flex; align-items: center; gap: 0; position: relative; }
.arr-track { flex: 1; height: 1px; background: #cbd5e1; min-width: 1.25rem; }
.arr-chip {
  width: 1.75rem; height: 1.75rem; border-radius: 50%; flex-shrink: 0;
  background: #ffffff; border: 1px solid #fed7aa; color: #f97316;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
}
.arr-chip svg { width: 0.8125rem; height: 0.8125rem; }

/* BODY */
.body {
  padding: 1.25rem 1.5rem;
  overflow-y: auto; flex: 1;
  scrollbar-width: thin; scrollbar-color: #cbd5e1 transparent;
}
.body::-webkit-scrollbar { width: 4px; }
.body::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 99px; }

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(145px, 1fr));
  gap: 0.875rem;
}

/* CARD */
.card {
  border-radius: 0.75rem;
  border: 1px solid #e2e8f0;
  background: #ffffff;
  overflow: hidden; position: relative;
  display: flex; flex-direction: column;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
  transition: transform .15s ease, box-shadow .15s ease, border-color .15s ease;
  animation: c-in .2s ease-out both;
}
@keyframes c-in { from{opacity:0;transform:translateY(4px)} to{opacity:1;transform:translateY(0)} }
.card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.08);
  border-color: #cbd5e1;
}

/* REMOVE BTN */
.rm {
  position: absolute; top: 0.375rem; right: 0.375rem; z-index: 20;
  width: 1.375rem; height: 1.375rem; border-radius: 50%;
  border: none; cursor: pointer;
  background: rgba(15, 23, 42, 0.55);
  backdrop-filter: blur(4px);
  color: #ffffff;
  display: flex; align-items: center; justify-content: center;
  opacity: 0; transform: scale(.8);
  transition: opacity .15s, transform .15s, background .15s;
}
.rm svg { width: 0.7rem; height: 0.7rem; }
.card:hover .rm { opacity: 1; transform: scale(1); }
.rm:hover { background: #ef4444 !important; }

/* THUMBNAIL AREA MATCHING GRID VIEW */
.thumb {
  height: 110px; position: relative; overflow: hidden;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.t-img { width: 100%; height: 100%; object-fit: cover; display: block; }

.t-mock {
  width: 100%; height: 100%;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  position: relative; overflow: hidden;
}
.t-icon { width: 2.75rem; height: 2.75rem; opacity: 0.85; }

.t-badge {
  position: absolute; bottom: 0.375rem; left: 0.375rem;
  font-size: 0.5rem; font-weight: 800; text-transform: uppercase; letter-spacing: 0.05em;
  padding: 0.1rem 0.35rem; border-radius: 0.25rem;
}

/* Category styling matching Grid View */
.t-folder      { background: linear-gradient(145deg, #fff7ed, #ffedd5); }
.t-folder .t-icon { color: #f97316; }
.b-folder      { background: rgba(249, 115, 22, 0.12); color: #c2410c; }

.t-pdf         { background: linear-gradient(145deg, #fef2f2, #fee2e2); }
.t-pdf .t-icon    { color: #dc2626; }
.b-pdf         { background: #fecaca; color: #991b1b; }

.t-image       { background: linear-gradient(145deg, #faf5ff, #ede9fe); }
.t-image .t-icon  { color: #7c3aed; }
.b-image       { background: #ddd6fe; color: #5b21b6; }

.t-spreadsheet { background: linear-gradient(145deg, #f0fdf4, #dcfce7); }
.t-spreadsheet .t-icon { color: #16a34a; }
.b-spreadsheet { background: #bbf7d0; color: #14532d; }

.t-word        { background: linear-gradient(145deg, #eff6ff, #dbeafe); }
.t-word .t-icon   { color: #2563eb; }
.b-word        { background: #bfdbfe; color: #1e40af; }

.t-ppt         { background: linear-gradient(145deg, #fff7ed, #fed7aa); }
.t-ppt .t-icon    { color: #ea580c; }
.b-ppt         { background: #ffedd5; color: #9a3412; }

.t-code        { background: linear-gradient(145deg, #ecfdf5, #d1fae5); }
.t-code .t-icon   { color: #059669; }
.b-code        { background: #a7f3d0; color: #064e3b; }

.t-default     { background: linear-gradient(145deg, #f0f9ff, #e0f2fe); }
.t-default .t-icon { color: #0284c7; }
.b-default     { background: #bae6fd; color: #075985; }

/* CARD INFO */
.c-info {
  padding: 0.5rem 0.625rem;
  border-top: 1px solid #f1f5f9;
  display: flex; flex-direction: column; gap: 0.1rem;
  background: #ffffff;
}
.c-name { font-size: 0.77rem; font-weight: 600; color: #0f172a; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.c-meta { font-size: 0.6875rem; color: #94a3b8; }

/* FOOTER */
.ft {
  display: flex; align-items: center; justify-content: flex-end; gap: 0.625rem;
  padding: 0.875rem 1.5rem;
  border-top: 1px solid #f1f5f9;
  background: #f8fafc;
}
.btn-sec {
  padding: 0.5625rem 1.25rem; border-radius: 0.5rem;
  border: 1px solid #cbd5e1; background: #ffffff;
  font-size: 0.8125rem; font-weight: 600; color: #334155;
  cursor: pointer; transition: all 0.15s ease;
}
.btn-sec:hover:not(:disabled) { background: #f8fafc; border-color: #94a3b8; }
.btn-sec:disabled { opacity: .5; cursor: not-allowed; }

.btn-pri {
  display: inline-flex; align-items: center; gap: 0.5rem;
  padding: 0.5625rem 1.25rem; border-radius: 0.5rem;
  border: none;
  background: #f97316;
  font-size: 0.8125rem; font-weight: 600; color: #ffffff;
  cursor: pointer;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
  transition: all 0.15s ease;
}
.btn-pri:hover:not(:disabled) {
  background: #ea580c;
  box-shadow: 0 2px 6px rgba(249, 115, 22, 0.25);
}
.btn-pri:active:not(:disabled) { transform: translateY(1px); }
.btn-pri:disabled { opacity: .5; cursor: not-allowed; box-shadow: none; }

.spin {
  width: 0.875rem; height: 0.875rem; flex-shrink: 0;
  border: 2px solid rgba(255,255,255,.35);
  border-top-color: #ffffff; border-radius: 50%;
  animation: spin .6s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
  `],
})
export class MoveConfirmationModalComponent {
  isOpen = input<boolean>(false);
  items = input<ItemToMovePreview[]>([]);
  sourceLocation = input<string | null>(null);
  targetLocation = input<TargetLocation | null>(null);
  isMoving = input<boolean>(false);

  closed    = output<void>();
  confirmed = output<void>();
  itemRemoved = output<number | string>();

  private total = computed(() => this.items().reduce((s, i) => s + (i.size || 0), 0));

  fmtTotal() { return this.fmtSize(this.total()); }

  onClose()   { if (!this.isMoving()) this.closed.emit(); }
  onConfirm() { if (!this.isMoving() && this.items().length > 0) this.confirmed.emit(); }
  removeItem(id: number | string) { this.itemRemoved.emit(id); }

  cat(item: ItemToMovePreview): string {
    if (item.type === 'folder') return 'folder';
    const m = (item.mimeType || item.contentType || '').toLowerCase();
    const n = item.name.toLowerCase();
    if (m.includes('pdf') || n.endsWith('.pdf')) return 'pdf';
    if (m.includes('image') || /\.(png|jpg|jpeg|gif|svg|webp|bmp)$/.test(n)) return 'image';
    if (m.includes('spreadsheet') || m.includes('excel') || /\.(xls|xlsx|csv)$/.test(n)) return 'spreadsheet';
    if (m.includes('word') || /\.(doc|docx)$/.test(n)) return 'word';
    if (m.includes('powerpoint') || /\.(ppt|pptx)$/.test(n)) return 'ppt';
    if (/\.(js|ts|py|java|html|css|json|xml|sh|cpp|go|rs)$/.test(n)) return 'code';
    return 'default';
  }

  badgeLabel(item: ItemToMovePreview): string {
    if (item.type === 'folder') return 'DIR';
    const c = this.cat(item);
    if (c === 'pdf') return 'PDF';
    if (c === 'image') return 'IMG';
    if (c === 'spreadsheet') return 'XLS';
    if (c === 'word') return 'DOC';
    if (c === 'ppt') return 'PPT';
    if (c === 'code') return 'CODE';
    const ext = this.ext(item.name);
    return ext || 'FILE';
  }

  ext(name: string): string {
    const p = name.split('.');
    return p.length > 1 ? p[p.length - 1].slice(0, 5).toUpperCase() : '';
  }

  fmtSize(bytes?: number): string {
    if (!bytes || bytes === 0) return '—';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1048576) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / 1048576).toFixed(1)} MB`;
  }
}

