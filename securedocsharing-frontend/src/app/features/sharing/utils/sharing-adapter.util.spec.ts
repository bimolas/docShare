import {
  sharedItemToDrawerItem,
  getSharedItemIcon,
  formatBytes,
  formatSharingDate,
  isShareInactive,
} from './sharing-adapter.util';
import { SharedItem } from '../models/sharing.model';

describe('sharing-adapter.util', () => {
  const sampleFolder: SharedItem = {
    permissionId: 1,
    resourceType: 'FOLDER',
    resourceId: 100,
    name: 'Shared Folder',
    permissionType: 'ALLOW',
    permissionLevel: 'EDIT',
    grantedByName: 'Alice',
    grantedByUserEmail: 'alice@example.com',
    ownerOrgCode: 'CORP',
    workspaceId: 5,
  };

  const sampleDoc: SharedItem = {
    permissionId: 2,
    resourceType: 'DOCUMENT',
    resourceId: 200,
    name: 'Report.pdf',
    contentType: 'application/pdf',
    fileSize: 2048,
    permissionType: 'ALLOW',
    permissionLevel: 'DOWNLOAD',
  };

  describe('sharedItemToDrawerItem', () => {
    it('should map folder SharedItem correctly to drawer item format', () => {
      const drawerItem = sharedItemToDrawerItem(sampleFolder, 'access');
      expect(drawerItem.type).toBe('folder');
      expect(drawerItem.data.id).toBe(100);
      expect(drawerItem.data.name).toBe('Shared Folder');
      expect(drawerItem.data.uploadedBy).toBe('Alice');
      expect(drawerItem.data.ownerOrgCode).toBe('CORP');
      expect(drawerItem.tab).toBe('access');
    });

    it('should map document SharedItem correctly to drawer item format', () => {
      const drawerItem = sharedItemToDrawerItem(sampleDoc, 'overview');
      expect(drawerItem.type).toBe('document');
      expect(drawerItem.data.id).toBe(200);
      expect(drawerItem.data.name).toBe('Report.pdf');
      expect(drawerItem.data.fileSize).toBe(2048);
      expect(drawerItem.tab).toBe('overview');
    });
  });

  describe('getSharedItemIcon', () => {
    it('should return folder for folders', () => {
      expect(getSharedItemIcon(sampleFolder)).toBe('folder');
    });

    it('should return file-text for pdf and text docs', () => {
      expect(getSharedItemIcon(sampleDoc)).toBe('file-text');
      expect(getSharedItemIcon({ ...sampleDoc, name: 'note.txt', contentType: 'text/plain' })).toBe('file-text');
    });

    it('should return image for image files', () => {
      expect(getSharedItemIcon({ ...sampleDoc, name: 'photo.png', contentType: 'image/png' })).toBe('image');
    });

    it('should return file for generic unknown documents', () => {
      expect(getSharedItemIcon({ ...sampleDoc, name: 'archive.bin', contentType: 'application/octet-stream' })).toBe('file');
    });
  });

  describe('formatBytes', () => {
    it('should format sizes into human-readable strings', () => {
      expect(formatBytes(0)).toBe('—');
      expect(formatBytes(null)).toBe('—');
      expect(formatBytes(500)).toBe('500 B');
      expect(formatBytes(1024)).toBe('1.0 KB');
      expect(formatBytes(1048576)).toBe('1.0 MB');
      expect(formatBytes(1073741824)).toBe('1.0 GB');
    });
  });

  describe('formatSharingDate', () => {
    it('should format valid dates and return placeholder for empty dates', () => {
      expect(formatSharingDate(null)).toBe('—');
      expect(formatSharingDate('')).toBe('—');
      expect(formatSharingDate('2026-03-01T10:00:00Z')).toContain('2026');
    });
  });

  describe('isShareInactive', () => {
    it('should return true for revoked or suspended shares', () => {
      expect(isShareInactive({ ...sampleDoc, revoked: true })).toBeTrue();
      expect(isShareInactive({ ...sampleDoc, suspended: true })).toBeTrue();
      expect(isShareInactive({ ...sampleDoc, status: 'REVOKED' })).toBeTrue();
      expect(isShareInactive({ ...sampleDoc, status: 'EXPIRED' })).toBeTrue();
    });

    it('should return true for past expiration dates', () => {
      expect(isShareInactive({ ...sampleDoc, expiresAt: '2020-01-01T00:00:00Z' })).toBeTrue();
    });

    it('should return false for active shares', () => {
      expect(isShareInactive({ ...sampleDoc, status: 'ACTIVE', revoked: false })).toBeFalse();
    });
  });
});
