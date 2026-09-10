/**
 * Authoritative Resource Classification Utility
 *
 * Implements canonical domain rules for distinguishing PERSONAL vs ORGANIZATION resources:
 * - A resource is classified ONLY from the RESOURCE'S OWN AUTHORITATIVE METADATA.
 * - Resource classification MUST NEVER fall back to or guess from the active UI workspace context.
 * - An ORGANIZATION resource has explicit non-null owner organization metadata
 *   (e.g., ownerOrgCode, spaceType === 'ORGANIZATION', or subspaceType !== 'PERSONAL').
 * - A PERSONAL resource has ownerOrgCode === null (or spaceType === 'PERSONAL' or subspaceType === 'PERSONAL').
 * - Undefined or missing metadata does NOT silently mean organization.
 */

export interface ResourceClassificationMetadata {
  ownerOrgCode?: string | null;
  orgCode?: string | null;
  ownerOrg?: string | { orgCode?: string } | null;
  spaceType?: 'PERSONAL' | 'ORGANIZATION' | string | null;
  subspaceType?: 'PERSONAL' | 'ORGANIZATION_GENERAL' | 'PROJECT_WORKSPACE' | 'COLLABORATION' | string | null;
  [key: string]: any;
}

export interface ResourcePeekMetadata {
  ownerOrgCode?: string | null;
  orgCode?: string | null;
  [key: string]: any;
}

/**
 * Extracts the explicit authoritative owner organization code from resource metadata.
 * Returns the trimmed non-empty organization code, or null if the resource is personal or has no org owner.
 * Never falls back to active UI workspace context.
 */
export function getResourceOwnerOrgCode(
  data?: ResourceClassificationMetadata | null,
  peekData?: ResourcePeekMetadata | null
): string | null {
  if (!data && !peekData) {
    return null;
  }

  // 1. Explicit ownerOrgCode on resource data
  if (typeof data?.ownerOrgCode === 'string' && data.ownerOrgCode.trim().length > 0) {
    return data.ownerOrgCode.trim();
  }

  // 2. Explicit orgCode on resource data
  if (typeof data?.orgCode === 'string' && data.orgCode.trim().length > 0) {
    return data.orgCode.trim();
  }

  // 3. Explicit ownerOrg on resource data (string or object)
  if (data?.ownerOrg) {
    if (typeof data.ownerOrg === 'string' && data.ownerOrg.trim().length > 0) {
      return data.ownerOrg.trim();
    }
    if (
      typeof data.ownerOrg === 'object' &&
      data.ownerOrg !== null &&
      'orgCode' in data.ownerOrg &&
      typeof data.ownerOrg.orgCode === 'string' &&
      data.ownerOrg.orgCode.trim().length > 0
    ) {
      return data.ownerOrg.orgCode.trim();
    }
  }

  // 4. Authoritative peek data (e.g., FolderPeekResponseDto)
  if (typeof peekData?.ownerOrgCode === 'string' && peekData.ownerOrgCode.trim().length > 0) {
    return peekData.ownerOrgCode.trim();
  }
  if (typeof peekData?.orgCode === 'string' && peekData.orgCode.trim().length > 0) {
    return peekData.orgCode.trim();
  }

  return null;
}

/**
 * Determines whether a resource is an ORGANIZATION resource based solely on authoritative metadata.
 * Returns true if the resource has explicit non-empty organization ownership or org spaceType/subspaceType.
 * Never guesses from UI workspace context.
 */
export function isOrganizationResource(
  data?: ResourceClassificationMetadata | null,
  peekData?: ResourcePeekMetadata | null
): boolean {
  if (!data && !peekData) {
    return false;
  }

  // 1. Explicit owner org code present and non-empty
  if (getResourceOwnerOrgCode(data, peekData) !== null) {
    return true;
  }

  // 2. Explicit spaceType === 'ORGANIZATION'
  if (data?.spaceType === 'ORGANIZATION') {
    return true;
  }

  // 3. Explicit subspaceType is present and not PERSONAL
  if (data?.subspaceType && data.subspaceType !== 'PERSONAL') {
    return true;
  }

  return false;
}

/**
 * Determines whether a resource is a PERSONAL resource based solely on authoritative metadata.
 * Returns true if the resource is not an organization resource.
 * Never guesses from UI workspace context.
 */
export function isPersonalResource(
  data?: ResourceClassificationMetadata | null,
  peekData?: ResourcePeekMetadata | null
): boolean {
  return !isOrganizationResource(data, peekData);
}
