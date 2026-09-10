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
import {
  LucideShield,
  LucideAlertCircle,
  LucideCheck,
  LucideChevronDown,
} from '@lucide/angular';
import { ModalComponent } from '../../../../shared/ui/modal/modal.component';
import { DropdownComponent } from '../../../../shared/ui/dropdown/dropdown.component';
import { OrganizationApiService } from '../../../../core/services/organization-api.service';
import { NotificationService } from '../../../../core/services/notification.service';
import {
  OrganizationMemberDetailResponseDto,
  UpdateMemberRoleRequestDto,
} from '../../../../core/models/organization.model';
import { extractErrorMessage } from '../../../../core/models/app-error.model';

export type AllowedOrgRole = 'ROLE_USER' | 'ROLE_ORG_MANAGER' | 'ROLE_ORG_ADMIN';

interface RoleOption {
  value: AllowedOrgRole;
  label: string;
  description: string;
}

@Component({
  selector: 'app-change-role-modal',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    LucideShield,
    LucideAlertCircle,
    LucideCheck,
    LucideChevronDown,
    ModalComponent,
    DropdownComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <sd-modal
      [open]="open()"
      title="Change Organization Role"
      subtitle="Update this member's organization-level role."
      size="sm"
      [closeOnBackdrop]="!submitting()"
      (closed)="onClose()"
    >
      @if (member()) {
        <div class="space-y-4 py-2">
          <!-- Member Info Pill -->
          <div class="flex items-center gap-3 p-3 rounded-lg bg-muted/50 border border-border">
            <div class="w-9 h-9 rounded-full bg-muted text-muted-foreground border border-border flex items-center justify-center font-semibold text-xs shrink-0">
              {{ getInitials() }}
            </div>
            <div class="min-w-0 flex-1">
              <div class="text-sm font-medium text-foreground truncate">{{ getDisplayName() }}</div>
              <div class="text-xs text-muted-foreground font-mono truncate">{{ member()?.email }}</div>
            </div>
          </div>

          <!-- Current Role Display -->
          <div class="flex items-center justify-between text-sm py-1 border-b border-border/50">
            <span class="text-muted-foreground font-medium">Current Role</span>
            <span class="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium border" [class]="getRoleBadgeClass(currentRole())">
              <svg lucideShield class="w-3.5 h-3.5"></svg>
              {{ formatRole(currentRole()) }}
            </span>
          </div>

          <!-- New Role Selection -->
          <div class="space-y-1.5">
            <label class="block text-xs font-semibold text-foreground uppercase tracking-wider">
              New Role
            </label>
            <sd-dropdown placement="bottom-start" class="w-full">
              <button
                dropdown-trigger
                type="button"
                class="w-full flex items-center justify-between px-3.5 py-2 rounded-lg border border-border bg-card text-foreground hover:bg-muted/40 transition-colors text-sm font-medium shadow-xs focus:outline-hidden focus:ring-2 focus:ring-primary/20"
                [disabled]="submitting() || isActionBlocked()"
              >
                <span>{{ formatRole(selectedRole()) }}</span>
                <svg lucideChevronDown class="w-4 h-4 text-muted-foreground"></svg>
              </button>
              <div dropdown-menu class="w-72 p-1 bg-popover rounded-lg border border-border shadow-lg space-y-0.5">
                @for (opt of availableRoleOptions(); track opt.value) {
                  <button
                    type="button"
                    class="w-full text-left p-2 rounded-md transition-colors flex items-start justify-between gap-2 text-sm"
                    [class.bg-primary-50]="selectedRole() === opt.value"
                    [class.dark:bg-primary-950/30]="selectedRole() === opt.value"
                    [class.hover:bg-muted]="selectedRole() !== opt.value"
                    (click)="selectedRole.set(opt.value)"
                  >
                    <div class="min-w-0">
                      <div class="font-medium text-foreground text-xs">{{ opt.label }}</div>
                      <div class="text-[11px] text-muted-foreground leading-tight mt-0.5">{{ opt.description }}</div>
                    </div>
                    @if (selectedRole() === opt.value) {
                      <svg lucideCheck class="w-4 h-4 text-primary shrink-0 mt-0.5"></svg>
                    }
                  </button>
                }
              </div>
            </sd-dropdown>
          </div>

          <!-- Last Admin Guard Warning -->
          @if (isLastAdminDemotionBlocked()) {
            <div class="flex items-start gap-2.5 p-3 rounded-lg bg-amber-500/10 border border-amber-500/30 text-amber-800 dark:text-amber-300 text-xs">
              <svg lucideAlertCircle class="w-4 h-4 text-amber-600 dark:text-amber-400 shrink-0 mt-0.5"></svg>
              <p>
                <strong>Sole Administrator:</strong> This organization must retain at least one active administrator. Promote another administrator or transfer administration before demoting this account.
              </p>
            </div>
          }

          <!-- Non-active Member Warning -->
          @if (isNonActiveMemberRoleBlocked()) {
            <div class="flex items-start gap-2.5 p-3 rounded-lg bg-amber-500/10 border border-amber-500/30 text-amber-800 dark:text-amber-300 text-xs">
              <svg lucideAlertCircle class="w-4 h-4 text-amber-600 dark:text-amber-400 shrink-0 mt-0.5"></svg>
              <p>
                Non-active members (current status: <strong>{{ member()?.status }}</strong>) cannot be assigned administrative or managerial roles. Reactivate the member first.
              </p>
            </div>
          }

          <!-- Explanation Banner -->
          @if (selectedRole() !== currentRole() && !isActionBlocked()) {
            <div class="flex items-start gap-2.5 p-3 rounded-lg bg-blue-500/10 border border-blue-500/20 text-blue-900 dark:text-blue-300 text-xs">
              <svg lucideShield class="w-4 h-4 text-blue-600 dark:text-blue-400 shrink-0 mt-0.5"></svg>
              <p>
                <strong>{{ getDisplayName() }}</strong> will be changed from
                <em>{{ formatRole(currentRole()) }}</em> to <em>{{ formatRole(selectedRole()) }}</em>.
                This change takes effect immediately and will be recorded in the audit log.
              </p>
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
          class="px-4 py-2 rounded-lg bg-primary text-primary-foreground hover:bg-primary/90 text-xs font-medium shadow-xs transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-1.5"
          [disabled]="submitting() || selectedRole() === currentRole() || isActionBlocked() || !member()"
          (click)="onConfirm()"
        >
          @if (submitting()) {
            <span class="w-3.5 h-3.5 border-2 border-primary-foreground border-t-transparent rounded-full animate-spin"></span>
            Updating...
          } @else {
            Update Role
          }
        </button>
      </div>
    </sd-modal>
  `,
})
export class ChangeRoleModalComponent {
  private readonly orgApiService = inject(OrganizationApiService);
  private readonly notificationService = inject(NotificationService);

  readonly open = input<boolean>(false);
  readonly member = input<OrganizationMemberDetailResponseDto | null>(null);
  readonly orgCode = input<string>('');
  readonly currentUserRole = input<string | null>(null);
  readonly activeAdminCount = input<number>(1);

  readonly closed = output<void>();
  readonly roleChanged = output<OrganizationMemberDetailResponseDto>();

  readonly selectedRole = signal<AllowedOrgRole>('ROLE_USER');
  readonly submitting = signal<boolean>(false);
  readonly error = signal<string | null>(null);

  private readonly allRoleOptions: RoleOption[] = [
    {
      value: 'ROLE_USER',
      label: 'Member',
      description: 'Standard member — access to assigned resources',
    },
    {
      value: 'ROLE_ORG_MANAGER',
      label: 'Organization Manager',
      description: 'Can manage members, invitations, and workspaces',
    },
    {
      value: 'ROLE_ORG_ADMIN',
      label: 'Organization Administrator',
      description: 'Full organization administrative authority',
    },
  ];

  readonly currentRole = computed<AllowedOrgRole>(() => {
    const raw = this.member()?.role;
    if (raw === 'ROLE_ORG_ADMIN' || raw === 'ORGANIZATION_ADMIN' || raw === 'ADMIN') {
      return 'ROLE_ORG_ADMIN';
    }
    if (raw === 'ROLE_ORG_MANAGER' || raw === 'ORGANIZATION_MANAGER' || raw === 'MANAGER') {
      return 'ROLE_ORG_MANAGER';
    }
    return 'ROLE_USER';
  });

  readonly availableRoleOptions = computed(() => {
    const callerRole = (this.currentUserRole() || '').toUpperCase();
    const isCallerAdmin = callerRole.includes('ADMIN');
    if (!isCallerAdmin) {
      // Organization Managers cannot promote to Admin
      return this.allRoleOptions.filter((opt) => opt.value !== 'ROLE_ORG_ADMIN');
    }
    return this.allRoleOptions;
  });

  readonly isLastAdminDemotionBlocked = computed(() => {
    const targetIsAdmin = this.currentRole() === 'ROLE_ORG_ADMIN';
    const newRoleNotAdmin = this.selectedRole() !== 'ROLE_ORG_ADMIN';
    const isTargetActive = this.member()?.status === 'ACTIVE';
    return targetIsAdmin && isTargetActive && newRoleNotAdmin && this.activeAdminCount() <= 1;
  });

  readonly isNonActiveMemberRoleBlocked = computed(() => {
    const status = this.member()?.status;
    const isNotActive = status && status !== 'ACTIVE';
    const isManagerial =
      this.selectedRole() === 'ROLE_ORG_ADMIN' || this.selectedRole() === 'ROLE_ORG_MANAGER';
    return isNotActive && isManagerial;
  });

  readonly isActionBlocked = computed(() => {
    return this.isLastAdminDemotionBlocked() || this.isNonActiveMemberRoleBlocked();
  });

  constructor() {
    effect(
      () => {
        if (this.open() && this.member()) {
          this.selectedRole.set(this.currentRole());
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
    const newRole = this.selectedRole();

    if (!mem || !mem.userId || !org || this.submitting() || this.isActionBlocked()) {
      return;
    }

    this.submitting.set(true);
    this.error.set(null);

    const payload: UpdateMemberRoleRequestDto = { role: newRole };

    this.orgApiService.updateMemberRole(org, mem.userId, payload).subscribe({
      next: (updated) => {
        this.submitting.set(false);
        this.notificationService.success(
          `Role updated to ${this.formatRole(updated.role)} successfully.`
        );
        this.roleChanged.emit(updated);
        this.closed.emit();
      },
      error: (err) => {
        this.submitting.set(false);
        const msg = extractErrorMessage(
          err,
          'Failed to update member role. Please try again.'
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

  formatRole(role: string): string {
    switch (role) {
      case 'ROLE_ORG_ADMIN':
      case 'ADMIN':
        return 'Administrator';
      case 'ROLE_ORG_MANAGER':
      case 'MANAGER':
        return 'Organization Manager';
      default:
        return 'Member';
    }
  }

  getRoleBadgeClass(role: string): string {
    switch (role) {
      case 'ROLE_ORG_ADMIN':
      case 'ADMIN':
        return 'bg-amber-500/10 text-amber-800 dark:text-amber-400 border-amber-200 dark:border-amber-800';
      case 'ROLE_ORG_MANAGER':
      case 'MANAGER':
        return 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300 border-slate-200 dark:border-slate-700';
      default:
        return 'bg-muted text-muted-foreground border-border';
    }
  }
}
