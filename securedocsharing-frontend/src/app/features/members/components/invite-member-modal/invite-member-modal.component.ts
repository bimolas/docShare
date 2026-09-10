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
  LucideMail,
  LucideAlertCircle,
  LucideSend,
  LucideCheck,
} from '@lucide/angular';
import { ModalComponent } from '../../../../shared/ui/modal/modal.component';
import { OrganizationApiService } from '../../../../core/services/organization-api.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { CreateOrganizationInvitationRequestDto } from '../../../../core/models/organization.model';
import { extractErrorMessage } from '../../../../core/models/app-error.model';

export interface InviteRoleOption {
  value: 'ROLE_USER' | 'ROLE_ORG_MANAGER' | 'ROLE_ORG_ADMIN';
  label: string;
  badge: string;
  description: string;
}

@Component({
  selector: 'app-invite-member-modal',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    LucideMail,
    LucideAlertCircle,
    LucideSend,
    LucideCheck,
    ModalComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <sd-modal
      [open]="isOpen()"
      title="Invite Member"
      subtitle="Send an email invitation to join this organization."
      size="sm"
      [closeOnBackdrop]="!isLoading()"
      (closed)="onClose()"
    >
      <form (ngSubmit)="onSubmit()" class="space-y-4 py-2">
        <!-- Error Alert Banner -->
        @if (errorMessage()) {
          <div class="flex items-start gap-2.5 p-3 rounded-lg bg-destructive/10 border border-destructive/20 text-destructive text-xs">
            <svg lucideAlertCircle class="w-4 h-4 shrink-0 mt-0.5"></svg>
            <p>{{ errorMessage() }}</p>
          </div>
        }

        <!-- Email Field -->
        <div class="space-y-1.5">
          <label for="invitee-email-input" class="block text-xs font-semibold text-foreground uppercase tracking-wider">
            Email Address <span class="text-destructive font-bold">*</span>
          </label>
          <div class="relative">
            <svg lucideMail class="w-4 h-4 text-muted-foreground absolute left-3 top-2.5 pointer-events-none"></svg>
            <input
              id="invitee-email-input"
              type="email"
              class="w-full pl-9 pr-3 py-2 rounded-lg border border-border bg-card text-foreground text-xs placeholder:text-muted-foreground shadow-xs focus:outline-hidden focus:ring-2 focus:ring-primary/20"
              [class.border-destructive]="isEmailTouched() && !isEmailValid()"
              [ngModel]="inviteeEmail()"
              (ngModelChange)="onEmailChange($event)"
              (blur)="isEmailTouched.set(true)"
              name="inviteeEmail"
              placeholder="colleague@company.com"
              autocomplete="off"
              [disabled]="isLoading()"
            />
          </div>
          @if (isAlreadyMember()) {
            <span class="text-[11px] text-amber-600 dark:text-amber-400 font-medium flex items-center gap-1 mt-1">
              <svg lucideAlertCircle class="w-3.5 h-3.5 shrink-0"></svg>
              <span>This user is already an active member of this organization.</span>
            </span>
          } @else if (isEmailTouched() && !isEmailValid()) {
            <span class="text-[11px] text-destructive">Please enter a valid email address.</span>
          }
        </div>

        <!-- Role Selection -->
        <div class="space-y-1.5">
          <label class="block text-xs font-semibold text-foreground uppercase tracking-wider">
            Organization Role <span class="text-destructive font-bold">*</span>
          </label>
          <div class="space-y-2">
            @for (opt of availableRoleOptions(); track opt.value) {
              <div
                class="p-2.5 rounded-lg border transition-colors cursor-pointer select-none"
                [class.border-primary]="selectedRole() === opt.value"
                [class.bg-primary-50]="selectedRole() === opt.value"
                [class.dark:bg-primary-950/20]="selectedRole() === opt.value"
                [class.border-border]="selectedRole() !== opt.value"
                [class.hover:bg-muted/40]="selectedRole() !== opt.value"
                (click)="selectRole(opt.value)"
              >
                <div class="flex items-center justify-between">
                  <div class="flex items-center gap-2">
                    <span class="text-xs font-semibold text-foreground">{{ opt.label }}</span>
                    <span
                      class="px-2 py-0.5 rounded-full text-[10px] font-medium border"
                      [class.bg-amber-500/10]="opt.value === 'ROLE_ORG_ADMIN'"
                      [class.text-amber-800]="opt.value === 'ROLE_ORG_ADMIN'"
                      [class.border-amber-200]="opt.value === 'ROLE_ORG_ADMIN'"
                      [class.bg-slate-100]="opt.value === 'ROLE_ORG_MANAGER'"
                      [class.text-slate-700]="opt.value === 'ROLE_ORG_MANAGER'"
                      [class.border-slate-200]="opt.value === 'ROLE_ORG_MANAGER'"
                      [class.bg-muted]="opt.value === 'ROLE_USER'"
                      [class.text-muted-foreground]="opt.value === 'ROLE_USER'"
                      [class.border-border]="opt.value === 'ROLE_USER'"
                    >
                      {{ opt.badge }}
                    </span>
                  </div>
                  <div
                    class="w-4 h-4 rounded-full border flex items-center justify-center transition-colors"
                    [class.border-primary]="selectedRole() === opt.value"
                    [class.bg-primary]="selectedRole() === opt.value"
                    [class.border-muted-foreground/40]="selectedRole() !== opt.value"
                  >
                    @if (selectedRole() === opt.value) {
                      <svg lucideCheck class="w-3 h-3 text-white stroke-[2.5]"></svg>
                    }
                  </div>
                </div>
                <p class="text-[11px] text-muted-foreground mt-1 leading-normal">{{ opt.description }}</p>
              </div>
            }
          </div>
        </div>

        <!-- Optional Personal Note -->
        <div class="space-y-1.5">
          <label for="invite-note-input" class="block text-xs font-semibold text-foreground uppercase tracking-wider">
            Personal Note <span class="text-muted-foreground font-normal lowercase">(optional)</span>
          </label>
          <textarea
            id="invite-note-input"
            rows="2"
            class="w-full px-3 py-2 rounded-lg border border-border bg-card text-foreground text-xs placeholder:text-muted-foreground resize-none shadow-xs focus:outline-hidden focus:ring-2 focus:ring-primary/20"
            placeholder="Add context or a welcome message for the invitee..."
            [ngModel]="note()"
            (ngModelChange)="note.set($event)"
            name="inviteNote"
            [disabled]="isLoading()"
          ></textarea>
        </div>

        <div modal-footer class="flex items-center justify-end gap-2.5 pt-2">
          <button
            type="button"
            class="px-4 py-2 rounded-lg border border-border bg-card text-foreground hover:bg-muted text-xs font-medium transition-colors"
            (click)="onClose()"
            [disabled]="isLoading()"
          >
            Cancel
          </button>
          <button
            type="submit"
            class="px-4 py-2 rounded-lg bg-primary text-primary-foreground hover:bg-primary/90 text-xs font-medium shadow-xs transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-1.5"
            [disabled]="isLoading() || !isFormValid()"
          >
            @if (isLoading()) {
              <span class="w-3.5 h-3.5 border-2 border-primary-foreground border-t-transparent rounded-full animate-spin"></span>
              <span>Sending...</span>
            } @else {
              <svg lucideSend class="w-3.5 h-3.5"></svg>
              <span>Send Invitation</span>
            }
          </button>
        </div>
      </form>
    </sd-modal>
  `,
})
export class InviteMemberModalComponent {
  readonly isOpen = input<boolean>(false);
  readonly orgCode = input<string | null>(null);
  readonly currentUserRole = input<string | null>(null);
  readonly existingMemberEmails = input<string[]>([]);

  readonly closeModal = output<void>();
  readonly invitationSent = output<void>();

  private readonly orgApiService = inject(OrganizationApiService);
  private readonly notificationService = inject(NotificationService);

  readonly inviteeEmail = signal<string>('');
  readonly selectedRole = signal<'ROLE_USER' | 'ROLE_ORG_MANAGER' | 'ROLE_ORG_ADMIN'>('ROLE_USER');
  readonly note = signal<string>('');

  readonly isLoading = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);
  readonly isEmailTouched = signal<boolean>(false);

  private readonly allRoleOptions: InviteRoleOption[] = [
    {
      value: 'ROLE_USER',
      label: 'Member',
      badge: 'Standard',
      description: 'Can access shared resources and assigned organization teams.',
    },
    {
      value: 'ROLE_ORG_MANAGER',
      label: 'Organization Manager',
      badge: 'Manager',
      description: 'Can manage organization members, invitations, and workspace governance.',
    },
    {
      value: 'ROLE_ORG_ADMIN',
      label: 'Administrator',
      badge: 'Admin',
      description: 'Full organization administrative authority, policies, and settings.',
    },
  ];

  readonly availableRoleOptions = computed(() => {
    const callerRole = (this.currentUserRole() || '').toUpperCase();
    const isCallerAdmin = callerRole.includes('ADMIN');
    if (!isCallerAdmin) {
      // Organization Managers cannot invite with Admin role
      return this.allRoleOptions.filter((opt) => opt.value !== 'ROLE_ORG_ADMIN');
    }
    return this.allRoleOptions;
  });

  readonly isEmailValid = computed(() => {
    const e = this.inviteeEmail().trim();
    if (!e) return false;
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(e);
  });

  readonly isAlreadyMember = computed(() => {
    const e = this.inviteeEmail().trim().toLowerCase();
    if (!e) return false;
    return this.existingMemberEmails().some((m) => (m || '').toLowerCase() === e);
  });

  readonly isFormValid = computed(() => {
    return this.isEmailValid() && !this.isAlreadyMember() && !!this.orgCode();
  });

  constructor() {
    effect(
      () => {
        if (this.isOpen()) {
          this.resetForm();
        }
      },
      { allowSignalWrites: true }
    );
  }

  onEmailChange(val: string): void {
    this.inviteeEmail.set(val);
    this.errorMessage.set(null);
  }

  selectRole(role: 'ROLE_USER' | 'ROLE_ORG_MANAGER' | 'ROLE_ORG_ADMIN'): void {
    if (this.isLoading()) return;
    this.selectedRole.set(role);
  }

  onClose(): void {
    if (this.isLoading()) return;
    this.resetForm();
    this.closeModal.emit();
  }

  onSubmit(): void {
    const org = this.orgCode();
    if (!this.isFormValid() || this.isLoading() || !org) return;

    const emailVal = this.inviteeEmail().trim();
    const roleVal = this.selectedRole();
    const noteVal = this.note().trim() || undefined;

    const payload: CreateOrganizationInvitationRequestDto = {
      inviteeEmail: emailVal,
      role: roleVal,
      note: noteVal,
    };

    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.orgApiService.createInvitation(org, payload).subscribe({
      next: (res) => {
        this.isLoading.set(false);
        this.notificationService.success(
          `Invitation successfully sent to ${res.inviteeEmail || emailVal}.`
        );
        this.resetForm();
        this.invitationSent.emit();
        this.closeModal.emit();
      },
      error: (err) => {
        this.isLoading.set(false);
        const msg = extractErrorMessage(
          err,
          'Failed to send invitation. Please verify the email address and your permissions.'
        );
        this.errorMessage.set(msg);
      },
    });
  }

  private resetForm(): void {
    this.inviteeEmail.set('');
    this.selectedRole.set('ROLE_USER');
    this.note.set('');
    this.isLoading.set(false);
    this.errorMessage.set(null);
    this.isEmailTouched.set(false);
  }
}
