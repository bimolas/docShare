import { Component, ChangeDetectionStrategy, input, output, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SharedItem } from '../models/sharing.model';
import { IconComponent, IconName } from '../../../shared/components/icon/icon.component';

@Component({
  selector: 'app-source-badge',
  standalone: true,
  imports: [CommonModule, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="source-badge-container">
      <span [class]="badgeClasses()" [attr.title]="tooltipText()">
        <app-icon [name]="iconName()" [size]="12" class="badge-icon" />
        <span class="badge-label">{{ displayLabel() }}</span>
      </span>

      @if (hasMultiGrant() && showMultiGrant()) {
        <button
          type="button"
          class="multi-grant-pill"
          (click)="onMultiGrantClick($event)"
          [attr.aria-label]="'View all ' + item().matchingGrantCount + ' access sources'"
          [title]="'Click to view all ' + item().matchingGrantCount + ' access sources'"
        >
          <span class="multi-grant-count">+{{ (item().matchingGrantCount ?? 1) - 1 }}</span>
          <app-icon name="layers" [size]="11" />
        </button>
      }
    </div>
  `,
  styles: [`
    :host {
      display: inline-flex;
      align-items: center;
    }

    .source-badge-container {
      display: inline-flex;
      align-items: center;
      gap: 5px;
    }

    .sd-source-badge {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      padding: 3px 8px;
      border-radius: var(--sd-radius-full, 9999px);
      font-size: 11px;
      font-weight: 500;
      line-height: 1;
      white-space: nowrap;
      border: 1px solid transparent;
      user-select: none;
    }

    /* ── Semantic Variants ── */
    .sd-source--direct {
      background-color: #ecfdf5;
      color: #047857;
      border-color: #a7f3d0;
    }

    .sd-source--team {
      background-color: #eff6ff;
      color: #1d4ed8;
      border-color: #bfdbfe;
    }

    .sd-source--organization {
      background-color: #fff7ed;
      color: #c2410c;
      border-color: #fed7aa;
    }

    .sd-source--collaboration {
      background-color: #faf5ff;
      color: #7e22ce;
      border-color: #e9d5ff;
    }

    .sd-source--generic {
      background-color: #f1f5f9;
      color: #475569;
      border-color: #cbd5e1;
    }

    .badge-icon {
      flex-shrink: 0;
    }

    .badge-label {
      max-width: 160px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    /* ── Multi-Grant Indicator Pill ── */
    .multi-grant-pill {
      display: inline-flex;
      align-items: center;
      gap: 3px;
      padding: 2px 6px;
      background: #f8fafc;
      border: 1px solid #cbd5e1;
      border-radius: var(--sd-radius-full, 9999px);
      color: #334155;
      font-size: 10.5px;
      font-weight: 600;
      font-family: var(--sd-font-sans, inherit);
      cursor: pointer;
      transition: all 0.15s ease;
      outline: none;
    }

    .multi-grant-pill:hover {
      background: #f1f5f9;
      border-color: #94a3b8;
      color: #0f172a;
      transform: translateY(-0.5px);
    }

    .multi-grant-pill:focus-visible {
      outline: 2px solid var(--sd-color-brand-focus, #fb923c);
      outline-offset: 1px;
    }

    .multi-grant-count {
      font-variant-numeric: tabular-nums;
    }
  `],
})
export class SourceBadgeComponent {
  item = input.required<SharedItem>();
  showMultiGrant = input<boolean>(true);
  multiGrantClicked = output<SharedItem>();

  readonly normalizedLabel = computed(() => {
    return (this.item().sourceLabel || '').toUpperCase();
  });

  readonly hasMultiGrant = computed(() => {
    const count = this.item().matchingGrantCount;
    return typeof count === 'number' && count > 1;
  });

  readonly iconName = computed<IconName>(() => {
    const label = this.normalizedLabel();
    if (label.includes('DIRECT')) return 'user';
    if (label.includes('TEAM')) return 'users-round';
    if (label.includes('ORGANIZATION') || label.includes('ORG')) return 'building-2';
    if (label.includes('COLLABORATION')) return 'share-2';
    return 'link';
  });

  readonly displayLabel = computed(() => {
    const item = this.item();
    const label = this.normalizedLabel();
    if (label.includes('DIRECT')) {
      return 'Direct Share';
    }
    if (label.includes('TEAM')) {
      return item.targetGroupName ? `Team: ${item.targetGroupName}` : 'Team Share';
    }
    if (label.includes('ORGANIZATION') || label.includes('ORG')) {
      return item.targetOrgName
        ? `Org: ${item.targetOrgName}`
        : item.targetOrgCode
          ? `Org: ${item.targetOrgCode}`
          : 'Organization Share';
    }
    if (label.includes('COLLABORATION')) {
      return item.collaborationName ? `Collab: ${item.collaborationName}` : 'Collaboration Share';
    }
    return item.sourceLabel || 'Explicit Share';
  });

  readonly tooltipText = computed(() => {
    const item = this.item();
    if (item.sourceDescription) {
      return item.sourceDescription;
    }
    return this.displayLabel();
  });

  badgeClasses(): string {
    const label = this.normalizedLabel();
    let variantClass = 'sd-source--generic';
    if (label.includes('DIRECT')) variantClass = 'sd-source--direct';
    else if (label.includes('TEAM')) variantClass = 'sd-source--team';
    else if (label.includes('ORGANIZATION') || label.includes('ORG')) variantClass = 'sd-source--organization';
    else if (label.includes('COLLABORATION')) variantClass = 'sd-source--collaboration';

    return `sd-source-badge ${variantClass}`;
  }

  onMultiGrantClick(event: Event): void {
    event.stopPropagation();
    this.multiGrantClicked.emit(this.item());
  }
}
