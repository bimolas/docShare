import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { UnifiedAuditApiService } from '../../../core/services/unified-audit-api.service';
import {
  UnifiedAuditEventResponseDto,
  UnifiedAuditQueryCriteria,
  AuditScopeType,
  AuditCategory,
  AuditViewType,
} from '../../../core/models/unified-audit.model';

@Injectable()
export class AuditStore {
  private readonly auditApi = inject(UnifiedAuditApiService);

  // ── State Signals ──
  readonly criteria = signal<UnifiedAuditQueryCriteria>({
    scopeType: 'PERSONAL',
    view: 'ALL',
  });
  readonly page = signal<number>(0);
  readonly size = signal<number>(20);
  readonly sort = signal<string>('timestamp,desc');

  readonly events = signal<UnifiedAuditEventResponseDto[]>([]);
  readonly totalElements = signal<number>(0);
  readonly totalPages = signal<number>(0);
  readonly isLoading = signal<boolean>(false);
  readonly isRefreshing = signal<boolean>(false);
  readonly error = signal<string | null>(null);
  readonly isAccessDenied = signal<boolean>(false);

  readonly selectedEvent = signal<UnifiedAuditEventResponseDto | null>(null);
  readonly isDrawerOpen = signal<boolean>(false);

  // ── Computed Signals ──
  readonly hasEvents = computed(() => this.events().length > 0);
  readonly activeScope = computed(() => this.criteria().scopeType);
  readonly activeView = computed(() => this.criteria().view || 'ALL');
  readonly hasActiveFilters = computed(() => {
    const c = this.criteria();
    return !!(c.search || c.category || c.actor || c.from || c.to);
  });

  /**
   * Set base criteria (e.g. when scope or container changes).
   * Resets pagination to page 0.
   */
  setScopeAndContainer(
    scopeType: AuditScopeType,
    containerIds?: {
      orgCode?: string;
      workspaceId?: number;
      folderId?: number;
      documentId?: number;
      teamId?: number;
      collaborationId?: number;
    }
  ): void {
    this.criteria.update((prev) => ({
      ...prev,
      scopeType,
      orgCode: containerIds?.orgCode,
      workspaceId: containerIds?.workspaceId,
      folderId: containerIds?.folderId,
      documentId: containerIds?.documentId,
      teamId: containerIds?.teamId,
      collaborationId: containerIds?.collaborationId,
      // If folder, keep includeDescendants default false if undefined
      includeDescendants: scopeType === 'FOLDER' ? (prev.includeDescendants ?? false) : undefined,
    }));
    this.page.set(0);
    this.loadEvents();
  }

  setView(view: AuditViewType): void {
    this.criteria.update((prev) => ({ ...prev, view }));
    this.page.set(0);
    this.loadEvents();
  }

  updateFilters(filters: {
    category?: AuditCategory;
    actor?: string;
    search?: string;
    from?: string;
    to?: string;
    includeDescendants?: boolean;
  }): void {
    this.criteria.update((prev) => ({
      ...prev,
      category: filters.category,
      actor: filters.actor,
      search: filters.search,
      from: filters.from,
      to: filters.to,
      includeDescendants: filters.includeDescendants,
    }));
    this.page.set(0);
    this.loadEvents();
  }

  clearFilters(): void {
    this.criteria.update((prev) => ({
      scopeType: prev.scopeType,
      orgCode: prev.orgCode,
      workspaceId: prev.workspaceId,
      folderId: prev.folderId,
      documentId: prev.documentId,
      teamId: prev.teamId,
      collaborationId: prev.collaborationId,
      view: prev.view || 'ALL',
      includeDescendants: prev.scopeType === 'FOLDER' ? false : undefined,
      category: undefined,
      actor: undefined,
      search: undefined,
      from: undefined,
      to: undefined,
    }));
    this.page.set(0);
    this.loadEvents();
  }

  setPage(pageIndex: number): void {
    if (pageIndex < 0 || (this.totalPages() > 0 && pageIndex >= this.totalPages())) {
      return;
    }
    this.page.set(pageIndex);
    this.loadEvents();
  }

  setPageSize(pageSize: number): void {
    this.size.set(pageSize);
    this.page.set(0);
    this.loadEvents();
  }

  loadEvents(isRefresh: boolean = false): void {
    if (isRefresh) {
      this.isRefreshing.set(true);
    } else {
      this.isLoading.set(true);
    }
    this.error.set(null);
    this.isAccessDenied.set(false);

    const currentCriteria = this.criteria();
    const currentPage = this.page();
    const currentSize = this.size();
    const currentSort = this.sort();

    this.auditApi.getEvents(currentCriteria, currentPage, currentSize, currentSort).subscribe({
      next: (response) => {
        this.events.set(response?.content || []);
        this.totalElements.set(response?.totalElements || 0);
        this.totalPages.set(response?.totalPages || 0);
        this.isLoading.set(false);
        this.isRefreshing.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.isLoading.set(false);
        this.isRefreshing.set(false);
        if (err.status === 403) {
          this.isAccessDenied.set(true);
          this.error.set(
            err.error?.message ||
              'Access denied: You do not have permission to view audit events for this scope.'
          );
        } else if (err.status === 404) {
          this.error.set('Resource or container not found.');
        } else {
          this.error.set(
            err.error?.message || 'Failed to load audit events. Please try again.'
          );
        }
      },
    });
  }

  openDetail(event: UnifiedAuditEventResponseDto): void {
    this.selectedEvent.set(event);
    this.isDrawerOpen.set(true);
  }

  closeDetail(): void {
    this.isDrawerOpen.set(false);
  }
}
