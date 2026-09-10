import {
  getResourceOwnerOrgCode,
  isOrganizationResource,
  isPersonalResource,
} from './resource-classification.util';

describe('resource-classification.util', () => {
  describe('getResourceOwnerOrgCode', () => {
    it('should return null when data and peekData are null or undefined', () => {
      expect(getResourceOwnerOrgCode(null)).toBeNull();
      expect(getResourceOwnerOrgCode(undefined)).toBeNull();
      expect(getResourceOwnerOrgCode({}, null)).toBeNull();
    });

    it('should return trimmed ownerOrgCode when present as non-empty string', () => {
      expect(getResourceOwnerOrgCode({ ownerOrgCode: 'ACME' })).toBe('ACME');
      expect(getResourceOwnerOrgCode({ ownerOrgCode: '  BETA_CORP  ' })).toBe('BETA_CORP');
    });

    it('should return null when ownerOrgCode is null or empty string', () => {
      expect(getResourceOwnerOrgCode({ ownerOrgCode: null })).toBeNull();
      expect(getResourceOwnerOrgCode({ ownerOrgCode: '' })).toBeNull();
      expect(getResourceOwnerOrgCode({ ownerOrgCode: '   ' })).toBeNull();
    });

    it('should support orgCode property when ownerOrgCode is absent', () => {
      expect(getResourceOwnerOrgCode({ orgCode: 'OMEGA' })).toBe('OMEGA');
    });

    it('should support ownerOrg as string or object with orgCode', () => {
      expect(getResourceOwnerOrgCode({ ownerOrg: 'DELTA' })).toBe('DELTA');
      expect(getResourceOwnerOrgCode({ ownerOrg: { orgCode: 'SIGMA' } })).toBe('SIGMA');
    });

    it('should fall back to peekData when data does not provide orgCode', () => {
      expect(getResourceOwnerOrgCode({}, { ownerOrgCode: 'ACME' })).toBe('ACME');
      expect(getResourceOwnerOrgCode({ id: 10 }, { orgCode: 'PEEK_ORG' })).toBe('PEEK_ORG');
      expect(getResourceOwnerOrgCode({ id: 10 }, { ownerOrgCode: null })).toBeNull();
    });
  });

  describe('isOrganizationResource', () => {
    it('should return false for null or undefined data without peekData', () => {
      expect(isOrganizationResource(null)).toBeFalse();
      expect(isOrganizationResource(undefined)).toBeFalse();
      expect(isOrganizationResource({})).toBeFalse();
    });

    it('should return true when explicit ownerOrgCode is present', () => {
      expect(isOrganizationResource({ ownerOrgCode: 'ACME' })).toBeTrue();
      expect(isOrganizationResource({ id: 1, ownerOrgCode: 'CORP' })).toBeTrue();
    });

    it('should return true when spaceType is ORGANIZATION', () => {
      expect(isOrganizationResource({ spaceType: 'ORGANIZATION' })).toBeTrue();
    });

    it('should return true when subspaceType is an organization subspace', () => {
      expect(isOrganizationResource({ subspaceType: 'ORGANIZATION_GENERAL' })).toBeTrue();
      expect(isOrganizationResource({ subspaceType: 'PROJECT_WORKSPACE' })).toBeTrue();
      expect(isOrganizationResource({ subspaceType: 'COLLABORATION' })).toBeTrue();
    });

    it('should return true when peekData has an organization ownerOrgCode', () => {
      expect(isOrganizationResource({ id: 10 }, { ownerOrgCode: 'PEEK_ORG' })).toBeTrue();
    });

    it('should return false for personal resources with explicit null or personal markers', () => {
      expect(isOrganizationResource({ ownerOrgCode: null, subspaceType: 'PERSONAL' })).toBeFalse();
      expect(isOrganizationResource({ spaceType: 'PERSONAL', ownerOrgCode: null })).toBeFalse();
      expect(isOrganizationResource({ id: 5, ownerOrgCode: null })).toBeFalse();
      expect(isOrganizationResource({ id: 5 }, { ownerOrgCode: null })).toBeFalse();
    });
  });

  describe('isPersonalResource', () => {
    it('should return true for null or empty metadata (does not silently mean organization)', () => {
      expect(isPersonalResource(null)).toBeTrue();
      expect(isPersonalResource({})).toBeTrue();
    });

    it('should return true when ownerOrgCode is null', () => {
      expect(isPersonalResource({ id: 10, ownerOrgCode: null })).toBeTrue();
    });

    it('should return true when spaceType is PERSONAL', () => {
      expect(isPersonalResource({ id: 20, spaceType: 'PERSONAL', ownerOrgCode: null })).toBeTrue();
    });

    it('should return true when subspaceType is PERSONAL', () => {
      expect(isPersonalResource({ id: 30, subspaceType: 'PERSONAL', ownerOrgCode: null })).toBeTrue();
    });

    it('should return false when resource has organization markers', () => {
      expect(isPersonalResource({ ownerOrgCode: 'ACME' })).toBeFalse();
      expect(isPersonalResource({ spaceType: 'ORGANIZATION' })).toBeFalse();
      expect(isPersonalResource({ subspaceType: 'PROJECT_WORKSPACE' })).toBeFalse();
      expect(isPersonalResource({}, { ownerOrgCode: 'ACME' })).toBeFalse();
    });
  });
});
