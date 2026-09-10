import { Injectable, signal, computed } from '@angular/core';

export type ModalType =
  | 'SHARE_DOCUMENT'
  | 'CREATE_FOLDER'
  | 'MOVE_COPY_ITEM'
  | 'INVITE_MEMBER'
  | 'CREATE_SHARE_LINK'
  | 'REVOKE_PERMISSION_CONFIRM'
  | 'DELETE_CONFIRMATION'
  | 'CREATE_ORGANIZATION'
  /** @deprecated Use CREATE_WORKSPACE instead */
  | 'CREATE_SUBSPACE'
  | 'CREATE_WORKSPACE';

@Injectable({ providedIn: 'root' })
export class LayoutStore {
  // Signals
  private readonly _isSidebarCollapsed = signal<boolean>(false);
  private readonly _isMobileDrawerOpen = signal<boolean>(false);
  private readonly _isDetailDrawerOpen = signal<boolean>(false);
  private readonly _activeModal = signal<ModalType | null>(null);

  // Readonly signals
  readonly isSidebarCollapsed = this._isSidebarCollapsed.asReadonly();
  readonly isMobileDrawerOpen = this._isMobileDrawerOpen.asReadonly();
  readonly isDetailDrawerOpen = this._isDetailDrawerOpen.asReadonly();
  readonly activeModal = this._activeModal.asReadonly();

  // Computed state
  readonly hasActiveModal = computed(() => this._activeModal() !== null);

  // Sidebar actions
  toggleSidebar(): void {
    this._isSidebarCollapsed.update((v) => !v);
  }

  setSidebarCollapsed(collapsed: boolean): void {
    this._isSidebarCollapsed.set(collapsed);
  }

  // Mobile drawer actions
  toggleMobileDrawer(): void {
    this._isMobileDrawerOpen.update((v) => !v);
  }

  openMobileDrawer(): void {
    this._isMobileDrawerOpen.set(true);
  }

  closeMobileDrawer(): void {
    this._isMobileDrawerOpen.set(false);
  }

  // Detail drawer actions
  toggleDetailDrawer(): void {
    this._isDetailDrawerOpen.update((v) => !v);
  }

  openDetailDrawer(): void {
    this._isDetailDrawerOpen.set(true);
  }

  closeDetailDrawer(): void {
    this._isDetailDrawerOpen.set(false);
  }

  // Modal actions
  openModal(modal: ModalType): void {
    this._activeModal.set(modal);
  }

  closeModal(): void {
    this._activeModal.set(null);
  }
}
