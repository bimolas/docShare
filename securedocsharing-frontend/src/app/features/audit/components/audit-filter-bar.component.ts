import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  signal,
  effect,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuditCategory, AuditScopeType } from '../../../core/models/unified-audit.model';
import { IconComponent } from '../../../shared/components/icon/icon.component';
import { DropdownComponent } from '../../../shared/ui/dropdown/dropdown.component';

@Component({
  selector: 'app-audit-filter-bar',
  standalone: true,
  imports: [CommonModule, FormsModule, IconComponent, DropdownComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="filter-bar">
      <!-- Search input -->
      <div class="search-field">
        <app-icon name="search" [size]="15" class="search-icon" />
        <input
          type="text"
          placeholder="Search activity by action, resource, details..."
          [ngModel]="searchQuery()"
          (ngModelChange)="onSearchChange($event)"
          class="search-input"
          aria-label="Search audit activity"
        />
        @if (searchQuery()) {
          <button type="button" class="clear-search-btn" (click)="onSearchChange('')" aria-label="Clear search">
            <app-icon name="x" [size]="13" />
          </button>
        }
      </div>

      <!-- Filters Row -->
      <div class="filters-group">
        <!-- Category Dropdown -->
        <div class="filter-control">
          <sd-dropdown class="filter-dropdown-host" placement="bottom-start">
            <button dropdown-trigger type="button" class="filter-select-trigger" aria-label="Filter by category">
              <span class="select-label-text">{{ getCategoryLabel() }}</span>
              <app-icon name="chevron-down" [size]="13" class="select-arrow" />
            </button>
            <div dropdown-menu class="filter-select-menu">
              <button type="button" class="filter-select-option" [class.selected]="!selectedCategory()" (click)="onCategoryChange('')">
                <span>All Categories</span>
                @if (!selectedCategory()) { <app-icon name="check" [size]="14" class="option-check" /> }
              </button>
              <button type="button" class="filter-select-option" [class.selected]="selectedCategory() === 'CONTENT'" (click)="onCategoryChange('CONTENT')">
                <span>Content &amp; Files</span>
                @if (selectedCategory() === 'CONTENT') { <app-icon name="check" [size]="14" class="option-check" /> }
              </button>
              <button type="button" class="filter-select-option" [class.selected]="selectedCategory() === 'ACCESS'" (click)="onCategoryChange('ACCESS')">
                <span>Access &amp; Permissions</span>
                @if (selectedCategory() === 'ACCESS') { <app-icon name="check" [size]="14" class="option-check" /> }
              </button>
              <button type="button" class="filter-select-option" [class.selected]="selectedCategory() === 'SHARING'" (click)="onCategoryChange('SHARING')">
                <span>External Sharing</span>
                @if (selectedCategory() === 'SHARING') { <app-icon name="check" [size]="14" class="option-check" /> }
              </button>
              <button type="button" class="filter-select-option" [class.selected]="selectedCategory() === 'SECURITY'" (click)="onCategoryChange('SECURITY')">
                <span>Security &amp; Audit</span>
                @if (selectedCategory() === 'SECURITY') { <app-icon name="check" [size]="14" class="option-check" /> }
              </button>
              <button type="button" class="filter-select-option" [class.selected]="selectedCategory() === 'TEAM'" (click)="onCategoryChange('TEAM')">
                <span>Team Governance</span>
                @if (selectedCategory() === 'TEAM') { <app-icon name="check" [size]="14" class="option-check" /> }
              </button>
              <button type="button" class="filter-select-option" [class.selected]="selectedCategory() === 'WORKSPACE'" (click)="onCategoryChange('WORKSPACE')">
                <span>Workspace Events</span>
                @if (selectedCategory() === 'WORKSPACE') { <app-icon name="check" [size]="14" class="option-check" /> }
              </button>
            </div>
          </sd-dropdown>
        </div>

        <!-- Date Preset Dropdown -->
        <div class="filter-control">
          <sd-dropdown class="filter-dropdown-host" placement="bottom-start">
            <button dropdown-trigger type="button" class="filter-select-trigger" aria-label="Filter by date range">
              <span class="select-label-text">{{ getDateLabel() }}</span>
              <app-icon name="chevron-down" [size]="13" class="select-arrow" />
            </button>
            <div dropdown-menu class="filter-select-menu">
              <button type="button" class="filter-select-option" [class.selected]="selectedDatePreset() === 'ALL'" (click)="onDatePresetChange('ALL')">
                <span>All Time</span>
                @if (selectedDatePreset() === 'ALL') { <app-icon name="check" [size]="14" class="option-check" /> }
              </button>
              <button type="button" class="filter-select-option" [class.selected]="selectedDatePreset() === 'TODAY'" (click)="onDatePresetChange('TODAY')">
                <span>Today</span>
                @if (selectedDatePreset() === 'TODAY') { <app-icon name="check" [size]="14" class="option-check" /> }
              </button>
              <button type="button" class="filter-select-option" [class.selected]="selectedDatePreset() === '7D'" (click)="onDatePresetChange('7D')">
                <span>Last 7 Days</span>
                @if (selectedDatePreset() === '7D') { <app-icon name="check" [size]="14" class="option-check" /> }
              </button>
              <button type="button" class="filter-select-option" [class.selected]="selectedDatePreset() === '30D'" (click)="onDatePresetChange('30D')">
                <span>Last 30 Days</span>
                @if (selectedDatePreset() === '30D') { <app-icon name="check" [size]="14" class="option-check" /> }
              </button>
            </div>
          </sd-dropdown>
        </div>

        <!-- Actor Filter -->
        <div class="filter-control actor-control">
          <app-icon name="user" [size]="14" class="actor-icon" />
          <input
            type="text"
            placeholder="Filter actor email..."
            [ngModel]="actorFilter()"
            (ngModelChange)="onActorChange($event)"
            class="actor-input"
            aria-label="Filter by actor email"
          />
        </div>

        <!-- Include Descendants Toggle (Visible only for FOLDER scope) -->
        @if (scopeType() === 'FOLDER') {
          <label class="descendants-toggle">
            <input
              type="checkbox"
              [checked]="includeDescendants()"
              (change)="onDescendantsChange($event)"
            />
            <span>Include subfolder activity</span>
          </label>
        }

        <!-- Reset Button -->
        @if (hasActiveFilters()) {
          <button type="button" class="btn-reset" (click)="onResetFilters()" title="Reset all filters">
            <app-icon name="x" [size]="13" />
            <span>Reset</span>
          </button>
        }
      </div>
    </div>
  `,
  styles: [
    `
      .filter-bar {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        justify-content: space-between;
        gap: 0.75rem;
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
        padding: 0.65rem 0.85rem;
      }

      .search-field {
        position: relative;
        display: flex;
        align-items: center;
        flex: 1;
        min-width: 260px;
      }

      .search-icon {
        position: absolute;
        left: 0.65rem;
        color: #94a3b8;
        pointer-events: none;
      }

      .search-input {
        width: 100%;
        padding: 0.4rem 2rem 0.4rem 2.1rem;
        font-size: 0.8125rem;
        color: #0f172a;
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 6px;
        outline: none;
        transition: all 0.15s ease;

        &:focus {
          background: #ffffff;
          border-color: #f97316;
          box-shadow: 0 0 0 2px rgba(249, 115, 22, 0.15);
        }

        &::placeholder {
          color: #94a3b8;
        }
      }

      .clear-search-btn {
        position: absolute;
        right: 0.5rem;
        background: transparent;
        border: none;
        color: #94a3b8;
        cursor: pointer;
        padding: 2px;
        display: flex;
        align-items: center;
        justify-content: center;

        &:hover {
          color: #475569;
        }
      }

      .filters-group {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 0.5rem;
      }

      .filter-control {
        display: flex;
        align-items: center;
      }

      ::ng-deep .filter-dropdown-host,
      ::ng-deep .filter-dropdown-host .sd-dropdown-container {
        display: inline-block;
        width: auto;
      }

      .filter-select-trigger {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        padding: 6px 10px 6px 12px;
        border: 1px solid #cbd5e1;
        border-radius: 6px;
        background: #ffffff;
        color: #334155;
        font-size: 12.5px;
        font-weight: 500;
        cursor: pointer;
        min-height: 32px;
        outline: none;
        transition: border-color 0.15s, box-shadow 0.15s;
        white-space: nowrap;
        font-family: inherit;

        &:hover {
          border-color: #94a3b8;
          background: #f8fafc;
        }

        &[aria-expanded="true"] {
          border-color: var(--sd-color-brand-focus, #fb923c);
          box-shadow: 0 0 0 2px rgba(251, 146, 60, 0.15);
        }

        &[aria-expanded="true"] .select-arrow {
          transform: rotate(180deg);
        }
      }

      .select-arrow {
        color: #64748b;
        transition: transform 0.15s ease;
        flex-shrink: 0;
      }

      ::ng-deep .filter-dropdown-host .sd-dropdown-menu {
        min-width: 180px;
      }

      .filter-select-menu {
        display: flex;
        flex-direction: column;
        gap: 2px;
        width: 100%;
      }

      .filter-select-option {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
        width: 100%;
        padding: 7px 10px;
        border: none;
        background: transparent;
        border-radius: 6px;
        cursor: pointer;
        text-align: left;
        font-size: 13px;
        color: #334155;
        font-family: inherit;
        transition: background 0.1s;

        &:hover {
          background: #f1f5f9;
        }

        &.selected {
          background: rgba(249, 115, 22, 0.07);
          color: var(--sd-color-primary, #f97316);
          font-weight: 600;
        }
      }

      .option-check {
        color: var(--sd-color-primary, #f97316);
        flex-shrink: 0;
      }

      .actor-control {
        position: relative;
        min-width: 150px;
      }

      .actor-icon {
        position: absolute;
        left: 0.55rem;
        color: #94a3b8;
        pointer-events: none;
      }

      .actor-input {
        width: 100%;
        padding: 0.4rem 0.5rem 0.4rem 1.85rem;
        font-size: 0.8125rem;
        color: #0f172a;
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 6px;
        outline: none;
        transition: all 0.15s ease;

        &:focus {
          background: #ffffff;
          border-color: #f97316;
          box-shadow: 0 0 0 2px rgba(249, 115, 22, 0.15);
        }

        &::placeholder {
          color: #94a3b8;
        }
      }

      .descendants-toggle {
        display: inline-flex;
        align-items: center;
        gap: 0.35rem;
        font-size: 0.75rem;
        color: #475569;
        cursor: pointer;
        user-select: none;

        input[type="checkbox"] {
          accent-color: #f97316;
        }
      }

      .btn-reset {
        display: inline-flex;
        align-items: center;
        gap: 0.25rem;
        padding: 0.38rem 0.65rem;
        background: #fff1ec;
        border: 1px solid #fed7aa;
        border-radius: 6px;
        font-size: 0.75rem;
        font-weight: 500;
        color: #ea580c;
        cursor: pointer;
        transition: all 0.15s ease;

        &:hover {
          background: #ffedd5;
        }
      }
    `,
  ],
})
export class AuditFilterBarComponent {
  scopeType = input<AuditScopeType>('PERSONAL');
  hasActiveFilters = input<boolean>(false);

  initialSearch = input<string>('');
  initialCategory = input<AuditCategory | undefined>(undefined);
  initialActor = input<string>('');

  filtersChange = output<{
    category?: AuditCategory;
    actor?: string;
    search?: string;
    from?: string;
    to?: string;
    includeDescendants?: boolean;
  }>();

  resetFilters = output<void>();

  searchQuery = signal<string>('');
  selectedCategory = signal<AuditCategory | undefined>(undefined);
  selectedDatePreset = signal<'ALL' | 'TODAY' | '7D' | '30D'>('ALL');
  actorFilter = signal<string>('');
  includeDescendants = signal<boolean>(false);

  private searchDebounceTimer: any;

  constructor() {
    effect(() => {
      this.searchQuery.set(this.initialSearch() || '');
    });
    effect(() => {
      this.selectedCategory.set(this.initialCategory());
    });
    effect(() => {
      this.actorFilter.set(this.initialActor() || '');
    });
  }

  onSearchChange(val: string): void {
    this.searchQuery.set(val);
    clearTimeout(this.searchDebounceTimer);
    this.searchDebounceTimer = setTimeout(() => {
      this.emitCurrentFilters();
    }, 300);
  }

  onCategoryChange(val: string): void {
    const cat = (val ? val : undefined) as AuditCategory | undefined;
    this.selectedCategory.set(cat);
    this.emitCurrentFilters();
  }

  onDatePresetChange(preset: 'ALL' | 'TODAY' | '7D' | '30D'): void {
    this.selectedDatePreset.set(preset);
    this.emitCurrentFilters();
  }

  onActorChange(val: string): void {
    this.actorFilter.set(val);
    clearTimeout(this.searchDebounceTimer);
    this.searchDebounceTimer = setTimeout(() => {
      this.emitCurrentFilters();
    }, 350);
  }

  onDescendantsChange(e: Event): void {
    const checked = (e.target as HTMLInputElement).checked;
    this.includeDescendants.set(checked);
    this.emitCurrentFilters();
  }

  onResetFilters(): void {
    this.searchQuery.set('');
    this.selectedCategory.set(undefined);
    this.selectedDatePreset.set('ALL');
    this.actorFilter.set('');
    this.includeDescendants.set(false);
    this.resetFilters.emit();
  }

  getCategoryLabel(): string {
    switch (this.selectedCategory()) {
      case 'CONTENT': return 'Content & Files';
      case 'ACCESS': return 'Access & Permissions';
      case 'SHARING': return 'External Sharing';
      case 'SECURITY': return 'Security & Audit';
      case 'TEAM': return 'Team Governance';
      case 'WORKSPACE': return 'Workspace Events';
      default: return 'All Categories';
    }
  }

  getDateLabel(): string {
    switch (this.selectedDatePreset()) {
      case 'TODAY': return 'Today';
      case '7D': return 'Last 7 Days';
      case '30D': return 'Last 30 Days';
      default: return 'All Time';
    }
  }

  private emitCurrentFilters(): void {
    let from: string | undefined;
    let to: string | undefined;
    const now = new Date();

    switch (this.selectedDatePreset()) {
      case 'TODAY': {
        const startOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate());
        from = startOfDay.toISOString();
        break;
      }
      case '7D': {
        const d = new Date(now);
        d.setDate(d.getDate() - 7);
        from = d.toISOString();
        break;
      }
      case '30D': {
        const d = new Date(now);
        d.setDate(d.getDate() - 30);
        from = d.toISOString();
        break;
      }
      case 'ALL':
      default:
        from = undefined;
        to = undefined;
        break;
    }

    this.filtersChange.emit({
      search: this.searchQuery() || undefined,
      category: this.selectedCategory(),
      actor: this.actorFilter() || undefined,
      from,
      to,
      includeDescendants: this.scopeType() === 'FOLDER' ? this.includeDescendants() : undefined,
    });
  }
}
