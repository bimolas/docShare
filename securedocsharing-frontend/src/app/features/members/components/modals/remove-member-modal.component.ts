import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  signal,
  computed,
  inject,
  effect,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LucideTrash2, LucideAlertCircle } from '@lucide/angular';
import { ModalComponent } from '../../../../shared/ui/modal/modal.component';
import { OrganizationApiService } from '../../../../core/services/organization-api.service';
import { NotificationService } from '../../../../core/services/notification.service';
import {
  OrganizationMemberDetailResponseDto,
  MemberStatusChangeRequestDto,
} from '../../../../core/models/organization.model';
import { extractErrorMessage } from '../../../../core/models/app-error.model';

@Component({
  selector: 'app-remove-member-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideTrash2, LucideAlertCircle, ModalComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <sd-modal
      [open]="open()"
      title="Remove Member"
      subtitle="Permanently remove this member from the organization."
      size="sm"
      [closeOnBackdrop]="!submitting()"
      (closed)="onClose()"
    >
      @if (member()) {
        <div class="space-y-4 py-2">
          <!-- Member Identification -->
          <div class="flex items-center gap-3 p-3 rounded-lg bg-muted/50 border border-border">
            <div class="w-9 h-9 rounded-full bg-destructive/10 text-destructive border border-destructive/20 flex items-center justify-center font-semibold text-xs shrink-0">
              {{ getInitials() }}
            </div>
            <div class="min-w-0 flex-1">
              <div class="text-sm font-medium text-foreground truncate">{{ getDisplayName() }}</div>
              <div class="text-xs text-muted-foreground font-mono truncate">{{ member()?.email }}</div>
            </div>
          </div>

          <!-- Last Admin Guard Warning -->
          @if (isLastAdminRemovalBlocked()) {
            <div class="flex items-start gap-2.5 p-3 rounded-lg bg-destructive/10 border border-destructive/20 text-destructive text-xs">
              <svg lucideAlertCircle class="w-4 h-4 shrink-0 mt-0.5"></svg>
              <p>
                <strong>Sole Administrator:</strong> You cannot remove the last active administrator of this organization. Promote another administrator or transfer administration before removing this account.
              </p>
            </div>
          } @else {
            <!-- High-Danger Cascading Notice -->
            <div class="p-3 rounded-lg bg-destructive/10 border border-destructive/20 text-destructive text-xs space-y-1.5">
              <div class="flex items-center gap-1.5 font-semibold">
                <svg lucideAlertCircle class="w-4 h-4 shrink-0"></svg>
                <span>Warning: Irreversible Cascading Deletion</span>
              </div>
              <p class="leading-relaxed">
                Removing <strong>{{ getDisplayName() }}</strong> will permanently terminate their organization membership and automatically trigger:
              </p>
              <ul class="list-disc list-inside space-y-0.5 text-[11px] opacity-90 pl-1">
                <li>Immediate deletion from all organization teams and groups</li>
                <li>Revocation of all workspace manager delegations</li>
                <li>Cleanup of shared collaboration access</li>
              </ul>
            </div>

            <!-- Required Reason Textarea -->
            <div class="space-y-1.5">
              <label for="remove-reason-input" class="block text-xs font-semibold text-foreground uppercase tracking-wider">
                Reason for removal <span class="text-destructive font-bold">*</span>
              </label>
              <textarea
                id="remove-reason-input"
                rows="3"
                class="w-full px-3 py-2 rounded-lg border border-border bg-card text-foreground text-xs focus:outline-hidden focus:ring-2 focus:ring-destructive/20 resize-none shadow-xs"
                placeholder="Required: Provide audit justification for member removal..."
                [value]="reason()"
                (input)="reason.set($any($event.target).value)"
                [disabled]="submitting()"
              ></textarea>
              @if (!reason().trim()) {
                <span class="text-[11px] text-muted-foreground">A justification reason is required for organization audit logs.</span>
              }
            </div>
          }

          <!-- Server Error Banner -->
          @if (error()) {
            <div class="flex items-start gap-2.5 p-3 rounded-lg bg-destructive/10 border border-destructive/20 text-destructive text-xs">
              <svg lucideAlertCircle class="w-4 h-4 shrink-0 mt-0.5"></svg>
              <p>{{ error() }}</p>
            </div>
          }
        </div>
      }

      <div modal-footer class="flex items-center justify-end gap-2.5 pt-2">
        <button
          type="button"
          class="px-4 py-2 rounded-lg border border-border bg-card text-foreground hover:bg-muted text-xs font-medium transition-colors"
          [disabled]="submitting()"
          (click)="onClose()"
        >
          Cancel
        </button>
        <button
          type="button"
          class="px-4 py-2 rounded-lg bg-destructive text-destructive-foreground hover:bg-destructive/90 text-xs font-medium shadow-xs transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-1.5"
          [disabled]="submitting() || !reason().trim() || isLastAdminRemovalBlocked() || !member()"
          (click)="onConfirm()"
        >
          @if (submitting()) {
            <span class="w-3.5 h-3.5 border-2 border-destructive-foreground border-t-transparent rounded-full animate-spin"></span>
            Removing...
          } @else {
            <svg lucideTrash2 class="w-3.5 h-3.5"></svg>
            Remove Member
          }
        </button>
      </div>
    </sd-modal>
  `,
})
export class RemoveMemberModalComponent {
  private readonly orgApiService = inject(OrganizationApiService);
  private readonly notificationService = inject(NotificationService);

  readonly open = input<boolean>(false);
  readonly member = input<OrganizationMemberDetailResponseDto | null>(null);
  readonly orgCode = input<string>('');
  readonly activeAdminCount = input<number>(1);

  readonly closed = output<void>();
  readonly memberRemoved = output<OrganizationMemberDetailResponseDto>();

  readonly reason = signal<string>('');
  readonly submitting = signal<boolean>(false);
  readonly error = signal<string | null>(null);

  readonly isLastAdminRemovalBlocked = computed(() => {
    const mem = this.member();
    if (!mem) return false;
    const role = (mem.role || '').toUpperCase();
    const isAdmin = role.includes('ADMIN');
    const isActive = mem.status === 'ACTIVE';
    return isAdmin && isActive && this.activeAdminCount() <= 1;
  });

  constructor() {
    effect(
      () => {
        if (this.open()) {
          this.reason.set('');
          this.error.set(null);
          this.submitting.set(false);
        }
      },
      { allowSignalWrites: true }
    );
  }

  onClose(): void {
    if (this.submitting()) return;
    this.closed.emit();
  }

  onConfirm(): void {
    const mem = this.member();
    const org = this.orgCode();
    const r = this.reason().trim();

    if (!mem || !mem.userId || !org || this.submitting() || !r || this.isLastAdminRemovalBlocked()) {
      return;
    }

    this.submitting.set(true);
    this.error.set(null);

    const payload: MemberStatusChangeRequestDto = { reason: r };

    this.orgApiService.removeMember(org, mem.userId, payload).subscribe({
      next: (updated) => {
        this.submitting.set(false);
        this.notificationService.success(
          `${this.getDisplayName()} has been removed from the organization.`
        );
        this.memberRemoved.emit(updated);
        this.closed.emit();
      },
      error: (err) => {
        this.submitting.set(false);
        const msg = extractErrorMessage(
          err,
          'Failed to remove member. Please try again.'
        );
        this.error.set(msg);
      },
    });
  }

  getDisplayName(): string {
    const m = this.member();
    if (!m) return '';
    const fn = m.firstName?.trim() || '';
    const ln = m.lastName?.trim() || '';
    if (fn || ln) return `${fn} ${ln}`.trim();
    return m.email;
  }

  getInitials(): string {
    const m = this.member();
    if (!m) return 'U';
    const fn = m.firstName?.trim() || '';
    const ln = m.lastName?.trim() || '';
    if (fn && ln) return `${fn.charAt(0)}${ln.charAt(0)}`.toUpperCase();
    if (fn) return fn.slice(0, 2).toUpperCase();
    if (m.email) return m.email.slice(0, 2).toUpperCase();
    return 'U';
  }
}
