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
  LucideShieldAlert,
  LucideAlertCircle,
  LucideChevronDown,
  LucideCheck,
  LucideUserCheck,
} from '@lucide/angular';
import { ModalComponent } from '../../../../shared/ui/modal/modal.component';
import { DropdownComponent } from '../../../../shared/ui/dropdown/dropdown.component';
import { OrganizationApiService } from '../../../../core/services/organization-api.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { AuthStore } from '../../../../state/auth/auth.store';
import { OrganizationMemberDetailResponseDto } from '../../../../core/models/organization.model';
import { extractErrorMessage } from '../../../../core/models/app-error.model';

@Component({
  selector: 'app-transfer-admin-modal',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    LucideShieldAlert,
    LucideAlertCircle,
    LucideChevronDown,
    LucideCheck,
    LucideUserCheck,
    ModalComponent,
    DropdownComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <sd-modal
      [open]="open()"
      title="Transfer Organization Administration"
      subtitle="Relinquish administrator role and transfer primary leadership."
      size="sm"
      [closeOnBackdrop]="!submitting()"
      (closed)="onClose()"
    >
      <div class="space-y-4 py-2">
        <!-- Dual Outcome Explanation Banner -->
        <div class="p-3 rounded-lg bg-amber-500/10 border border-amber-500/30 text-amber-900 dark:text-amber-300 text-xs space-y-2">
          <div class="flex items-center gap-1.5 font-semibold">
            <svg lucideShieldAlert class="w-4 h-4 text-amber-600 dark:text-amber-400 shrink-0"></svg>
            <span>Important Governance Transfer Rules</span>
          </div>
          <p class="leading-relaxed">
            Transferring administration is an irreversible atomic leadership change:
          </p>
          <ul class="list-disc list-inside space-y-0.5 text-[11px] opacity-90 pl-1">
            <li>The selected member will immediately become the <strong>Organization Administrator</strong>.</li>
            <li>Your account will be automatically stepped down to an <strong>Organization Manager</strong>.</li>
            <li>All organizational policies, team assignments, and audit controls will transfer.</li>
          </ul>
        </div>

        <!-- Target Member Selector -->
        <div class="space-y-1.5">
          <label class="block text-xs font-semibold text-foreground uppercase tracking-wider">
            Select New Administrator <span class="text-destructive font-bold">*</span>
          </label>
          <sd-dropdown placement="bottom-start" class="w-full">
            <button
              dropdown-trigger
              type="button"
              class="w-full flex items-center justify-between px-3.5 py-2 rounded-lg border border-border bg-card text-foreground hover:bg-muted/40 transition-colors text-sm font-medium shadow-xs focus:outline-hidden focus:ring-2 focus:ring-amber-500/20"
              [disabled]="submitting() || eligibleMembers().length === 0"
            >
              @if (selectedTarget()) {
                <span class="truncate">{{ getMemberDisplayName(selectedTarget()!) }} ({{ selectedTarget()?.email }})</span>
              } @else {
                <span class="text-muted-foreground">Select an active member...</span>
              }
              <svg lucideChevronDown class="w-4 h-4 text-muted-foreground shrink-0 ml-2"></svg>
            </button>
            <div dropdown-menu class="w-80 max-h-56 overflow-y-auto p-1 bg-popover rounded-lg border border-border shadow-lg space-y-0.5 custom-scrollbar">
              @for (mem of eligibleMembers(); track mem.userId) {
                <button
                  type="button"
                  class="w-full text-left p-2 rounded-md transition-colors flex items-center justify-between gap-2 text-xs"
                  [class.bg-primary-50]="selectedTarget()?.userId === mem.userId"
                  [class.dark:bg-primary-950/30]="selectedTarget()?.userId === mem.userId"
                  [class.hover:bg-muted]="selectedTarget()?.userId !== mem.userId"
                  (click)="selectedTarget.set(mem)"
                >
                  <div class="min-w-0">
                    <div class="font-medium text-foreground truncate">{{ getMemberDisplayName(mem) }}</div>
                    <div class="text-[11px] text-muted-foreground font-mono truncate">{{ mem.email }}</div>
                  </div>
                  @if (selectedTarget()?.userId === mem.userId) {
                    <svg lucideCheck class="w-3.5 h-3.5 text-primary shrink-0"></svg>
                  }
                </button>
              }
              @if (eligibleMembers().length === 0) {
                <div class="p-3 text-center text-xs text-muted-foreground">
                  No other active members available to receive administrator role.
                </div>
              }
            </div>
          </sd-dropdown>
        </div>

        <!-- Explicit Confirmation Checkbox -->
        <div class="pt-1">
          <label class="flex items-start gap-2.5 p-2.5 rounded-lg border border-border bg-card/60 cursor-pointer select-none">
            <input
              type="checkbox"
              class="mt-0.5 rounded border-border text-primary focus:ring-primary/20"
              [checked]="confirmedAck()"
              (change)="confirmedAck.set($any($event.target).checked)"
              [disabled]="submitting() || !selectedTarget()"
            />
            <span class="text-[11px] text-foreground leading-relaxed">
              I understand that I am relinquishing Organization Administrator authority to
              <strong>{{ selectedTarget() ? getMemberDisplayName(selectedTarget()!) : 'the selected member' }}</strong>
              and my account will become an Organization Manager.
            </span>
          </label>
        </div>

        <!-- Server Error Banner -->
        @if (error()) {
          <div class="flex items-start gap-2.5 p-3 rounded-lg bg-destructive/10 border border-destructive/20 text-destructive text-xs">
            <svg lucideAlertCircle class="w-4 h-4 shrink-0 mt-0.5"></svg>
            <p>{{ error() }}</p>
          </div>
        }
      </div>

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
          [disabled]="submitting() || !selectedTarget() || !confirmedAck()"
          (click)="onConfirm()"
        >
          @if (submitting()) {
            <span class="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
            Transferring...
          } @else {
            <svg lucideUserCheck class="w-3.5 h-3.5"></svg>
            Transfer Administration
          }
        </button>
      </div>
    </sd-modal>
  `,
})
export class TransferAdminModalComponent {
  private readonly orgApiService = inject(OrganizationApiService);
  private readonly notificationService = inject(NotificationService);
  private readonly authStore = inject(AuthStore);

  readonly open = input<boolean>(false);
  readonly orgCode = input<string>('');
  readonly members = input<OrganizationMemberDetailResponseDto[]>([]);
  readonly preselectedTarget = input<OrganizationMemberDetailResponseDto | null>(null);

  readonly closed = output<void>();
  readonly adminTransferred = output<OrganizationMemberDetailResponseDto>();

  readonly selectedTarget = signal<OrganizationMemberDetailResponseDto | null>(null);
  readonly confirmedAck = signal<boolean>(false);
  readonly submitting = signal<boolean>(false);
  readonly error = signal<string | null>(null);

  readonly currentUserId = computed(() => this.authStore.currentUser()?.id);

  readonly eligibleMembers = computed(() => {
    const list = this.members() || [];
    const myId = this.currentUserId();
    return list.filter((m) => {
      if (m.userId === myId) return false;
      if (m.status !== 'ACTIVE') return false;
      const role = (m.role || '').toUpperCase();
      if (role.includes('ADMIN')) return false; // Already an admin
      return true;
    });
  });

  constructor() {
    effect(
      () => {
        if (this.open()) {
          this.confirmedAck.set(false);
          this.error.set(null);
          this.submitting.set(false);

          const pre = this.preselectedTarget();
          if (pre && pre.userId !== this.currentUserId() && pre.status === 'ACTIVE') {
            this.selectedTarget.set(pre);
          } else {
            this.selectedTarget.set(null);
          }
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
    const target = this.selectedTarget();
    const org = this.orgCode();

    if (!target || !target.userId || !org || this.submitting() || !this.confirmedAck()) {
      return;
    }

    this.submitting.set(true);
    this.error.set(null);

    this.orgApiService.transferOrgAdmin(org, target.userId).subscribe({
      next: (updatedTarget) => {
        this.submitting.set(false);
        this.notificationService.success(
          `Organization administration has been successfully transferred to ${this.getMemberDisplayName(updatedTarget)}. You are now an Organization Manager.`
        );
        this.adminTransferred.emit(updatedTarget);
        this.closed.emit();
      },
      error: (err) => {
        this.submitting.set(false);
        const msg = extractErrorMessage(
          err,
          'Failed to transfer organization administration. Please try again.'
        );
        this.error.set(msg);
      },
    });
  }

  getMemberDisplayName(m: OrganizationMemberDetailResponseDto): string {
    const fn = m.firstName?.trim() || '';
    const ln = m.lastName?.trim() || '';
    if (fn || ln) return `${fn} ${ln}`.trim();
    return m.email;
  }
}
