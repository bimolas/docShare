/**
 * Authoritative Resource Ownership Utility
 *
 * Implements canonical domain rules for determining resource ownership:
 * 1. A user is the owner of a personal resource ONLY if their identity matches
 *    the resource's authoritative creator/uploader metadata.
 * 2. Stable User ID matching is primary authority (Keycloak UUID trimmed and case-insensitive).
 * 3. Owner Email matching is secondary authority (evaluated ONLY when User ID is unavailable on the resource).
 * 4. Active workspace/organization context MUST NEVER determine or fallback ownership.
 * 5. Being a member/manager of an organization never grants personal ownership.
 * 6. Missing or unresolvable owner metadata MUST return false (never assume ownership).
 */

export interface ResourceOwnerMetadata {
  createdByUserId?: string | null;
  uploadedByUserId?: string | null;
  ownerUserId?: string | null;
  ownerId?: string | null;
  userId?: string | null;
  createdBy?:
    | {
        id?: string | number | null;
        email?: string | null;
        fullName?: string | null;
        username?: string | null;
      }
    | string
    | null;
  uploadedBy?:
    | {
        id?: string | number | null;
        email?: string | null;
        fullName?: string | null;
        username?: string | null;
      }
    | string
    | null;
  owner?:
    | {
        id?: string | number | null;
        email?: string | null;
        fullName?: string | null;
        username?: string | null;
      }
    | string
    | null;
  createdByEmail?: string | null;
  uploadedByUserEmail?: string | null;
  ownerEmail?: string | null;
  [key: string]: any;
}

export interface ResourcePeekOwnerMetadata {
  createdByUserId?: string | null;
  createdByEmail?: string | null;
  ownerEmail?: string | null;
  [key: string]: any;
}

export interface EffectivePermissionsOwnerMetadata {
  ownerEmail?: string | null;
  userAccessSummaries?: Array<{
    email?: string | null;
    accessSource?: string | null;
    effectiveAccessLevel?: string | null;
    [key: string]: any;
  }> | null;
  [key: string]: any;
}

export interface CurrentUserOwnerMetadata {
  id?: string | number | null;
  email?: string | null;
  [key: string]: any;
}

/**
 * Extracts the authoritative owner user ID from resource metadata or peek data.
 * Respects itemType ('folder' vs 'document') when provided.
 * Trims whitespace and normalizes string IDs.
 */
