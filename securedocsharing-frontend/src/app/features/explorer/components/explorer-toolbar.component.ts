import { Component, ChangeDetectionStrategy, input, output, signal, HostListener, ElementRef, inject } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { ExplorerViewMode } from '../state/explorer.store';

export interface FilterOption {
  value: string;
  label: string;
}

@Component({
  selector: 'app-explorer-toolbar',
  standalone: true,
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="toolbar-container">
      <!-- Left: Filter Controls (Search, Date, Type, Size, Sort) -->
      <div class="toolbar-filters">
        <!-- Search Box -->
        <div class="search-box">
          <svg class="search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="11" cy="11" r="8"></circle>
            <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
          </svg>
          <input
            type="text"
            placeholder="Search..."
            [ngModel]="searchQuery()"
            (ngModelChange)="onSearchChange($event)"
            class="search-input"
          />
          @if (searchQuery()) {
            <button class="clear-btn" (click)="onSearchChange('')" title="Clear search">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
            </button>
          }
        </div>

        <!-- Custom Popover: Date Filter -->
        <div class="popover-wrapper">
          <button
            type="button"
            class="popover-trigger"
            [class.active]="activePopover() === 'date'"
            [class.has-value]="dateFilter() !== 'all'"
            (click)="togglePopover('date', $event)"
          >
            <span>{{ getDateLabel(dateFilter()) }}</span>
            <svg class="chevron" [class.open]="activePopover() === 'date'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="6 9 12 15 18 9"></polyline>
            </svg>
          </button>

          @if (activePopover() === 'date') {
            <div class="popover-menu">
              <div class="popover-header">Date Filter</div>
              @for (opt of dateOptions; track opt.value) {
                <button
                  type="button"
                  class="popover-item"
                  [class.selected]="dateFilter() === opt.value"
                  (click)="selectDate(opt.value)"
                >
                  <span class="item-label">{{ opt.label }}</span>
                  @if (dateFilter() === opt.value) {
                    <svg class="check-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                      <polyline points="20 6 9 17 4 12"></polyline>
                    </svg>
                  }
                </button>
              }
            </div>
          }
        </div>

        <!-- Custom Popover: Type Filter -->
        <div class="popover-wrapper">
          <button
            type="button"
            class="popover-trigger"
            [class.active]="activePopover() === 'type'"
            [class.has-value]="typeFilter() !== 'all'"
            (click)="togglePopover('type', $event)"
          >
            <span>{{ getTypeLabel(typeFilter()) }}</span>
            <svg class="chevron" [class.open]="activePopover() === 'type'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="6 9 12 15 18 9"></polyline>
            </svg>
          </button>

          @if (activePopover() === 'type') {
            <div class="popover-menu">
              <div class="popover-header">Type Filter</div>
              @for (opt of typeOptions; track opt.value) {
                <button
                  type="button"
                  class="popover-item"
                  [class.selected]="typeFilter() === opt.value"
                  (click)="selectType(opt.value)"
                >
                  <span class="item-label">{{ opt.label }}</span>
                  @if (typeFilter() === opt.value) {
                    <svg class="check-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                      <polyline points="20 6 9 17 4 12"></polyline>
                    </svg>
                  }
                </button>
              }
            </div>
          }
        </div>

        <!-- Custom Popover: Size Filter -->
        <div class="popover-wrapper">
          <button
            type="button"
            class="popover-trigger"
            [class.active]="activePopover() === 'size'"
            [class.has-value]="sizeFilter() !== 'all'"
            (click)="togglePopover('size', $event)"
          >
            <span>{{ getSizeLabel(sizeFilter()) }}</span>
            <svg class="chevron" [class.open]="activePopover() === 'size'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="6 9 12 15 18 9"></polyline>
            </svg>
          </button>

          @if (activePopover() === 'size') {
            <div class="popover-menu">
              <div class="popover-header">File Size</div>
              @for (opt of sizeOptions; track opt.value) {
                <button
                  type="button"
                  class="popover-item"
                  [class.selected]="sizeFilter() === opt.value"
                  (click)="selectSize(opt.value)"
                >
                  <span class="item-label">{{ opt.label }}</span>
                  @if (sizeFilter() === opt.value) {
                    <svg class="check-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                      <polyline points="20 6 9 17 4 12"></polyline>
                    </svg>
                  }
                </button>
              }
            </div>
          }
        </div>

        <!-- Custom Popover: Sort Option -->
        <div class="popover-wrapper">
          <button
            type="button"
            class="popover-trigger"
            [class.active]="activePopover() === 'sort'"
            (click)="togglePopover('sort', $event)"
          >
            <span>{{ getSortLabel(sortOption()) }}</span>
            <svg class="chevron" [class.open]="activePopover() === 'sort'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="6 9 12 15 18 9"></polyline>
            </svg>
          </button>

          @if (activePopover() === 'sort') {
            <div class="popover-menu">
              <div class="popover-header">Sort Order</div>
              @for (opt of sortOptions; track opt.value) {
                <button
                  type="button"
                  class="popover-item"
                  [class.selected]="sortOption() === opt.value"
                  (click)="selectSort(opt.value)"
                >
                  <span class="item-label">{{ opt.label }}</span>
                  @if (sortOption() === opt.value) {
                    <svg class="check-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                      <polyline points="20 6 9 17 4 12"></polyline>
                    </svg>
                  }
                </button>
              }
            </div>
          }
        </div>
      </div>

      <!-- Right: Action Buttons, Matches Count Badge & View Mode Switcher -->
      <div class="toolbar-right">
        @if (showCreateActions()) {
          <button
            type="button"
            class="tb-action-btn secondary"
            (click)="newFolderClicked.emit()"
            title="Create a new folder"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            <span>New Folder</span>
          </button>

          <button
            type="button"
            class="tb-action-btn primary"
            (click)="uploadFileClicked.emit()"
            title="Upload a new file"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/>
            </svg>
            <span>Upload</span>
          </button>

          <div class="divider-v"></div>
        }

        <span class="matches-badge">
          Matches: <strong>{{ totalItems() }}</strong>
        </span>

        <div class="view-toggle-group">
          <button
            class="toggle-btn"
            [class.active]="viewMode() === 'grid'"
            (click)="onViewModeSelect('grid')"
            title="Grid View"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="3" width="7" height="7"></rect>
              <rect x="14" y="3" width="7" height="7"></rect>
              <rect x="14" y="14" width="7" height="7"></rect>
              <rect x="3" y="14" width="7" height="7"></rect>
            </svg>
          </button>
          <button
            class="toggle-btn"
            [class.active]="viewMode() === 'list'"
            (click)="onViewModeSelect('list')"
            title="List View"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="8" y1="6" x2="21" y2="6"></line>
              <line x1="8" y1="12" x2="21" y2="12"></line>
              <line x1="8" y1="18" x2="21" y2="18"></line>
              <line x1="3" y1="6" x2="3.01" y2="6"></line>
              <line x1="3" y1="12" x2="3.01" y2="12"></line>
              <line x1="3" y1="18" x2="3.01" y2="18"></line>
            </svg>
          </button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .toolbar-container {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 0.75rem;
      padding: 0;
      background: transparent;
      border: none;
      border-radius: 0;
      box-shadow: none;
    }

    .toolbar-filters {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.5rem;
      flex: 1;
    }

    .search-box {
      position: relative;
      display: flex;
      align-items: center;
      width: 180px;
    }
    .search-icon {
      position: absolute;
      left: 0.625rem;
      width: 0.875rem;
      height: 0.875rem;
      color: #94a3b8;
      pointer-events: none;
    }
    .search-input {
      width: 100%;
      padding: 0.4375rem 1.75rem 0.4375rem 1.75rem;
      font-size: 0.8125rem;
      color: #1e293b;
      border: 1px solid #e2e8f0;
      border-radius: 0.375rem;
      background-color: #f8fafc;
      outline: none;
      transition: all 0.15s ease;
    }
    .search-input::placeholder { color: #94a3b8; }
    .search-input:focus {
      background-color: #ffffff;
      border-color: #ff5500;
      box-shadow: 0 0 0 2px rgba(255, 85, 0, 0.12);
    }
    .clear-btn {
      position: absolute;
      right: 0.375rem;
      background: none;
      border: none;
      cursor: pointer;
      color: #94a3b8;
      display: flex;
      align-items: center;
      padding: 0.125rem;
    }
    .clear-btn svg { width: 0.75rem; height: 0.75rem; }

    /* Popover Controls */
    .popover-wrapper {
      position: relative;
      display: inline-block;
    }

    .popover-trigger {
      display: flex;
      align-items: center;
      gap: 0.375rem;
      padding: 0.4375rem 0.625rem;
      font-size: 0.8125rem;
      font-weight: 500;
      color: #334155;
      background-color: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 0.375rem;
      cursor: pointer;
      outline: none;
      transition: all 0.15s ease;
      user-select: none;
    }
    .popover-trigger:hover {
      background-color: #ffffff;
      border-color: #cbd5e1;
    }
    .popover-trigger.active {
      background-color: #ffffff;
      border-color: #ff5500;
      box-shadow: 0 0 0 2px rgba(255, 85, 0, 0.12);
      color: #ff5500;
    }
    .popover-trigger.has-value:not(.active) {
      color: #0f172a;
      border-color: #94a3b8;
      background-color: #ffffff;
    }
    .chevron {
      width: 0.75rem;
      height: 0.75rem;
      color: #64748b;
      transition: transform 0.2s ease;
    }
    .chevron.open {
      transform: rotate(180deg);
      color: #ff5500;
    }

    .popover-menu {
      position: absolute;
      top: calc(100% + 0.375rem);
      left: 0;
      z-index: 50;
      min-width: 150px;
      padding: 0.375rem 0;
      background: #ffffff;
      border-radius: 0.5rem;
      box-shadow: 0 10px 25px -5px rgba(15, 23, 42, 0.1), 0 4px 6px -2px rgba(15, 23, 42, 0.05);
      animation: popoverFade 0.15s ease-out;
    }
    @keyframes popoverFade {
      from { opacity: 0; transform: translateY(-4px); }
      to   { opacity: 1; transform: translateY(0); }
    }

    .popover-header {
      padding: 0.25rem 0.75rem;
      font-size: 0.6875rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: #94a3b8;
      border-bottom: 1px solid #f1f5f9;
      margin-bottom: 0.25rem;
    }

    .popover-item {
      display: flex;
      align-items: center;
      justify-content: space-between;
      width: 100%;
      padding: 0.4375rem 0.75rem;
      font-size: 0.8125rem;
      color: #334155;
      background: none;
      border: none;
      cursor: pointer;
      text-align: left;
      transition: background-color 0.12s ease;
    }
    .popover-item:hover {
      background-color: #f8fafc;
      color: #0f172a;
    }
    .popover-item.selected {
      color: #ff5500;
      font-weight: 600;
      background-color: rgba(255, 85, 0, 0.05);
    }
    .check-icon {
      width: 0.875rem;
      height: 0.875rem;
      color: #ff5500;
      flex-shrink: 0;
    }

    .toolbar-right {
      display: flex;
      align-items: center;
      gap: 0.625rem;
    }

    .tb-action-btn {
      display: inline-flex;
      align-items: center;
      gap: 0.375rem;
      padding: 0.4375rem 0.75rem;
      font-size: 0.8125rem;
      font-weight: 600;
      border-radius: 0.375rem;
      cursor: pointer;
      border: none;
      transition: all 0.15s ease;
    }
    .tb-action-btn svg {
      width: 0.875rem;
      height: 0.875rem;
    }
    .tb-action-btn.secondary {
      background: #ffffff;
      color: #334155;
      border: 1px solid #cbd5e1;
    }
    .tb-action-btn.secondary:hover {
      background: #f8fafc;
      border-color: #94a3b8;
      color: #0f172a;
    }
    .tb-action-btn.primary {
      background: #ff5500;
      color: #ffffff;
      box-shadow: 0 1px 3px rgba(255, 85, 0, 0.25);
    }
    .tb-action-btn.primary:hover {
      background: #e04b00;
      box-shadow: 0 2px 6px rgba(255, 85, 0, 0.35);
    }

    .divider-v {
      width: 1px;
      height: 1.25rem;
      background-color: #e2e8f0;
      margin: 0 0.125rem;
    }

    .matches-badge {
      font-size: 0.78125rem;
      color: #64748b;
      background-color: #f1f5f9;
      padding: 0.25rem 0.625rem;
      border-radius: 0.375rem;
      white-space: nowrap;
    }
    .matches-badge strong {
      color: #0f172a;
    }

    .view-toggle-group {
      display: flex;
      background-color: #f1f5f9;
      border: 1px solid #e2e8f0;
      padding: 0.125rem;
      border-radius: 0.375rem;
      gap: 0.125rem;
    }
    .toggle-btn {
      background: none;
      border: 1px solid transparent;
      padding: 0.3125rem 0.4375rem;
      border-radius: 0.25rem;
      color: #64748b;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.15s ease;
    }
    .toggle-btn svg { width: 0.9375rem; height: 0.9375rem; }
    .toggle-btn:hover:not(.active) { color: #334155; background-color: rgba(255, 255, 255, 0.5); }
    .toggle-btn.active {
      background-color: #ffffff;
      color: #ff5500;
      border-color: #cbd5e1;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
    }
  `],
})
export class ExplorerToolbarComponent {
  private elementRef = inject(ElementRef);

  viewMode = input<ExplorerViewMode>('grid');
  searchQuery = input<string>('');
  totalItems = input<number>(0);
  showCreateActions = input<boolean>(true);

  dateFilter = input<string>('all');
  typeFilter = input<string>('all');
  sizeFilter = input<string>('all');
  sortOption = input<string>('name');

  viewModeChanged = output<ExplorerViewMode>();
  searchQueryChanged = output<string>();
  dateFilterChanged = output<string>();
  typeFilterChanged = output<string>();
  sizeFilterChanged = output<string>();
  sortOptionChanged = output<string>();
  newFolderClicked = output<void>();
  uploadFileClicked = output<void>();

  activePopover = signal<'date' | 'type' | 'size' | 'sort' | null>(null);

  readonly dateOptions: FilterOption[] = [
    { value: 'all', label: 'Date: All' },
    { value: 'today', label: 'Today' },
    { value: '7days', label: 'Last 7 days' },
    { value: '30days', label: 'Last 30 days' },
  ];

  readonly typeOptions: FilterOption[] = [
    { value: 'all', label: 'Type: All' },
    { value: 'folders', label: 'Folders' },
    { value: 'documents', label: 'Documents' },
    { value: 'pdf', label: 'PDF' },
    { value: 'images', label: 'Images' },
  ];

  readonly sizeOptions: FilterOption[] = [
    { value: 'all', label: 'Size: Any' },
    { value: 'small', label: '< 1 MB' },
    { value: 'medium', label: '1 MB - 10 MB' },
    { value: 'large', label: '> 10 MB' },
  ];

  readonly sortOptions: FilterOption[] = [
    { value: 'name', label: 'Sort: Name' },
    { value: 'date', label: 'Sort: Date' },
    { value: 'size', label: 'Sort: Size' },
  ];

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.elementRef.nativeElement.contains(event.target)) {
      this.activePopover.set(null);
    }
  }

  togglePopover(popover: 'date' | 'type' | 'size' | 'sort', event: MouseEvent): void {
    event.stopPropagation();
    if (this.activePopover() === popover) {
      this.activePopover.set(null);
    } else {
      this.activePopover.set(popover);
    }
  }

  getDateLabel(val: string): string {
    return this.dateOptions.find(o => o.value === val)?.label ?? 'Date: All';
  }

  getTypeLabel(val: string): string {
    return this.typeOptions.find(o => o.value === val)?.label ?? 'Type: All';
  }

  getSizeLabel(val: string): string {
    return this.sizeOptions.find(o => o.value === val)?.label ?? 'Size: Any';
  }

  getSortLabel(val: string): string {
    return this.sortOptions.find(o => o.value === val)?.label ?? 'Sort: Name';
  }

  selectDate(val: string): void {
    this.dateFilterChanged.emit(val);
    this.activePopover.set(null);
  }

  selectType(val: string): void {
    this.typeFilterChanged.emit(val);
    this.activePopover.set(null);
  }

  selectSize(val: string): void {
    this.sizeFilterChanged.emit(val);
    this.activePopover.set(null);
  }

  selectSort(val: string): void {
    this.sortOptionChanged.emit(val);
    this.activePopover.set(null);
  }

  onSearchChange(val: string): void {
    this.searchQueryChanged.emit(val);
  }

  onViewModeSelect(mode: ExplorerViewMode): void {
    this.viewModeChanged.emit(mode);
  }
}
