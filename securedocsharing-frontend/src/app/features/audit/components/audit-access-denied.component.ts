import { Component, ChangeDetectionStrategy, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { IconComponent } from '../../../shared/components/icon/icon.component';

@Component({
  selector: 'app-audit-access-denied',
  standalone: true,
  imports: [CommonModule, RouterLink, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="access-denied-card" role="alert">
      <div class="icon-wrap">
        <app-icon name="shield" [size]="28" class="text-amber-600" />
      </div>
      <div class="content-wrap">
        <h3 class="title">Administrative Audit Access Restricted</h3>
        <p class="description">
          {{ message() || 'Viewing organization or workspace audit logs requires administrative authorization.' }}
        </p>
        <div class="actions">
          <a routerLink="/w/personal/audit" class="btn-personal-audit">
            <app-icon name="clipboard-list" [size]="16" />
            <span>Switch to Personal Audit Trail</span>
          </a>
        </div>
      </div>
    </div>
  `,
  styles: [
    `
      .access-denied-card {
        display: flex;
        align-items: flex-start;
        gap: 1.25rem;
        background: #ffffff;
        border: 1px solid #fed7aa;
        border-radius: 10px;
        padding: 2rem;
        max-width: 640px;
        margin: 2rem auto;
        box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
      }

      .icon-wrap {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 48px;
        height: 48px;
        border-radius: 50%;
        background: #fff7ed;
        flex-shrink: 0;
      }

      .content-wrap {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
      }

      .title {
        font-size: 1.0625rem;
        font-weight: 600;
        color: #0f172a;
        margin: 0;
      }

      .description {
        font-size: 0.875rem;
        color: #64748b;
        line-height: 1.5;
        margin: 0;
      }

      .actions {
        margin-top: 0.75rem;
      }

      .btn-personal-audit {
        display: inline-flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.5rem 1rem;
        background: #f97316;
        color: #ffffff;
        border-radius: 6px;
        font-size: 0.8125rem;
        font-weight: 500;
        text-decoration: none;
        transition: background 0.15s ease;

        &:hover {
          background: #ea580c;
        }
      }
    `,
  ],
})
export class AuditAccessDeniedComponent {
  message = input<string | null>(null);
}
