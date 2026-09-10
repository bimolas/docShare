import {
  isResourceOwner,
  getResourceOwnerUserId,
  getResourceOwnerEmail,
} from './resource-ownership.util';

describe('Resource Ownership Utilities (Cluster D)', () => {
  const aliceUser = {
    id: 'user-alice-1234',
    email: 'alice@example.com',
  };

  const bobUser = {
    id: 'user-bob-5678',
    email: 'bob@example.com',
  };

  describe('getResourceOwnerUserId', () => {
    it('should resolve createdByUserId for folders', () => {
      const id = getResourceOwnerUserId({ createdByUserId: 'user-alice-1234' }, null, 'folder');
      expect(id).toBe('user-alice-1234');
    });

    it('should resolve uploadedByUserId for documents', () => {
      const id = getResourceOwnerUserId({ uploadedByUserId: 'user-alice-1234' }, null, 'document');
      expect(id).toBe('user-alice-1234');
    });

    it('should ignore uploadedByUserId if it looks like an email string', () => {
      const id = getResourceOwnerUserId({ uploadedByUserId: 'alice@example.com' }, null, 'document');
      expect(id).toBeNull();
    });

    it('should fallback to peekData createdByUserId when resource data lacks ID', () => {
      const id = getResourceOwnerUserId({ id: 10, name: 'Light Folder' }, { createdByUserId: 'user-alice-1234' }, 'folder');
      expect(id).toBe('user-alice-1234');
    });

    it('should resolve nested createdBy.id and uploadedBy.id', () => {
      expect(getResourceOwnerUserId({ createdBy: { id: 'user-alice-1234' } })).toBe('user-alice-1234');
      expect(getResourceOwnerUserId({ uploadedBy: { id: 'user-alice-1234' } })).toBe('user-alice-1234');
    });

    it('should return null when no ID is present on data or peekData', () => {
      expect(getResourceOwnerUserId(null, null)).toBeNull();
      expect(getResourceOwnerUserId({}, {})).toBeNull();
      expect(getResourceOwnerUserId({ id: 99, name: 'No Owner Info' })).toBeNull();
    });
  });

  describe('getResourceOwnerEmail', () => {
    it('should resolve uploadedByUserEmail for documents', () => {
      const email = getResourceOwnerEmail({ uploadedByUserEmail: 'Alice@Example.COM' }, null, null, 'document');
      expect(email).toBe('alice@example.com');
    });

    it('should resolve createdByEmail for folders', () => {
      const email = getResourceOwnerEmail({ createdByEmail: 'Alice@Example.COM' }, null, null, 'folder');
      expect(email).toBe('alice@example.com');
    });

    it('should resolve effectivePermissions.ownerEmail when resource data lacks email', () => {
      const email = getResourceOwnerEmail(
        { id: 10 },
        null,
        { ownerEmail: 'Alice@Example.COM' }
      );
      expect(email).toBe('alice@example.com');
    });

    it('should ignore SYSTEM ownerEmail from effectivePermissions', () => {
      const email = getResourceOwnerEmail(
        { id: 10 },
        null,
        { ownerEmail: 'SYSTEM' }
      );
      expect(email).toBeNull();
    });

    it('should resolve from effectivePermissions.userAccessSummaries with accessSource OWNER', () => {
      const email = getResourceOwnerEmail(
        { id: 10 },
        null,
        {
          ownerEmail: null,
          userAccessSummaries: [
            { email: 'other@example.com', accessSource: 'DIRECT' },
            { email: 'Alice@Example.COM', accessSource: 'OWNER' },
          ],
        }
      );
      expect(email).toBe('alice@example.com');
    });

    it('should return null if no email is found anywhere', () => {
      expect(getResourceOwnerEmail(null, null, null)).toBeNull();
      expect(getResourceOwnerEmail({}, {}, {})).toBeNull();
    });
  });

  describe('isResourceOwner', () => {
    it('should return true when createdByUserId matches current user id (case-insensitive)', () => {
      const result = isResourceOwner(
        aliceUser,
        { createdByUserId: 'USER-ALICE-1234' },
        null,
        null,
        'folder'
      );
      expect(result).toBeTrue();
    });

    it('should return true when uploadedByUserId matches current user id (case-insensitive)', () => {
      const result = isResourceOwner(
        aliceUser,
        { uploadedByUserId: 'USER-ALICE-1234' },
        null,
        null,
        'document'
      );
      expect(result).toBeTrue();
    });

    it('should return false when owner user ID does NOT match current user ID', () => {
      const result = isResourceOwner(
        aliceUser,
        { createdByUserId: 'user-bob-5678' },
        null,
        null,
        'folder'
      );
      expect(result).toBeFalse();
    });

    it('should NOT fall back to email if owner user ID is present on resource and mismatches', () => {
      // Even if effectivePermissions has Alice's email, if the resource data has Bob's ID, reject!
      const result = isResourceOwner(
        aliceUser,
        { createdByUserId: 'user-bob-5678' },
        null,
        { ownerEmail: 'alice@example.com' },
        'folder'
      );
      expect(result).toBeFalse();
    });

    it('should fall back to email when owner user ID is absent on resource data and peek data', () => {
      const result = isResourceOwner(
        aliceUser,
        { id: 10, name: 'Personal Receipts' }, // no createdByUserId
        null,
        { ownerEmail: 'ALICE@example.com' },
        'folder'
      );
      expect(result).toBeTrue();
    });

    it('should return false if owner email does not match current user email', () => {
      const result = isResourceOwner(
        aliceUser,
        { id: 10, name: 'Personal Receipts' },
        null,
        { ownerEmail: 'bob@example.com' },
        'folder'
      );
      expect(result).toBeFalse();
    });

    it('should return false if neither owner user ID nor owner email is available anywhere', () => {
      const result = isResourceOwner(
        aliceUser,
        { id: 10, name: 'Mystery Resource' },
        null,
        null,
        'folder'
      );
      expect(result).toBeFalse();
    });

    it('should return false if current user is null or undefined', () => {
      expect(isResourceOwner(null, { createdByUserId: 'user-1' })).toBeFalse();
      expect(isResourceOwner(undefined, { createdByUserId: 'user-1' })).toBeFalse();
    });

    it('should return false if current user has no id and no email', () => {
      expect(isResourceOwner({}, { createdByUserId: 'user-1' })).toBeFalse();
      expect(isResourceOwner({ id: '', email: '' }, { createdByUserId: 'user-1' })).toBeFalse();
    });
  });
});