export function getResourceOwnerUserId(
  data?: ResourceOwnerMetadata | null,
  peekData?: ResourcePeekOwnerMetadata | null,
  itemType?: 'folder' | 'document' | string | null
): string | null {
  if (!data && !peekData) {
    return null;
  }

  // A. Folder specific check
  if (itemType === 'folder') {
    if (data?.createdByUserId != null && String(data.createdByUserId).trim().length > 0) {
      return String(data.createdByUserId).trim();
    }
    if (data?.createdBy && typeof data.createdBy === 'object' && data.createdBy.id != null) {
      const val = String(data.createdBy.id).trim();
      if (val.length > 0) return val;
    }
    if (typeof data?.createdBy === 'string' && data.createdBy.trim().length > 0 && !data.createdBy.includes('@')) {
      return data.createdBy.trim();
    }
    if (peekData?.createdByUserId != null && String(peekData.createdByUserId).trim().length > 0) {
      return String(peekData.createdByUserId).trim();
    }
    // Generic fallback for folders
    if (data?.ownerUserId != null && String(data.ownerUserId).trim().length > 0) {
      return String(data.ownerUserId).trim();
    }
    if (data?.ownerId != null && String(data.ownerId).trim().length > 0) {
      return String(data.ownerId).trim();
    }
    if (data?.userId != null && String(data.userId).trim().length > 0) {
      return String(data.userId).trim();
    }
    return null;
  }

  // B. Document specific check
  if (itemType === 'document') {
    if (data?.uploadedByUserId != null && String(data.uploadedByUserId).trim().length > 0) {
      const val = String(data.uploadedByUserId).trim();
      if (!val.includes('@')) return val;
    }
    if (data?.uploadedBy && typeof data.uploadedBy === 'object' && data.uploadedBy.id != null) {
      const val = String(data.uploadedBy.id).trim();
      if (val.length > 0) return val;
    }
    if (typeof data?.uploadedBy === 'string' && data.uploadedBy.trim().length > 0 && !data.uploadedBy.includes('@')) {
      return data.uploadedBy.trim();
    }
    // Generic fallback for documents
    if (data?.createdByUserId != null && String(data.createdByUserId).trim().length > 0) {
      return String(data.createdByUserId).trim();
    }
    if (data?.ownerUserId != null && String(data.ownerUserId).trim().length > 0) {
      return String(data.ownerUserId).trim();
    }
    if (data?.ownerId != null && String(data.ownerId).trim().length > 0) {
      return String(data.ownerId).trim();
    }
    if (data?.userId != null && String(data.userId).trim().length > 0) {
      return String(data.userId).trim();
    }
    return null;
  }

  // C. Unspecified / General check
  if (data?.createdByUserId != null && String(data.createdByUserId).trim().length > 0) {
    return String(data.createdByUserId).trim();
  }
  if (data?.uploadedByUserId != null && String(data.uploadedByUserId).trim().length > 0) {
    const val = String(data.uploadedByUserId).trim();
    if (!val.includes('@')) return val;
  }
  if (data?.createdBy && typeof data.createdBy === 'object' && data.createdBy.id != null) {
    const val = String(data.createdBy.id).trim();
    if (val.length > 0) return val;
  }
  if (data?.uploadedBy && typeof data.uploadedBy === 'object' && data.uploadedBy.id != null) {
    const val = String(data.uploadedBy.id).trim();
    if (val.length > 0) return val;
  }
  if (data?.ownerUserId != null && String(data.ownerUserId).trim().length > 0) {
    return String(data.ownerUserId).trim();
  }
  if (data?.ownerId != null && String(data.ownerId).trim().length > 0) {
    return String(data.ownerId).trim();
  }
  if (data?.userId != null && String(data.userId).trim().length > 0) {
    return String(data.userId).trim();
  }
  if (data?.owner && typeof data.owner === 'object' && data.owner.id != null) {
    const val = String(data.owner.id).trim();
    if (val.length > 0) return val;
  }
  if (typeof data?.uploadedBy === 'string' && data.uploadedBy.trim().length > 0 && !data.uploadedBy.includes('@')) {
    return data.uploadedBy.trim();
  }
  if (typeof data?.createdBy === 'string' && data.createdBy.trim().length > 0 && !data.createdBy.includes('@')) {
    return data.createdBy.trim();
  }
  if (peekData?.createdByUserId != null && String(peekData.createdByUserId).trim().length > 0) {
    return String(peekData.createdByUserId).trim();
  }

  return null;
}

/**
 * Extracts the authoritative owner email from resource metadata, peek data,
 * or backend effective permissions audit.
 * Returns normalized trimmed lowercase email, or null.
 */
