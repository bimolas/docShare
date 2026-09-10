export type NotificationStatus = 'UNREAD' | 'READ' | 'ARCHIVED';

export type NotificationType =
  | 'PERMISSION_GRANTED'
  | 'PERMISSION_UPDATED'
  | 'PERMISSION_REVOKED'
  | 'PERMISSION_NOTE_ADDED'
  | 'COLLABORATION_INVITE'
  | 'COLLABORATION_ACCEPTED'
  | 'COLLABORATION_REJECTED'
  | 'COLLABORATION_EXPIRED'
  | 'COLLABORATION_REVOKED'
  | 'CHANNEL_INVITE'
  | 'CHANNEL_MENTION'
  | 'CHANNEL_THREAD_REPLY'
  | 'DM_RECEIVED'
  | 'TEAM_ADDED'
  | 'TEAM_REMOVED'
  | 'TEAM_MANAGER_ASSIGNED'
  | 'ORG_INVITATION_SENT'
  | 'ORG_INVITATION_ACCEPTED'
  | 'ORG_INVITATION_DECLINED'
  | 'ORG_INVITATION_REVOKED'
  | 'ONBOARDING_COMPLETED'
  | 'ORG_MEMBER_ROLE_UPDATED'
  | 'ORG_MEMBER_SUSPENDED'
  | 'ORG_MEMBER_REACTIVATED'
  | 'ORG_MEMBER_REMOVED'
  | 'ACCESS_REQUEST_SUBMITTED'
  | 'ACCESS_REQUEST_APPROVED'
  | 'ACCESS_REQUEST_DENIED'
  | 'ACCESS_REQUEST_CANCELLED'
  | 'WORKSPACE_MANAGER_ASSIGNED'
  | 'WORKSPACE_MANAGER_REMOVED'
  | 'FILE_REQUEST_CREATED'
  | 'FILE_REQUEST_UPLOADED'
  | 'FILE_REQUEST_FULFILLED'
  | 'FILE_REQUEST_CANCELLED'
  | 'DOCUMENT_VERSION_RESTORED';

export type NotificationCategory =
  | 'SECURITY'
  | 'SHARING'
  | 'ACCESS_REQUEST'
  | 'FILE_REQUEST'
  | 'INVITATION'
  | 'GOVERNANCE'
  | 'TEAM'
  | 'WORKSPACE'
  | 'COLLABORATION'
  | 'DOCUMENT'
  | 'SYSTEM';

export type NotificationPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';

export type NotificationSourceType =
  | 'PERMISSION'
  | 'COLLABORATION'
  | 'CHANNEL'
  | 'MESSAGE'
  | 'DM'
  | 'TEAM'
  | 'SYSTEM'
  | 'ORGANIZATION'
  | 'ACCESS_REQUEST'
  | 'FILE_REQUEST'
  | 'DOCUMENT';

export interface NotificationResponseDto {
  id: number;
  recipientId: string;
  actorId?: string;
  actorName?: string;
  type: NotificationType;
  category: NotificationCategory;
  priority: NotificationPriority;
  title: string;
  body: string;
  status: NotificationStatus;
  sourceType: NotificationSourceType;
  sourceId?: string;
  createdAt: string;
  readAt?: string;
  archivedAt?: string;
  actionPending?: boolean;
}

export interface UnreadCountResponseDto {
  unreadCount: number;
}

export interface NotificationQueryParams {
  status?: NotificationStatus;
  sourceType?: NotificationSourceType;
  category?: NotificationCategory;
  page?: number;
  size?: number;
  sort?: string;
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
