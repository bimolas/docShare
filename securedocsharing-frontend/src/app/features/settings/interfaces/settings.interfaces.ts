export interface UserMessagePrivacySettingsDto {
  allowInternalDms: boolean;
  allowExternalDms: boolean;
  readReceiptsEnabled: boolean;
}

export type WorkspaceType = 'PERSONAL' | 'ORGANIZATION';
export type ExternalSharingMode = 'ON' | 'OFF';
export type DiscoverabilityMode = 'PRIVATE' | 'TRUSTED_ONLY' | 'DISCOVERABLE';
export type ReviewerScopePolicy = 
  | 'RESOURCE_ADMINS_ONLY'
  | 'RESOURCE_AND_WORKSPACE_MANAGERS'
  | 'RESOURCE_AND_TEAM_MANAGERS'
  | 'RESOURCE_AND_COLLABORATION_MANAGERS'
  | 'ORGANIZATION_ADMINS';

/**
 * Target identifier for workspace policy operations.
 * Must be 'personal' for Personal Space user policy, or an alphanumeric organization code (orgCode).
 * Numeric ProjectWorkspace IDs must never be used.
 */
export type WorkspacePolicyTarget = 'personal' | string;

export interface WorkspacePolicyDto {
  workspaceType: WorkspaceType;
  externalSharingMode: ExternalSharingMode;
  discoverabilityMode: DiscoverabilityMode;
  allowAccessRequests: boolean;
  requireRequestReason: boolean;
  allowExternalAccessRequests: boolean;
  reviewerScopePolicy: ReviewerScopePolicy;
}