export function getResourceOwnerEmail(
  data?: ResourceOwnerMetadata | null,
  peekData?: ResourcePeekOwnerMetadata | null,
  effectivePermissions?: EffectivePermissionsOwnerMetadata | null,
  itemType?: 'folder' | 'document' | string | null
): string | null {
  if (!data && !peekData && !effectivePermissions) {
    return null;
  }

  // 1. Explicit document email
  if (itemType !== 'folder') {
    if (typeof data?.uploadedByUserEmail === 'string' && data.uploadedByUserEmail.trim().length > 0) {
      return data.uploadedByUserEmail.trim().toLowerCase();
    }
    if (
      data?.uploadedBy &&
      typeof data.uploadedBy === 'object' &&
      typeof data.uploadedBy.email === 'string' &&
      data.uploadedBy.email.trim().length > 0
    ) {
      return data.uploadedBy.email.trim().toLowerCase();
    }
    if (typeof data?.uploadedBy === 'string' && data.uploadedBy.includes('@')) {
      return data.uploadedBy.trim().toLowerCase();
    }
    if (typeof data?.uploadedByUserId === 'string' && data.uploadedByUserId.includes('@')) {
      return data.uploadedByUserId.trim().toLowerCase();
    }
  }

  // 2. Explicit folder email
  if (itemType !== 'document') {
    if (typeof data?.createdByEmail === 'string' && data.createdByEmail.trim().length > 0) {
      return data.createdByEmail.trim().toLowerCase();
    }
    if (
      data?.createdBy &&
      typeof data.createdBy === 'object' &&
      typeof data.createdBy.email === 'string' &&
      data.createdBy.email.trim().length > 0
    ) {
      return data.createdBy.email.trim().toLowerCase();
    }
    if (typeof data?.createdBy === 'string' && data.createdBy.includes('@')) {
      return data.createdBy.trim().toLowerCase();
    }
    if (typeof peekData?.createdByEmail === 'string' && peekData.createdByEmail.trim().length > 0) {
      return peekData.createdByEmail.trim().toLowerCase();
    }
  }

  // 3. General owner email on data/peekData
  if (typeof data?.ownerEmail === 'string' && data.ownerEmail.trim().length > 0) {
    return data.ownerEmail.trim().toLowerCase();
  }
  if (
    data?.owner &&
    typeof data.owner === 'object' &&
    typeof data.owner.email === 'string' &&
    data.owner.email.trim().length > 0
  ) {
    return data.owner.email.trim().toLowerCase();
  }
  if (typeof peekData?.ownerEmail === 'string' && peekData.ownerEmail.trim().length > 0) {
    return peekData.ownerEmail.trim().toLowerCase();
  }

  // 4. Authoritative backend effectivePermissions.ownerEmail
  if (
    typeof effectivePermissions?.ownerEmail === 'string' &&
    effectivePermissions.ownerEmail.trim().length > 0 &&
    effectivePermissions.ownerEmail.trim().toUpperCase() !== 'SYSTEM'
  ) {
    return effectivePermissions.ownerEmail.trim().toLowerCase();
  }

  // 5. Authoritative backend effectivePermissions.userAccessSummaries where accessSource === 'OWNER'
  if (Array.isArray(effectivePermissions?.userAccessSummaries)) {
    const ownerSummary = effectivePermissions.userAccessSummaries.find(
      (u) =>
        u &&
        typeof u.accessSource === 'string' &&
        u.accessSource.trim().toUpperCase() === 'OWNER' &&
        typeof u.email === 'string' &&
        u.email.trim().length > 0 &&
        u.email.trim().toUpperCase() !== 'SYSTEM'
    );
    if (ownerSummary?.email) {
      return ownerSummary.email.trim().toLowerCase();
    }
  }

  return null;
}

/**
 * Determines authoritatively whether the current user is the owner of a resource.
 *
 * Precedence:
 * 1. User ID Match (Primary Authority):
 *    If an owner user ID exists on the resource / peek data, it is checked against currentUser.id.
 *    Comparison is trimmed and case-insensitive.
 *    If an owner user ID is present on the resource, this check is DECISIVE:
 *    matching yields true; mismatching strictly yields false (no email fallback).
 * 2. Owner Email Match (Secondary Authority):
 *    Evaluated ONLY when owner user ID is absent from the resource metadata and peek data.
 *    Checks resource email fields, peekData, and backend effectivePermissions.ownerEmail.
 *    Comparison is trimmed and case-insensitive.
 * 3. Strict Rejection:
 *    If neither ID nor email matches, or if owner metadata is completely absent, returns false.
 *    Never guesses from active workspace or UI context.
 */
export function isResourceOwner(
  currentUser?: CurrentUserOwnerMetadata | null,
  data?: ResourceOwnerMetadata | null,
  peekData?: ResourcePeekOwnerMetadata | null,
  effectivePermissions?: EffectivePermissionsOwnerMetadata | null,
  itemType?: 'folder' | 'document' | string | null
): boolean {
  if (!currentUser) {
    return false;
  }

  const currentUserId =
    currentUser.id != null && String(currentUser.id).trim().length > 0
      ? String(currentUser.id).trim().toLowerCase()
      : null;
  const currentUserEmail =
    currentUser.email != null && currentUser.email.trim().length > 0
      ? currentUser.email.trim().toLowerCase()
      : null;

  if (!currentUserId && !currentUserEmail) {
    return false;
  }

  // 1. Primary Authority: User ID
  const ownerUserId = getResourceOwnerUserId(data, peekData, itemType);
  if (ownerUserId != null) {
    if (currentUserId && ownerUserId.toLowerCase() === currentUserId) {
      return true;
    }
    // Owner User ID was explicitly present on the resource and did NOT match current user ID.
    // Strict rejection: do not fall back to email to prevent cross-account identity spoofing.
    return false;
  }

  // 2. Secondary Authority: Owner Email (when resource lacks owner User ID)
  const ownerEmail = getResourceOwnerEmail(data, peekData, effectivePermissions, itemType);
  if (ownerEmail != null) {
    if (currentUserEmail && ownerEmail.toLowerCase() === currentUserEmail) {
      return true;
    }
    return false;
  }

  // 3. Rejection: No owner metadata could be established
  return false;
}
