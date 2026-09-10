import { Component, ChangeDetectionStrategy, input, output, signal, ElementRef, ViewChild, effect } from '@angular/core';

import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-create-folder-modal',
  standalone: true,
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (isOpen()) {
      <div class="modal-backdrop" (click)="onClose()">
        <div class="modal-card" (click)="$event.stopPropagation()">
          <!-- Header -->
          <div class="modal-header">
            <div class="header-title-group">
              <div class="folder-icon-circle">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M20 6h-8l-2-2H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2z"/>
                </svg>
              </div>
              <div>
                <h3>Create New Folder</h3>
                @if (parentFolderName()) {
                  <p class="subtitle">Inside: <span>{{ parentFolderName() }}</span></p>
                }
              </div>
            </div>
            <button class="close-btn" (click)="onClose()" title="Close">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"></line>
                <line x1="6" y1="6" x2="18" y2="18"></line>
              </svg>
            </button>
          </div>

          <!-- Body -->
          <form (submit)="onSubmit($event)" class="modal-body">
            @if (errorMessage()) {
              <div class="error-banner">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10"></circle>
                  <line x1="12" y1="8" x2="12" y2="12"></line>
                  <line x1="12" y1="16" x2="12.01" y2="16"></line>
                </svg>
                <span>{{ errorMessage() }}</span>
              </div>
            }

            <div class="form-group">
              <label for="folderNameInput">Folder Name</label>
              <input
                #folderInput
                id="folderNameInput"
                type="text"
                placeholder="e.g. Marketing Assets"
                [ngModel]="folderName()"
                (ngModelChange)="folderName.set($event); errorMessage.set(null)"
                name="folderName"
                class="form-input"
                autocomplete="off"
              />
            </div>

            <!-- Footer -->
            <div class="modal-footer">
              <button type="button" class="btn btn-secondary" (click)="onClose()" [disabled]="isSubmitting()">
                Cancel
              </button>
              <button type="submit" class="btn btn-primary" [disabled]="isSubmitting() || !folderName().trim()">
                @if (isSubmitting()) {
                  <span class="spinner"></span>
                  <span>Creating...</span>
                } @else {
                  <span>Create Folder</span>
                }
              </button>
            </div>
          </form>
        </div>
      </div>
    }
  `,
  styles: [`
    .modal-backdrop {
      position: fixed;
      inset: 0;
      background-color: rgba(15, 23, 42, 0.5);
      backdrop-filter: blur(4px);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 50;
      padding: 1rem;
      animation: fadeIn 0.15s ease-out;
    }
    @keyframes fadeIn {
      from { opacity: 0; }
      to { opacity: 1; }
    }
    .modal-card {
      background-color: #ffffff;
      border-radius: 0.875rem;
      box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
      width: 100%;
      max-width: 440px;
      overflow: hidden;
      animation: scaleUp 0.15s ease-out;
    }
    @keyframes scaleUp {
      from { transform: scale(0.95); }
      to { transform: scale(1); }
    }
    .modal-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 1.25rem 1.5rem;
      border-bottom: 1px solid #f1f5f9;
    }
    .header-title-group {
      display: flex;
      align-items: center;
      gap: 0.875rem;
    }
    .folder-icon-circle {
      width: 2.5rem;
      height: 2.5rem;
      border-radius: 0.625rem;
      background-color: #fff7ed;
      color: #ff5500;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .folder-icon-circle svg {
      width: 1.375rem;
      height: 1.375rem;
    }
    .modal-header h3 {
      font-size: 1.125rem;
      font-weight: 700;
      color: #0f172a;
      margin: 0;
    }
    .subtitle {
      font-size: 0.75rem;
      color: #64748b;
      margin-top: 0.125rem;
    }
    .subtitle span {
      font-weight: 600;
      color: #334155;
    }
    .close-btn {
      background: none;
      border: none;
      color: #94a3b8;
      cursor: pointer;
      padding: 0.375rem;
      border-radius: 0.375rem;
      transition: all 0.15s ease;
    }
    .close-btn:hover {
      background-color: #f1f5f9;
      color: #0f172a;
    }
    .close-btn svg {
      width: 1.25rem;
      height: 1.25rem;
    }
    .modal-body {
      padding: 1.5rem;
    }
    .error-banner {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.75rem;
      border-radius: 0.5rem;
      background-color: #fef2f2;
      border: 1px solid #fecaca;
      color: #ef4444;
      font-size: 0.8125rem;
      margin-bottom: 1rem;
    }
    .error-banner svg {
      width: 1rem;
      height: 1rem;
      flex-shrink: 0;
    }
    .form-group {
      display: flex;
      flex-direction: column;
      gap: 0.375rem;
    }
    .form-group label {
      font-size: 0.8125rem;
      font-weight: 600;
      color: #334155;
    }
    .form-input {
      width: 100%;
      padding: 0.625rem 0.875rem;
      font-size: 0.875rem;
      border: 1px solid #cbd5e1;
      border-radius: 0.5rem;
      outline: none;
      transition: all 0.15s ease;
    }
    .form-input:focus {
      border-color: #ff5500;
      box-shadow: 0 0 0 3px rgba(255, 85, 0, 0.12);
    }
    .modal-footer {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 0.75rem;
      margin-top: 1.5rem;
    }
    .btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      padding: 0.625rem 1.125rem;
      font-size: 0.875rem;
      font-weight: 600;
      border-radius: 0.5rem;
      cursor: pointer;
      border: none;
      transition: all 0.15s ease;
    }
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
      background-color: #ff5500;
      color: #ffffff;
    }
    .btn-primary:hover:not(:disabled) {
      background-color: #e04b00;
    }
    .btn:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }
    .spinner {
      width: 1rem;
      height: 1rem;
      border: 2px solid rgba(255, 255, 255, 0.3);
      border-top-color: #ffffff;
      border-radius: 50%;
      animation: spin 0.6s linear infinite;
    }
    @keyframes spin {
      to { transform: rotate(360deg); }
    }
  `],
})
export class CreateFolderModalComponent {
  isOpen = input<boolean>(false);
  parentFolderName = input<string>('');

  closed = output<void>();
  created = output<string>();

  folderName = signal<string>('');
  isSubmitting = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  @ViewChild('folderInput') folderInputRef?: ElementRef<HTMLInputElement>;

  constructor() {
    effect(() => {
      if (this.isOpen()) {
        this.folderName.set('');
        this.errorMessage.set(null);
        this.isSubmitting.set(false);
        setTimeout(() => this.folderInputRef?.nativeElement.focus(), 50);
      }
    });
  }

  onClose(): void {
    if (!this.isSubmitting()) {
      this.closed.emit();
    }
  }

  onSubmit(e: Event): void {
    e.preventDefault();
    const name = this.folderName().trim();
    if (!name) {
      this.errorMessage.set('Folder name cannot be empty.');
      return;
    }
    this.created.emit(name);
  }
}
