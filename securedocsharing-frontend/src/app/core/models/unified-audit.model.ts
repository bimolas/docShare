/**
 * Canonical Unified Audit Models matching backend UnifiedAuditEventResponseDto & UnifiedAuditQueryCriteria.
 */

export type AuditScopeType =
  | 'PERSONAL'
  | 'ORGANIZATION'
  | 'WORKSPACE'
  | 'FOLDER'
  | 'DOCUMENT'
  | 'TEAM'
  | 'COLLABORATION';

export type AuditCategory =
  | 'CONTENT'
  | 'ACCESS'
  | 'SHARING'
  | 'SECURITY'
  | 'SYSTEM'
  | 'TEAM'
  | 'WORKSPACE';

export type AuditViewType =
  | 'ALL'
  | 'GOVERNANCE'
  | 'CONTENT';

export interface AuditActorDto {
  userId?: string;
  email?: string;
  displayName?: string;
  impersonatedBy?: string;
}

export interface AuditTargetDto {
  id?: number;
  type?: string;
  name?: string;
  fileHash?: string;
  fileSize?: number;
}

export interface AuditLocationDto {
  spaceType?: string;
  orgCode?: string;
  workspaceId?: number;
  workspaceName?: string;
  folderId?: number;
  folderName?: string;
  pathSnapshot?: string;
}

export interface AuditChangesDto {
  previousValue?: string;
  newValue?: string;
  sourceLocation?: string;
  destinationLocation?: string;
  reason?: string;
  details?: string;
}

export interface AuditResultDto {
  status?: string;
  blockchainStatus?: string;
  blockchainTxHash?: string;
}

export interface AuditMetadataDto {
  ipAddress?: string;
  userAgent?: string;
  recordHash?: string;
}

export interface UnifiedAuditEventResponseDto {
  id: string;
  rawId: number;
  source: 'TELEMETRY' | 'GOVERNANCE';
  category: AuditCategory;
  action: string;
  actionDisplay: string;
  actor?: AuditActorDto;
  target?: AuditTargetDto;
  location?: AuditLocationDto;
  changes?: AuditChangesDto;
  timestamp: string;
  result?: AuditResultDto;
  metadata?: AuditMetadataDto;
}

export interface UnifiedAuditQueryCriteria {
  scopeType: AuditScopeType;
  orgCode?: string;
  workspaceId?: number;
  folderId?: number;
  documentId?: number;
  teamId?: number;
  collaborationId?: number;
  includeDescendants?: boolean;
  view?: AuditViewType;
  category?: AuditCategory;
  action?: string;
  actor?: string;
  search?: string;
  from?: string;
  to?: string;
}

export interface SpringPageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}
