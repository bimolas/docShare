import { Component, ChangeDetectionStrategy, input, computed } from '@angular/core';
import { CommonModule } from '@angular/common';

export type UploadStatus =
  | 'pending'
  | 'hashing'
  | 'initiating'
  | 'uploading'
  | 'confirming'
  | 'completed'
  | 'error';

interface Stage {
  key: UploadStatus;
  label: string;
}

@Component({
  selector: 'app-explorer-upload-progress',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="progress-pipeline">
      <!-- Top row: Status label & percentage -->
      <div class="pipeline-header">
        <span class="status-badge" [ngClass]="'status-' + status()">
          @if (status() === 'completed') {
            <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="3 8 6 11 13 4"/>
            </svg>
          } @else if (status() === 'error') {
            <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="8" cy="8" r="6"/>
              <line x1="8" y1="5" x2="8" y2="8"/>
              <line x1="8" y1="11" x2="8.01" y2="11"/>
            </svg>
          } @else if (status() !== 'pending') {
            <span class="spinner-dot"></span>
          }
          {{ getStatusLabel() }}
        </span>

        @if (status() !== 'pending' && status() !== 'completed' && status() !== 'error') {
          <span class="pct-text">{{ progress() }}%</span>
        }
      </div>

      <!-- Stage Stepper Pipeline -->
      <div class="pipeline-stepper">
        @for (stage of stages; track stage.key; let idx = $index) {
          <div
            class="step-item"
            [class.completed]="currentStageIndex() > idx || status() === 'completed'"
            [class.active]="currentStageIndex() === idx && status() !== 'completed' && status() !== 'error'"
            [class.error]="status() === 'error' && currentStageIndex() === idx"
          >
            <div class="step-dot">
              @if (currentStageIndex() > idx || status() === 'completed') {
                <svg viewBox="0 0 12 12" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="2 6 4.5 8.5 10 3"/>
                </svg>
              } @else {
                <span class="dot-inner"></span>
              }
            </div>
            <span class="step-label">{{ stage.label }}</span>
            @if (idx < stages.length - 1) {
              <div
                class="step-line"
                [class.filled]="currentStageIndex() > idx || status() === 'completed'"
              ></div>
            }
          </div>
        }
      </div>

      <!-- Progress Fill Bar -->
      <div class="progress-bar-track">
        <div
          class="progress-bar-fill"
          [style.width.%]="status() === 'completed' ? 100 : progress()"
          [class.is-error]="status() === 'error'"
        ></div>
      </div>

      <!-- Error message -->
      @if (errorMessage() && status() === 'error') {
        <p class="error-msg">{{ errorMessage() }}</p>
      }
    </div>
  `,
  styles: [`
    .progress-pipeline {
      display: flex;
      flex-direction: column;
      gap: 0.375rem;
      width: 100%;
      margin-top: 0.25rem;
    }

    .pipeline-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .status-badge {
      display: inline-flex;
      align-items: center;
      gap: 0.375rem;
      font-size: 0.6875rem;
      font-weight: 600;
      padding: 0.125rem 0.375rem;
      border-radius: 0.25rem;
      text-transform: uppercase;
      letter-spacing: 0.03em;
    }
    .status-badge svg { width: 0.75rem; height: 0.75rem; }

    .status-pending    { background: #f1f5f9; color: #64748b; }
    .status-hashing,
    .status-initiating,
    .status-uploading,
    .status-confirming { background: #fff7ed; color: #ea580c; }
    .status-completed  { background: #f0fdf4; color: #16a34a; }
    .status-error      { background: #fef2f2; color: #dc2626; }

    .spinner-dot {
      width: 0.5rem; height: 0.5rem;
      border: 1.5px solid currentColor;
      border-top-color: transparent;
      border-radius: 50%;
      animation: spin 0.6s linear infinite;
    }
    @keyframes spin { to { transform: rotate(360deg); } }

    .pct-text {
      font-size: 0.6875rem;
      font-weight: 700;
      color: #ff5500;
      font-feature-settings: "tnum";
    }

    /* Stepper */
    .pipeline-stepper {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.25rem;
      margin: 0.125rem 0;
    }
    .step-item {
      display: flex;
      align-items: center;
      gap: 0.25rem;
      flex: 1;
      position: relative;
    }
    .step-dot {
      width: 1rem; height: 1rem;
      border-radius: 50%;
      background: #e2e8f0;
      color: #94a3b8;
      display: flex; align-items: center; justify-content: center;
      flex-shrink: 0;
      transition: all 0.2s ease;
    }
    .step-dot svg { width: 0.625rem; height: 0.625rem; }
    .dot-inner {
      width: 0.375rem; height: 0.375rem;
      border-radius: 50%;
      background: #94a3b8;
    }

    .step-item.active .step-dot {
      background: #ff5500;
      color: #ffffff;
      box-shadow: 0 0 0 3px rgba(255,85,0,0.2);
      animation: pulseDot 1.5s ease-in-out infinite;
    }
    .step-item.active .dot-inner { background: #ffffff; }
    @keyframes pulseDot {
      0%, 100% { box-shadow: 0 0 0 3px rgba(255,85,0,0.2); }
      50%       { box-shadow: 0 0 0 6px rgba(255,85,0,0.08); }
    }

    .step-item.completed .step-dot {
      background: #22c55e;
      color: #ffffff;
    }

    .step-item.error .step-dot {
      background: #ef4444;
      color: #ffffff;
    }

    .step-label {
      font-size: 0.625rem;
      font-weight: 500;
      color: #94a3b8;
      white-space: nowrap;
      transition: color 0.2s;
    }
    .step-item.active .step-label { color: #ff5500; font-weight: 700; }
    .step-item.completed .step-label { color: #16a34a; }
    .step-item.error .step-label { color: #dc2626; }

    .step-line {
      flex: 1;
      height: 2px;
      background: #e2e8f0;
      border-radius: 1px;
      transition: background 0.3s ease;
      margin: 0 0.125rem;
    }
    .step-line.filled {
      background: #22c55e;
    }

    /* Progress bar track */
    .progress-bar-track {
      height: 3px;
      background: #e2e8f0;
      border-radius: 9999px;
      overflow: hidden;
    }
    .progress-bar-fill {
      height: 100%;
      background: linear-gradient(90deg, #ff5500, #ff7733);
      border-radius: 9999px;
      transition: width 0.2s ease-out;
    }
    .progress-bar-fill.is-error {
      background: #ef4444;
    }

    .error-msg {
      font-size: 0.6875rem;
      color: #dc2626;
      margin: 0;
    }
  `],
})
export class ExplorerUploadProgressComponent {
  status = input<UploadStatus>('pending');
  progress = input<number>(0);
  errorMessage = input<string | undefined>(undefined);

  readonly stages: Stage[] = [
    { key: 'hashing', label: 'Hash' },
    { key: 'initiating', label: 'Init' },
    { key: 'uploading', label: 'Upload' },
    { key: 'confirming', label: 'Confirm' },
  ];

  currentStageIndex = computed(() => {
    const s = this.status();
    switch (s) {
      case 'hashing': return 0;
      case 'initiating': return 1;
      case 'uploading': return 2;
      case 'confirming': return 3;
      case 'completed': return 4;
      default: return -1;
    }
  });

  getStatusLabel(): string {
    switch (this.status()) {
      case 'pending': return 'Ready';
      case 'hashing': return 'Calculating Hash...';
      case 'initiating': return 'Initializing Upload...';
      case 'uploading': return 'Uploading File...';
      case 'confirming': return 'Confirming Upload...';
      case 'completed': return 'Uploaded Successfully';
      case 'error': return 'Upload Failed';
      default: return '';
    }
  }
}
