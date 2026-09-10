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
import { LucideBan, LucideAlertCircle } from '@lucide/angular';
import { ModalComponent } from '../../../../shared/ui/modal/modal.component';
import { OrganizationApiService } from '../../../../core/services/organization-api.service';
import { NotificationService } from '../../../../core/services/notification.service';
import {
  OrganizationMemberDetailResponseDto,
  MemberStatusChangeRequestDto,
} from '../../../../core/models/organization.model';
import { extractErrorMessage } from '../../../../core/models/app-error.model';

@Component({
  selector: 'app-suspend-member-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideBan, LucideAlertCircle, ModalComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <sd-modal
      [open]="open()"
      title="Suspend Member"
      subtitle="Temporarily suspend organization membership and access."
      size="sm"
      [closeOnBackdrop]="!submitting()"
      (closed)="onClose()"
    >
      @if (member()) {
        <div class="space-y-4 py-2">
          <!-- Member Identification -->
          <div class="flex items-center gap-3 p-3 rounded-lg bg-muted/50 border border-border">
            <div class="w-9 h-9 rounded-full bg-amber-500/10 text-amber-700 dark:text-amber-400 border border-amber-500/20 flex items-center justify-center font-semibold text-xs shrink-0">
              {{ getInitials() }}
            </div>
            <div class="min-w-0 flex-1">
              <div class="text-sm font-medium text-foreground truncate">{{ getDisplayName() }}</div>
              <div class="text-xs text-muted-foreground font-mono truncate">{{ member()?.email }}</div>
            </div>
          </div>

          <!-- Last Admin Guard Warning -->
          @if (isLastAdminSuspensionBlocked()) {
            <div class="flex items-start gap-2.5 p-3 rounded-lg bg-destructive/10 border border-destructive/20 text-destructive text-xs">
              <svg lucideAlertCircle class="w-4 h-4 shrink-0 mt-0.5"></svg>
              <p>
                <strong>Sole Administrator:</strong> You cannot suspend the last active administrator of this organization. Promote another administrator or transfer administration before suspending this account.
              </p>
            </div>
          } @else {
            <!-- Explanatory Warning -->
            <div class="flex items-start gap-2.5 p-3 rounded-lg bg-amber-500/10 border border-amber-500/30 text-amber-900 dark:text-amber-300 text-xs">
              <svg lucideBan class="w-4 h-4 text-amber-600 dark:text-amber-400 shrink-0 mt-0.5"></svg>
              <p>
                <strong>{{ getDisplayName() }}</strong> will temporarily lose access to all organization resources, workspaces, and teams. Their team assignments and permissions will be preserved. An administrator can reactivate this account at any time.
              </p>
            </div>

            <!-- Reason Textarea -->
            <div class="space-y-1.5">
              <label for="suspend-reason-input" class="block text-xs font-semibold text-foreground uppercase tracking-wider">
                Reason for suspension <span class="text-muted-foreground font-normal lowercase">(optional)</span>
              </label>
              <textarea
                id="suspend-reason-input"
                rows="3"
                class="w-full px-3 py-2 rounded-lg border border-border bg-card text-foreground text-xs focus:outline-hidden focus:ring-2 focus:ring-amber-500/20 resize-none shadow-xs"
                placeholder="Document the operational or security reason for suspension..."
                [value]="reason()"
                (input)="reason.set($any($event.target).value)"
                [disabled]="submitting()"
              ></textarea>
            </div>
          }

          <!-- Error Alert Banner -->
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
          class="px-4 py-2 rounded-lg bg-amber-600 text-white hover:bg-amber-700 text-xs font-medium shadow-xs transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-1.5"
          [disabled]="submitting() || isLastAdminSuspensionBlocked() || !member()"
          (click)="onConfirm()"
        >
          @if (submitting()) {
            <span class="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
            Suspending...
          } @else {
            Suspend Member
          }
        </button>
      </div>
    </sd-modal>
  `,
})
export class SuspendMemberModalComponent {
  private readonly orgApiService = inject(OrganizationApiService);
  private readonly notificationService = inject(NotificationService);

  readonly open = input<boolean>(false);
  readonly member = input<OrganizationMemberDetailResponseDto | null>(null);
  readonly orgCode = input<string>('');
  readonly activeAdminCount = input<number>(1);

  readonly closed = output<void>();
  readonly memberSuspended = output<OrganizationMemberDetailResponseDto>();

  readonly reason = signal<string>('');
  readonly submitting = signal<boolean>(false);
  readonly error = signal<string | null>(null);

  readonly isLastAdminSuspensionBlocked = computed(() => {
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

    if (!mem || !mem.userId || !org || this.submitting() || this.isLastAdminSuspensionBlocked()) {
      return;
    }

    this.submitting.set(true);
    this.error.set(null);

    const payload: MemberStatusChangeRequestDto = {
      reason: this.reason().trim() || undefined,
    };

    this.orgApiService.suspendMember(org, mem.userId, payload).subscribe({
      next: (updated) => {
        this.submitting.set(false);
        this.notificationService.success(
          `${this.getDisplayName()} has been suspended.`
        );
        this.memberSuspended.emit(updated);
        this.closed.emit();
      },
      error: (err) => {
        this.submitting.set(false);
        const msg = extractErrorMessage(
          err,
          'Failed to suspend member. Please try again.'
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
