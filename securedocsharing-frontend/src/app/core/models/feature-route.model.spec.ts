import {
  isWorkspaceContentFeature,
  isPersonalSpaceFeature,
  isOrganizationAdminFeature,
  isSharedAccountFeature,
  isOrgOnlyFeature,
  buildCanonicalRoute,
  WORKSPACE_CONTENT_FEATURES,
  PERSONAL_SPACE_FEATURES,
  ORGANIZATION_ADMIN_FEATURES,
  SHARED_ACCOUNT_FEATURES,
} from './feature-route.model';

describe('Feature Route Scope Classification & Canonical Route Builder', () => {
  describe('Classification Functions', () => {
    it('should correctly classify workspace content features', () => {
      WORKSPACE_CONTENT_FEATURES.forEach((feature) => {
        expect(isWorkspaceContentFeature(feature)).toBeTrue();
      });
      expect(isWorkspaceContentFeature('members')).toBeFalse();
      expect(isWorkspaceContentFeature('teams')).toBeFalse();
      expect(isWorkspaceContentFeature('settings')).toBeFalse();
      expect(isWorkspaceContentFeature('shared-with-me')).toBeFalse();
      expect(isWorkspaceContentFeature('shared-by-me')).toBeFalse();
    });

    it('should correctly classify personal space features', () => {
      PERSONAL_SPACE_FEATURES.forEach((feature) => {
        expect(isPersonalSpaceFeature(feature)).toBeTrue();
      });
      expect(isPersonalSpaceFeature('members')).toBeFalse();
      expect(isPersonalSpaceFeature('teams')).toBeFalse();
      expect(isPersonalSpaceFeature('explorer')).toBeFalse();
      expect(isPersonalSpaceFeature('shared-with-me')).toBeTrue();
      expect(isPersonalSpaceFeature('shared-by-me')).toBeTrue();
    });

    it('should correctly classify organization administration features', () => {
      ORGANIZATION_ADMIN_FEATURES.forEach((feature) => {
        expect(isOrganizationAdminFeature(feature)).toBeTrue();
      });
      expect(isOrganizationAdminFeature('dashboard')).toBeFalse();
      expect(isOrganizationAdminFeature('explorer')).toBeFalse();
      expect(isOrganizationAdminFeature('settings')).toBeFalse();
    });

    it('should correctly classify shared account features', () => {
      SHARED_ACCOUNT_FEATURES.forEach((feature) => {
        expect(isSharedAccountFeature(feature)).toBeTrue();
      });
      expect(isSharedAccountFeature('dashboard')).toBeFalse();
      expect(isSharedAccountFeature('members')).toBeFalse();
      expect(isSharedAccountFeature('teams')).toBeFalse();
    });

    it('should correctly identify organization-only features', () => {
      expect(isOrgOnlyFeature('members')).toBeTrue();
      expect(isOrgOnlyFeature('teams')).toBeTrue();
      expect(isOrgOnlyFeature('dashboard')).toBeFalse();
      expect(isOrgOnlyFeature('settings')).toBeFalse();
      expect(isOrgOnlyFeature('audit')).toBeFalse();
    });
  });

  describe('buildCanonicalRoute', () => {
    describe('Shared Discovery Views (strictly personal discovery routes)', () => {
      it('should route shared discovery views strictly to /w/personal/:feature regardless of context or workspaceId', () => {
        expect(buildCanonicalRoute('shared-with-me', null, null)).toEqual(['/w', 'personal', 'shared-with-me']);
        expect(buildCanonicalRoute('shared-with-me', null, 42)).toEqual(['/w', 'personal', 'shared-with-me']);
        expect(buildCanonicalRoute('shared-with-me', 'ACME', null)).toEqual(['/w', 'personal', 'shared-with-me']);
        expect(buildCanonicalRoute('shared-with-me', 'ACME', 7)).toEqual(['/w', 'personal', 'shared-with-me']);

        expect(buildCanonicalRoute('shared-by-me', null, null)).toEqual(['/w', 'personal', 'shared-by-me']);
        expect(buildCanonicalRoute('shared-by-me', null, 42)).toEqual(['/w', 'personal', 'shared-by-me']);
        expect(buildCanonicalRoute('shared-by-me', 'ACME', null)).toEqual(['/w', 'personal', 'shared-by-me']);
        expect(buildCanonicalRoute('shared-by-me', 'ACME', 7)).toEqual(['/w', 'personal', 'shared-by-me']);
      });
    });

    describe('Personal Context (orgCode is null/undefined)', () => {
      it('should distinguish the two dashboard scopes: Personal Space Home vs Personal Workspace Dashboard', () => {
        // Scope 1: Personal Space Home (no numeric workspace ID)
        expect(buildCanonicalRoute('dashboard', null, null)).toEqual(['/w', 'personal', 'dashboard']);
        expect(buildCanonicalRoute('dashboard', undefined, undefined)).toEqual(['/w', 'personal', 'dashboard']);

        // Scope 2: Personal Workspace Dashboard (valid numeric workspace ID present)
        expect(buildCanonicalRoute('dashboard', null, 42)).toEqual(['/w', 'personal', 'w', '42', 'dashboard']);
        expect(buildCanonicalRoute('dashboard', undefined, 10)).toEqual(['/w', 'personal', 'w', '10', 'dashboard']);
      });

      it('should route personal workspace content features to /w/personal/w/:numericId/:feature when numeric ID is present', () => {
        expect(buildCanonicalRoute('explorer', null, 42)).toEqual(['/w', 'personal', 'w', '42', 'explorer']);
        expect(buildCanonicalRoute('permissions', null, 42)).toEqual(['/w', 'personal', 'w', '42', 'permissions']);
      });

      it('should fallback workspace content features to /w/personal/:feature when numeric ID is absent', () => {
        expect(buildCanonicalRoute('dashboard', null, null)).toEqual(['/w', 'personal', 'dashboard']);
        expect(buildCanonicalRoute('explorer', null, null)).toEqual(['/w', 'personal', 'explorer']);
        expect(buildCanonicalRoute('permissions', null, null)).toEqual(['/w', 'personal', 'permissions']);
      });

      it('should route shared account features to /w/personal/:feature even if workspace ID is provided', () => {
        expect(buildCanonicalRoute('settings', null, 42)).toEqual(['/w', 'personal', 'settings']);
        expect(buildCanonicalRoute('audit', null, 42)).toEqual(['/w', 'personal', 'audit']);
        expect(buildCanonicalRoute('invitations', null, null)).toEqual(['/w', 'personal', 'invitations']);
        expect(buildCanonicalRoute('profile', null, undefined)).toEqual(['/w', 'personal', 'profile']);
      });

      it('should guard org-only features in personal context by falling back to dashboard', () => {
        expect(buildCanonicalRoute('members', null, null)).toEqual(['/w', 'personal', 'dashboard']);
        expect(buildCanonicalRoute('teams', null, null)).toEqual(['/w', 'personal', 'dashboard']);
      });
    });

    describe('Organization Context (orgCode is present)', () => {
      it('should route org-level admin features to /org/:orgCode/:feature', () => {
        expect(buildCanonicalRoute('members', 'ACME', 42)).toEqual(['/org', 'ACME', 'members']);
        expect(buildCanonicalRoute('teams', 'ACME', 42)).toEqual(['/org', 'ACME', 'teams']);
      });

      it('should route shared account features to /org/:orgCode/:feature in org context', () => {
        expect(buildCanonicalRoute('audit', 'ACME', 42)).toEqual(['/org', 'ACME', 'audit']);
        expect(buildCanonicalRoute('invitations', 'ACME', 42)).toEqual(['/org', 'ACME', 'invitations']);
        expect(buildCanonicalRoute('settings', 'ACME', 42)).toEqual(['/org', 'ACME', 'settings']);
        expect(buildCanonicalRoute('profile', 'ACME', 42)).toEqual(['/org', 'ACME', 'profile']);
      });

      it('should route workspace content features with numeric workspaceId to /org/:orgCode/w/:numericId/:feature', () => {
        expect(buildCanonicalRoute('dashboard', 'ACME', 42)).toEqual(['/org', 'ACME', 'w', '42', 'dashboard']);
        expect(buildCanonicalRoute('explorer', 'ACME', 105)).toEqual(['/org', 'ACME', 'w', '105', 'explorer']);
        expect(buildCanonicalRoute('permissions', 'ACME', 7)).toEqual(['/org', 'ACME', 'w', '7', 'permissions']);
      });

      it('should accept 0 as a valid numeric workspaceId', () => {
        expect(buildCanonicalRoute('dashboard', 'ACME', 0)).toEqual(['/org', 'ACME', 'w', '0', 'dashboard']);
      });

      it('should fallback workspace content features to /org/:orgCode/w/default/:feature when workspace ID is not numeric', () => {
        const routeNull = buildCanonicalRoute('dashboard', 'ACME', null);
        expect(routeNull).toEqual(['/org', 'ACME', 'w', 'default', 'dashboard']);

        const routeUndefined = buildCanonicalRoute('explorer', 'ACME', undefined);
        expect(routeUndefined).toEqual(['/org', 'ACME', 'w', 'default', 'explorer']);

        const routeNaN = buildCanonicalRoute('permissions', 'ACME', NaN);
        expect(routeNaN).toEqual(['/org', 'ACME', 'w', 'default', 'permissions']);
      });
    });
  });
});
