import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  signal,
  HostListener,
  OnDestroy,
  ElementRef,
  ViewChild,
  NgZone,
  AfterViewInit,
  inject,
} from '@angular/core';


@Component({
  selector: 'app-explorer-drop-zone',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      #dropZoneWrapper
      class="drop-zone-wrapper cloudy-border"
      [class.is-drag-over]="isDragOver()"
      (dragover)="onDragOver($event)"
      (dragleave)="onDragLeave($event)"
      (drop)="onDrop($event)"
    >
      <div class="mouse-ambient-glow"></div>

      <!-- Main Drop Target Surface -->
      <div class="drop-target" (click)="fileInput.click()">
        <input #fileInput type="file" multiple class="hidden-input" (change)="onFileSelected($event)"/>
        <div class="upload-icon-ring" [class.drag-active]="isDragOver()">
          <div class="aura-wave aura-1"></div>
          <div class="aura-wave aura-2"></div>
          <svg class="cloud-upload-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.85" stroke-linecap="round" stroke-linejoin="round">
            <path class="cloud-path" d="M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242"/>
            <g class="arrow-group">
              <path class="arrow-stem" d="M12 20V10"/>
              <path class="arrow-head" d="m8.5 13.5 3.5-3.5 3.5 3.5"/>
            </g>
          </svg>
        </div>

        <p class="drop-primary">
          @if (isDragOver()) { <span class="drag-text-active">Release to inspect files</span> }
          @else { <span class="highlight-click">Click to upload</span> or drag and drop }
        </p>
        <p class="drop-secondary">PDF, DOC, TXT, XLS, JPG or ZIP (Instant grid preview & thumbnail generation)</p>
      </div>

      <!-- Quick Sample Chips for Instant Testing -->
      <div class="simulate-row" (click)="$event.stopPropagation()">
        <span class="simulate-label">Quick samples:</span>
        @for (chip of simulateChips; track chip.name) {
          <button class="sim-chip" (click)="simulateFile(chip)" title="Click to test upload preview with sample file">
            @if (chip.name.endsWith('.zip')) {
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
                <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
                <line x1="12" y1="11" x2="12" y2="17"/>
              </svg>
            } @else {
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
                <path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/>
                <polyline points="14 2 14 8 20 8"/>
                <line x1="8" y1="13" x2="16" y2="13"/>
                <line x1="8" y1="17" x2="14" y2="17"/>
              </svg>
            }
            {{ chip.name }}
          </button>
        }
      </div>
    </div>
  `,
  styles: [`
    .drop-zone-wrapper {
      position: relative;
      background: #ffffff;
      border-radius: 1.25rem;
      border: 1px solid rgba(226, 232, 240, 0.85);
      overflow: hidden;
      transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
    }

    /* Soft ambient mouse glow */
    .mouse-ambient-glow {
      position: absolute;
      inset: 0;
      background: radial-gradient(
        circle 420px at var(--mouse-x, 50%) var(--mouse-y, 30%),
        rgba(255, 110, 30, 0.09) 0%,
        rgba(255, 140, 60, 0.05) 35%,
        rgba(255, 245, 238, 0.25) 60%,
        transparent 85%
      );
      opacity: 0;
      pointer-events: none;
      transition: opacity 0.4s ease;
      z-index: 1;
    }

    .drop-zone-wrapper:hover .mouse-ambient-glow,
    .drop-zone-wrapper.is-drag-over .mouse-ambient-glow {
      opacity: 1;
    }

    .drop-zone-wrapper.is-drag-over {
      border-color: rgba(255, 85, 0, 0.5);
      box-shadow: 0 0 0 4px rgba(255, 85, 0, 0.08), 0 12px 28px -4px rgba(255, 85, 0, 0.12);
      transform: scale(1.002);
    }

    /* Drop Target Surface */
    .drop-target {
      position: relative;
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 2.25rem 1.5rem 1rem;
      cursor: pointer;
      user-select: none;
      z-index: 2;
    }

    /* Integrated icon container */
    .upload-icon-ring {
      position: relative;
      width: 3.75rem;
      height: 3.75rem;
      border-radius: 50%;
      border: 1px solid rgba(255, 120, 40, 0.18);
      background: linear-gradient(135deg, rgba(255, 245, 238, 0.6) 0%, rgba(255, 235, 225, 0.4) 100%);
      color: #ff5500;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 1.125rem;
      transition: transform 0.4s cubic-bezier(0.16, 1, 0.3, 1),
                  border-color 0.4s ease,
                  background 0.4s ease,
                  box-shadow 0.4s ease;
      box-shadow: 0 4px 12px -2px rgba(255, 85, 0, 0.06);
    }

    .aura-wave {
      position: absolute;
      inset: -4px;
      border-radius: 50%;
      border: 1px solid rgba(255, 120, 40, 0.12);
      opacity: 0.25;
      pointer-events: none;
      transition: opacity 0.4s ease, transform 0.4s ease;
    }

    .aura-2 {
      inset: -8px;
      border-color: rgba(255, 140, 60, 0.06);
      opacity: 0.15;
    }

    .cloud-upload-svg {
      width: 1.875rem;
      height: 1.875rem;
      overflow: visible;
      transition: transform 0.4s cubic-bezier(0.16, 1, 0.3, 1);
    }

    .arrow-group {
      transition: transform 0.4s cubic-bezier(0.16, 1, 0.3, 1);
    }

    /* Hover micro-interaction */
    .drop-target:hover .upload-icon-ring,
    .upload-icon-ring.drag-active {
      border-color: rgba(255, 85, 0, 0.45);
      background: linear-gradient(135deg, rgba(255, 240, 230, 0.85) 0%, rgba(255, 225, 210, 0.65) 100%);
      box-shadow: 0 6px 18px -2px rgba(255, 85, 0, 0.18);
      transform: translateY(-3px);
    }

    .drop-target:hover .arrow-group,
    .upload-icon-ring.drag-active .arrow-group {
      transform: translateY(-3px);
    }

    .drop-target:hover .aura-wave,
    .upload-icon-ring.drag-active .aura-wave {
      opacity: 0.55;
    }

    .drop-primary {
      font-size: 1.0625rem;
      font-weight: 700;
      color: #0f172a;
      text-align: center;
      margin: 0 0 0.375rem;
      letter-spacing: -0.015em;
    }
    .highlight-click {
      color: #ff5500;
      text-decoration: underline;
      text-underline-offset: 3px;
      text-decoration-color: rgba(255, 85, 0, 0.35);
      transition: text-decoration-color 0.2s;
    }
    .drop-target:hover .highlight-click {
      text-decoration-color: #ff5500;
    }
    .drag-text-active {
      color: #ff5500;
      font-weight: 800;
    }

    .drop-secondary {
      font-size: 0.8125rem;
      color: #94a3b8;
      margin: 0;
      text-align: center;
      font-weight: 500;
    }
    .hidden-input { display: none; }

    /* Quick Sample Chips */
    .simulate-row {
      position: relative;
      z-index: 2;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.625rem;
      padding: 0.5rem 1.5rem 1.25rem;
      flex-wrap: wrap;
    }
    .simulate-label { font-size: 0.8125rem; font-weight: 600; color: #94a3b8; flex-shrink: 0; }
    .sim-chip {
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      padding: 0.375rem 0.875rem;
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 9999px;
      font-size: 0.8125rem;
      font-weight: 600;
      color: #334155;
      cursor: pointer;
      box-shadow: 0 1px 2px rgba(0,0,0,0.03);
      transition: all 0.15s ease;
    }
    .sim-chip svg { width: 0.9375rem; height: 0.9375rem; color: #64748b; flex-shrink: 0; transition: color 0.15s ease; }
    .sim-chip:hover { border-color: #fdba74; background: #ffffff; color: #ff5500; box-shadow: 0 2px 8px rgba(255,85,0,0.12); transform: translateY(-1px); }
    .sim-chip:hover svg { color: #ff5500; }
  `],
})
export class ExplorerDropZoneComponent implements AfterViewInit, OnDestroy {
  @ViewChild('dropZoneWrapper') dropZoneWrapper?: ElementRef<HTMLDivElement>;

  currentFolderId = input<number | null>(null);
  filesSelected = output<File[]>();

  isDragOver = signal<boolean>(false);

  private ngZone = inject(NgZone);
  private targetX = 50;
  private targetY = 30;
  private currentX = 50;
  private currentY = 30;
  private animFrameId: number | null = null;
  private removeMouseMoveListener?: () => void;
  private removeMouseLeaveListener?: () => void;

  readonly simulateChips = [
    { name: 'Contract.pdf', type: 'application/pdf', size: 1024 * 1024 * 2.3 },
    { name: 'Specs.doc', type: 'application/msword', size: 1024 * 512 * 1.6 },
    { name: 'Media.zip', type: 'application/zip', size: 1024 * 1024 * 5.1 },
  ];

  ngAfterViewInit(): void {
    const el = this.dropZoneWrapper?.nativeElement;
    if (!el) return;

    this.ngZone.runOutsideAngular(() => {
      const onMove = (e: MouseEvent) => {
        const rect = el.getBoundingClientRect();
        this.targetX = ((e.clientX - rect.left) / rect.width) * 100;
        this.targetY = ((e.clientY - rect.top) / rect.height) * 100;

        if (!this.animFrameId) {
          this.startSmoothLoop();
        }
      };

      const onLeave = () => {
        this.targetX = 50;
        this.targetY = 30;
        if (!this.animFrameId) {
          this.startSmoothLoop();
        }
      };

      el.addEventListener('mousemove', onMove, { passive: true });
      el.addEventListener('mouseleave', onLeave, { passive: true });

      this.removeMouseMoveListener = () => el.removeEventListener('mousemove', onMove);
      this.removeMouseLeaveListener = () => el.removeEventListener('mouseleave', onLeave);
    });
  }

  private startSmoothLoop(): void {
    const step = () => {
      const dx = this.targetX - this.currentX;
      const dy = this.targetY - this.currentY;

      this.currentX += dx * 0.08;
      this.currentY += dy * 0.08;

      const rx = Math.round(this.currentX * 100) / 100;
      const ry = Math.round(this.currentY * 100) / 100;

      const el = this.dropZoneWrapper?.nativeElement;
      if (el) {
        el.style.setProperty('--mouse-x', `${rx}%`);
        el.style.setProperty('--mouse-y', `${ry}%`);
      }

      if (Math.abs(dx) > 0.05 || Math.abs(dy) > 0.05) {
        this.animFrameId = requestAnimationFrame(step);
      } else {
        this.currentX = this.targetX;
        this.currentY = this.targetY;
        const fx = Math.round(this.currentX * 100) / 100;
        const fy = Math.round(this.currentY * 100) / 100;
        if (el) {
          el.style.setProperty('--mouse-x', `${fx}%`);
          el.style.setProperty('--mouse-y', `${fy}%`);
        }
        this.animFrameId = null;
      }
    };
    this.animFrameId = requestAnimationFrame(step);
  }

  ngOnDestroy(): void {
    if (this.animFrameId) {
      cancelAnimationFrame(this.animFrameId);
    }
    this.removeMouseMoveListener?.();
    this.removeMouseLeaveListener?.();
  }

  simulateFile(chip: { name: string; type: string; size: number }): void {
    const dummyBuffer = new ArrayBuffer(1024);
    const blob = new Blob([dummyBuffer], { type: chip.type });
    const file = new File([blob], chip.name, { type: chip.type, lastModified: Date.now() });
    Object.defineProperty(file, 'size', { value: chip.size, writable: false });
    this.filesSelected.emit([file]);
  }

  @HostListener('document:dragenter', ['$event'])
  onDocumentDragEnter(e: DragEvent): void {
    if (e.dataTransfer?.types.includes('Files')) {
      this.isDragOver.set(true);
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
    if (e.dataTransfer?.files?.length) {
      const files = Array.from(e.dataTransfer.files);
      this.filesSelected.emit(files);
    }
  }

  onFileSelected(e: Event): void {
    const input = e.target as HTMLInputElement;
    if (input.files?.length) {
      const files = Array.from(input.files);
      this.filesSelected.emit(files);
      input.value = '';
    }
  }
}
