import { Component, ChangeDetectionStrategy, input, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import {
  PersonalDashboardSummary,
  OrganizationDashboardSummary,
  DashboardScope,
} from '../models/dashboard.model';
import { IconComponent } from '../../../shared/components/icon/icon.component';

/**
 * DashboardActionCardComponent
 *
 * Focuses on urgent operational items (access reviews, invitations, pending requests)
 * and canonical shortcut routing for the active space.
 */
@Component({
  selector: 'app-dashboard-action-card',
  standalone: true,
  imports: [CommonModule, RouterLink, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-col justify-between p-5 rounded-2xl bg-white border border-slate-200/80 shadow-xs h-full">
      <!-- Header -->
      <div class="mb-3">
        <span class="text-[11px] font-bold text-slate-400 uppercase tracking-wider block">
          Attention & Governance
        </span>
        <h3 class="text-base font-semibold text-slate-900 mt-0.5">
          {{ isPersonal() ? 'Personal Actions' : 'Organization Administration' }}
        </h3>
      </div>

      <!-- Action-Required Alerts or Status Items -->
      <div class="space-y-2.5 my-auto py-1">
        @if (isPersonal()) {
          <!-- Personal Pending Access Reviews -->
          @if (personalSummary()?.pendingAccessReviewsCount; as reviewsCount) {
            <a
              routerLink="/w/personal/access-requests"
              class="group flex items-center justify-between p-3.5 rounded-xl bg-amber-50/70 border border-amber-200/80 hover:bg-amber-100/70 transition-all duration-150"
            >
              <div class="flex items-center gap-3 min-w-0">
                <div class="w-8 h-8 rounded-lg bg-amber-500 text-white flex items-center justify-center shrink-0">
                  <app-icon name="key" [size]="16" />
                </div>
                <div class="min-w-0">
                  <div class="text-xs font-bold text-amber-900 flex items-center gap-1.5">
                    <span>Access Reviews Required</span>
                    <span class="inline-flex items-center px-1.5 py-0.2 rounded-full bg-amber-200 text-amber-900 text-[10px] font-extrabold">
                      {{ reviewsCount }}
                    </span>
                  </div>
                  <p class="text-[11px] text-amber-700/90 truncate mt-0.5">
                    Requests waiting for your security review
                  </p>
                </div>
              </div>
              <app-icon
                name="arrow-right"
                [size]="15"
                class="text-amber-700 group-hover:translate-x-0.5 transition-transform shrink-0"
              />
            </a>
          }

          <!-- Personal Outgoing Pending Requests -->
          @if (personalSummary()?.myPendingAccessRequestsCount; as myReqCount) {
            <a
              routerLink="/w/personal/access-requests"
              class="group flex items-center justify-between p-3 rounded-xl bg-slate-50 border border-slate-200/80 hover:bg-slate-100/80 transition-all duration-150"
            >
              <div class="flex items-center gap-3 min-w-0">
                <div class="w-8 h-8 rounded-lg bg-slate-200 text-slate-700 flex items-center justify-center shrink-0">
                  <app-icon name="clock" [size]="16" />
                </div>
                <div class="min-w-0">
                  <div class="text-xs font-semibold text-slate-800">
                    Requests In Review ({{ myReqCount }})
                  </div>
                  <p class="text-[11px] text-slate-500 truncate mt-0.5">
                    Awaiting document owner approval
                  </p>
                </div>
              </div>
              <app-icon
                name="chevron-right"
                [size]="16"
                class="text-slate-400 group-hover:text-slate-600 transition-colors shrink-0"
              />
            </a>
          }

          <!-- Personal Direct Shares -->
          @if (personalSummary()?.activeDirectSharesCount; as sharesCount) {
            <a
              routerLink="/w/personal/shared-by-me"
              class="group flex items-center justify-between p-3 rounded-xl bg-slate-50 border border-slate-200/80 hover:bg-slate-100/80 transition-all duration-150"
            >
              <div class="flex items-center gap-3 min-w-0">
                <div class="w-8 h-8 rounded-lg bg-slate-200 text-slate-700 flex items-center justify-center shrink-0">
                  <app-icon name="share-2" [size]="16" />
                </div>
                <div class="min-w-0">
                  <div class="text-xs font-semibold text-slate-800">
                    Active Direct Shares ({{ sharesCount }})
                  </div>
                  <p class="text-[11px] text-slate-500 truncate mt-0.5">
                    Items shared externally or with peers
                  </p>
                </div>
              </div>
              <app-icon
                name="chevron-right"
                [size]="16"
                class="text-slate-400 group-hover:text-slate-600 transition-colors shrink-0"
              />
            </a>
          }

          <!-- If all counts are zero, show green all-clear banner -->
          @if (!hasPersonalPendingItems()) {
            <div class="flex items-center gap-3 p-3.5 rounded-xl bg-emerald-50/60 border border-emerald-200/60 text-emerald-800">
              <div class="w-8 h-8 rounded-lg bg-emerald-100 text-emerald-700 flex items-center justify-center shrink-0">
                <app-icon name="shield-check" [size]="18" />
              </div>
              <div>
                <div class="text-xs font-bold text-emerald-900">All Clear</div>
                <div class="text-[11px] text-emerald-700 mt-0.5">No pending access requests or reviews require your action.</div>
              </div>
            </div>
          }
        } @else {
          <!-- Organization Pending Access Requests -->
          @if (organizationSummary()?.pendingAccessRequestsCount; as reqCount) {
            <a
              [routerLink]="['/org', orgCode(), 'access-requests']"
              class="group flex items-center justify-between p-3.5 rounded-xl bg-amber-50/70 border border-amber-200/80 hover:bg-amber-100/70 transition-all duration-150"
            >
              <div class="flex items-center gap-3 min-w-0">
                <div class="w-8 h-8 rounded-lg bg-amber-500 text-white flex items-center justify-center shrink-0">
                  <app-icon name="key" [size]="16" />
                </div>
                <div class="min-w-0">
                  <div class="text-xs font-bold text-amber-900 flex items-center gap-1.5">
                    <span>Access Requests</span>
                    <span class="inline-flex items-center px-1.5 py-0.2 rounded-full bg-amber-200 text-amber-900 text-[10px] font-extrabold">
                      {{ reqCount }}
                    </span>
                  </div>
                  <p class="text-[11px] text-amber-700/90 truncate mt-0.5">
                    Organization requests awaiting admin review
                  </p>
                </div>
              </div>
              <app-icon
                name="arrow-right"
                [size]="15"
                class="text-amber-700 group-hover:translate-x-0.5 transition-transform shrink-0"
              />
            </a>
          }

          <!-- Organization Pending Invitations -->
          @if (organizationSummary()?.pendingInvitationsCount; as invCount) {
            <a
              [routerLink]="['/org', orgCode(), 'members']"
              class="group flex items-center justify-between p-3 rounded-xl bg-slate-50 border border-slate-200/80 hover:bg-slate-100/80 transition-all duration-150"
            >
              <div class="flex items-center gap-3 min-w-0">
                <div class="w-8 h-8 rounded-lg bg-slate-200 text-slate-700 flex items-center justify-center shrink-0">
                  <app-icon name="mail" [size]="16" />
                </div>
                <div class="min-w-0">
                  <div class="text-xs font-semibold text-slate-800">
                    Pending Invitations ({{ invCount }})
                  </div>
                  <p class="text-[11px] text-slate-500 truncate mt-0.5">
                    Awaiting invitee acceptance
                  </p>
                </div>
              </div>
              <app-icon
                name="chevron-right"
                [size]="16"
                class="text-slate-400 group-hover:text-slate-600 transition-colors shrink-0"
              />
            </a>
          }

          <!-- If all org counts are zero, show green all-clear banner -->
          @if (!hasOrgPendingItems()) {
            <div class="flex items-center gap-3 p-3.5 rounded-xl bg-emerald-50/60 border border-emerald-200/60 text-emerald-800">
              <div class="w-8 h-8 rounded-lg bg-emerald-100 text-emerald-700 flex items-center justify-center shrink-0">
                <app-icon name="shield-check" [size]="18" />
              </div>
              <div>
                <div class="text-xs font-bold text-emerald-900">Governance In Good Standing</div>
                <div class="text-[11px] text-emerald-700 mt-0.5">No pending invitations or unreviewed access requests.</div>
              </div>
            </div>
          }
        }
      </div>

      <!-- Quick Shortcuts Section -->
      <div class="pt-3 border-t border-slate-100">
        <span class="text-[10px] font-bold text-slate-400 uppercase tracking-wider block mb-2">
          Quick Shortcuts
        </span>
        <div class="grid grid-cols-2 gap-2">
          @if (isPersonal()) {
            <a
              routerLink="/w/personal/explorer"
              class="flex items-center gap-2 p-2 rounded-xl text-xs font-medium text-slate-700 bg-slate-50 hover:bg-amber-50 hover:text-amber-800 border border-slate-200/60 transition-colors"
            >
              <app-icon name="folder" [size]="15" class="text-slate-400 shrink-0" />
              <span class="truncate">My Files</span>
            </a>
            <a
              routerLink="/w/personal/shared-with-me"
              class="flex items-center gap-2 p-2 rounded-xl text-xs font-medium text-slate-700 bg-slate-50 hover:bg-amber-50 hover:text-amber-800 border border-slate-200/60 transition-colors"
            >
              <app-icon name="inbox" [size]="15" class="text-slate-400 shrink-0" />
              <span class="truncate">Shared With Me</span>
            </a>
          } @else {
            <a
              [routerLink]="['/org', orgCode(), 'teams']"
              class="flex items-center gap-2 p-2 rounded-xl text-xs font-medium text-slate-700 bg-slate-50 hover:bg-amber-50 hover:text-amber-800 border border-slate-200/60 transition-colors"
            >
              <app-icon name="users" [size]="15" class="text-slate-400 shrink-0" />
              <span class="truncate">Teams</span>
            </a>
            <a
              [routerLink]="['/org', orgCode(), 'members']"
              class="flex items-center gap-2 p-2 rounded-xl text-xs font-medium text-slate-700 bg-slate-50 hover:bg-amber-50 hover:text-amber-800 border border-slate-200/60 transition-colors"
            >
              <app-icon name="user" [size]="15" class="text-slate-400 shrink-0" />
              <span class="truncate">Members</span>
            </a>
          }
        </div>
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      height: 100%;
    }
  `],
})
export class DashboardActionCardComponent {
  readonly scope = input.required<DashboardScope>();
  readonly personalSummary = input<PersonalDashboardSummary | null>(null);
  readonly organizationSummary = input<OrganizationDashboardSummary | null>(null);
  readonly orgCode = input<string | null>(null);

  readonly isPersonal = computed(() => this.scope() === 'PERSONAL');

  readonly hasPersonalPendingItems = computed(() => {
    const s = this.personalSummary();
    if (!s) return false;
    return (s.pendingAccessReviewsCount || 0) > 0 ||
           (s.myPendingAccessRequestsCount || 0) > 0 ||
           (s.activeDirectSharesCount || 0) > 0;
  });

  readonly hasOrgPendingItems = computed(() => {
    const s = this.organizationSummary();
    if (!s) return false;
    return (s.pendingAccessRequestsCount || 0) > 0 ||
           (s.pendingInvitationsCount || 0) > 0;
  });
}
