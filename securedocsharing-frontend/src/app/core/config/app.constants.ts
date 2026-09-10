export const APP_CONSTANTS = {
  APP_NAME: 'SecureDocSharing',
  APP_VERSION: '1.0.0',
  DEFAULT_PAGE_SIZE: 20,
  MAX_FILE_SIZE_MB: 500,
  SUPPORTED_PREVIEW_TYPES: ['pdf', 'png', 'jpg', 'jpeg', 'gif', 'svg', 'txt', 'md'],
  PERMISSION_LEVELS: ['READ', 'WRITE', 'ADMIN'] as const,
  SPACE_TYPES: ['PERSONAL', 'ORGANIZATION'] as const,
  MEMBER_ROLES: ['OWNER', 'ADMIN', 'MEMBER'] as const,
} as const;

export type PermissionLevel = (typeof APP_CONSTANTS.PERMISSION_LEVELS)[number];
export type SpaceType = (typeof APP_CONSTANTS.SPACE_TYPES)[number];
export type MemberRole = (typeof APP_CONSTANTS.MEMBER_ROLES)[number];
