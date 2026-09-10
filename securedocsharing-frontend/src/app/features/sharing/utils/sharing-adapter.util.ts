import { SharedItem } from '../models/sharing.model';
import { IconName } from '../../../shared/components/icon/icon.component';

export interface DrawerItemAdapter {
  type: 'folder' | 'document';
  data: {
    id: number;
    name: string;
    fileName: string;
    fileSize?: number | null;
    fileSizeBytes?: number | null;
    fileHash?: string | null;
    contentType?: string | null;
    mimeType?: string | null;
    createdAt?: string | null;
    grantedAt?: string | null;
    uploadedByUserId?: string | null;
    uploadedBy?: string | null;
    ownerOrgCode?: string | null;
    ownerOrgName?: string | null;
    workspaceId?: number | null;
    workspaceName?: string | null;
    permissionLevel?: string | null;
    permissionType?: string | null;
    sourceType?: string | null;
    external?: boolean | null;
    [key: string]: any;
  };
  tab?: 'overview' | 'access';
}

/**
 * Maps a SharedItem (from Shared With Me or Shared By Me) to the format expected by ItemDetailDrawer.
 */
export function sharedItemToDrawerItem(
  item: SharedItem,
  tab: 'overview' | 'access' = 'overview'
): DrawerItemAdapter {
  const isFolder = item.resourceType === 'FOLDER';
  const uploaderOrGrantor = item.grantedByName || item.grantedByUserEmail || 'Shared Access';

  return {
    type: isFolder ? 'folder' : 'document',
    data: {
      id: item.resourceId,
      name: item.name,
      fileName: item.name,
      fileSize: item.fileSize ?? null,
      fileSizeBytes: item.fileSize ?? null,
      fileHash: item.fileHash ?? null,
      contentType: item.contentType ?? null,
      mimeType: item.contentType ?? null,
      createdAt: item.grantedAt ?? null,
      grantedAt: item.grantedAt ?? null,
      uploadedByUserId: item.grantedByUserId ?? null,
      uploadedBy: uploaderOrGrantor,
      ownerOrgCode: item.ownerOrgCode ?? null,
      ownerOrgName: item.ownerOrgName ?? null,
      workspaceId: item.workspaceId ?? null,
      workspaceName: item.workspaceName ?? null,
      permissionLevel: item.permissionLevel,
      permissionType: item.permissionType,
      sourceType: item.sourceType ?? null,
      external: item.external ?? false,
    },
    tab,
  };
}

/**
 * Returns the appropriate icon name for a shared item.
 */
export function getSharedItemIcon(item: SharedItem): IconName {
  if (item.resourceType === 'FOLDER') {
    return 'folder';
  }

  const ct = (item.contentType || '').toLowerCase();
  const name = (item.name || '').toLowerCase();

  if (ct.startsWith('image/') || /\.(png|jpe?g|gif|webp|svg|bmp)$/i.test(name)) {
    return 'image';
  }
  if (
    ct === 'application/pdf' ||
    /\.(pdf)$/i.test(name) ||
    ct.includes('document') ||
    ct.includes('word') ||
    /\.(docx?|odt|txt|rtf|md)$/i.test(name)
  ) {
    return 'file-text';
  }

  return 'file';
}

/**
 * Human-readable byte size formatter with tabular numerals.
 */
export function formatBytes(bytes?: number | null): string {
  if (bytes === undefined || bytes === null || isNaN(bytes) || bytes <= 0) {
    return '—';
  }
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let size = bytes;
  let unitIdx = 0;

  while (size >= 1024 && unitIdx < units.length - 1) {
    size /= 1024;
    unitIdx++;
  }

  const formatted = unitIdx === 0 ? Math.round(size) : size.toFixed(1);
  return `${formatted} ${units[unitIdx]}`;
}

/**
 * Human-readable formatted date string.
 */
export function formatSharingDate(dateStr?: string | null): string {
  if (!dateStr) return '—';
  try {
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return '—';
    return d.toLocaleDateString(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    });
  } catch {
    return '—';
  }
}

/**
 * Checks if a share lifecycle is no longer active.
 */
export function isShareInactive(item: SharedItem): boolean {
  if (item.revoked === true) return true;
  if (item.suspended === true) return true;
  if (item.status === 'REVOKED' || item.status === 'SUSPENDED' || item.status === 'EXPIRED') {
    return true;
  }
  if (item.expiresAt) {
    const exp = new Date(item.expiresAt).getTime();
    if (!isNaN(exp) && exp < Date.now()) {
      return true;
    }
  }
  return false;
}
