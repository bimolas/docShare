package com.subject77.docshare.modules.audit.entities;

import com.subject77.docshare.modules.audit.enums.AuditCategory;

public enum AuditEventType {
    // Content & Document Lifecycle
    DOCUMENT_UPLOAD("Uploaded document", AuditCategory.CONTENT),
    DOCUMENT_VIEW("Viewed document", AuditCategory.CONTENT),
    DOCUMENT_VIEW_METADATA("Viewed document metadata", AuditCategory.CONTENT),
    DOCUMENT_PREVIEW("Previewed document", AuditCategory.CONTENT),
    DOCUMENT_DOWNLOAD("Downloaded document", AuditCategory.CONTENT),
    DOCUMENT_RENAME("Renamed document", AuditCategory.CONTENT),
    DOCUMENT_MOVE("Moved document", AuditCategory.CONTENT),
    DOCUMENT_DELETE("Deleted document", AuditCategory.CONTENT),
    DOCUMENT_UPDATE("Updated document", AuditCategory.CONTENT),
    DOCUMENT_INITIATE_UPLOAD("Initiated document upload", AuditCategory.CONTENT),
    VERSION_CREATED("Created new version", AuditCategory.CONTENT),
    VERSION_NEW("Uploaded new version", AuditCategory.CONTENT),
    VERSION_RESTORED("Restored document version", AuditCategory.CONTENT),
    VERSION_RESTORE("Restored document version", AuditCategory.CONTENT),
    VERSION_INITIATE("Initiated new version upload", AuditCategory.CONTENT),

    // Folder Lifecycle
    FOLDER_CREATE("Created folder", AuditCategory.CONTENT),
    FOLDER_RENAME("Renamed folder", AuditCategory.CONTENT),
    FOLDER_MOVE("Moved folder", AuditCategory.CONTENT),
    FOLDER_DELETE("Deleted folder", AuditCategory.CONTENT),
    FOLDER_VIEW_METADATA("Viewed folder metadata", AuditCategory.CONTENT),

    // Permission & Access Lifecycle
    PERMISSION_GRANT("Granted permission", AuditCategory.ACCESS),
    PERMISSION_GRANTED("Granted permission", AuditCategory.ACCESS),
    PERMISSION_UPDATE("Updated permission", AuditCategory.ACCESS),
    PERMISSION_UPDATED("Updated permission", AuditCategory.ACCESS),
    PERMISSION_REVOKE("Revoked permission", AuditCategory.ACCESS),
    PERMISSION_REVOKED("Revoked permission", AuditCategory.ACCESS),
    PERMISSION_EXPIRE("Permission expired", AuditCategory.ACCESS),
    ACCESS_REQUEST_CREATED("Created access request", AuditCategory.ACCESS),
    ACCESS_REQUEST_SUBMITTED("Submitted access request", AuditCategory.ACCESS),
    ACCESS_REQUEST_SUBMIT("Submitted access request", AuditCategory.ACCESS),
    ACCESS_REQUEST_APPROVED("Approved access request", AuditCategory.ACCESS),
    ACCESS_REQUEST_APPROVE("Approved access request", AuditCategory.ACCESS),
    ACCESS_REQUEST_DENIED("Denied access request", AuditCategory.ACCESS),
    ACCESS_REQUEST_DENY("Denied access request", AuditCategory.ACCESS),
    ACCESS_REQUEST_CANCELLED("Cancelled access request", AuditCategory.ACCESS),
    ACCESS_REQUEST_CANCEL("Cancelled access request", AuditCategory.ACCESS),
    FILE_REQUEST_CREATED("Created file request", AuditCategory.ACCESS),
    FILE_REQUEST_CREATE("Created file request", AuditCategory.ACCESS),
    FILE_REQUEST_UPLOADED("Uploaded requested file", AuditCategory.ACCESS),
    FILE_REQUEST_UPLOAD("Uploaded requested file", AuditCategory.ACCESS),
    FILE_REQUEST_FULFILLED("Fulfilled file request", AuditCategory.ACCESS),
    FILE_REQUEST_FULFILL("Fulfilled file request", AuditCategory.ACCESS),
    FILE_REQUEST_CANCELLED("Cancelled file request", AuditCategory.ACCESS),
    FILE_REQUEST_CANCEL("Cancelled file request", AuditCategory.ACCESS),

    // Sharing Lifecycle
    SHARE_LINK_CREATE("Created share link", AuditCategory.SHARING),
    SHARE_LINK_ACCESS("Accessed share link", AuditCategory.SHARING),
    SHARE_LINK_REVOKE("Revoked share link", AuditCategory.SHARING),
    SHARE_LINK_EXPIRE("Share link expired", AuditCategory.SHARING),
    ORG_SHARE_GRANT("Granted organization share", AuditCategory.SHARING),
    ORG_SHARE_REVOKE("Revoked organization share", AuditCategory.SHARING),

    // Team Lifecycle & Governance
    TEAM_CREATED("Created team", AuditCategory.TEAM),
    TEAM_CREATE("Created team", AuditCategory.TEAM),
    TEAM_RENAME("Renamed team", AuditCategory.TEAM),
    TEAM_ARCHIVE("Archived team", AuditCategory.TEAM),
    TEAM_RESTORE("Restored team", AuditCategory.TEAM),
    TEAM_MEMBER_ADDED("Added team member", AuditCategory.TEAM),
    TEAM_MEMBER_ADD("Added team member", AuditCategory.TEAM),
    TEAM_MEMBER_REMOVED("Removed team member", AuditCategory.TEAM),
    TEAM_MEMBER_REMOVE("Removed team member", AuditCategory.TEAM),
    TEAM_MANAGER_TRANSFERRED("Transferred team manager", AuditCategory.TEAM),
    TEAM_MANAGER_TRANSFER("Transferred team manager", AuditCategory.TEAM),
    TEAM_ALLOCATE_WORKSPACE("Allocated workspace to team", AuditCategory.TEAM),
    TEAM_DEALLOCATE("Deallocated resource from team", AuditCategory.TEAM),
    TEAM_DELEGATION_GRANT("Granted member access delegation", AuditCategory.TEAM),
    TEAM_DELEGATION_UPDATE("Updated member access delegation", AuditCategory.TEAM),
    TEAM_DELEGATION_REVOKE("Revoked member access delegation", AuditCategory.TEAM),

    // Workspace Lifecycle
    WORKSPACE_CREATED("Created workspace", AuditCategory.WORKSPACE),
    WORKSPACE_CREATE("Created workspace", AuditCategory.WORKSPACE),
    WORKSPACE_UPDATED("Updated workspace", AuditCategory.WORKSPACE),
    WORKSPACE_UPDATE("Updated workspace", AuditCategory.WORKSPACE),
    WORKSPACE_ARCHIVED("Archived workspace", AuditCategory.WORKSPACE),
    WORKSPACE_ARCHIVE("Archived workspace", AuditCategory.WORKSPACE),
    WORKSPACE_RESTORED("Restored workspace", AuditCategory.WORKSPACE),
    WORKSPACE_RESTORE("Restored workspace", AuditCategory.WORKSPACE),
    WORKSPACE_DELETED("Deleted workspace", AuditCategory.WORKSPACE),
    WORKSPACE_DELETE("Deleted workspace", AuditCategory.WORKSPACE),
    WORKSPACE_MANAGER_ASSIGNED("Assigned workspace manager", AuditCategory.WORKSPACE),
    WORKSPACE_MANAGER_ASSIGN("Assigned workspace manager", AuditCategory.WORKSPACE),
    WORKSPACE_MANAGER_REMOVED("Removed workspace manager", AuditCategory.WORKSPACE),
    WORKSPACE_MANAGER_REMOVE("Removed workspace manager", AuditCategory.WORKSPACE),

    // Organization Lifecycle
    ORG_CREATE("Created organization", AuditCategory.ORGANIZATION),
    ORG_INVITATION_SENT("Sent organization invitation", AuditCategory.ORGANIZATION),
    ORG_MEMBER_INVITE("Invited organization member", AuditCategory.ORGANIZATION),
    ORG_INVITATION_ACCEPTED("Accepted organization invitation", AuditCategory.ORGANIZATION),
    ORG_INVITATION_ACCEPT("Accepted organization invitation", AuditCategory.ORGANIZATION),
    ORG_INVITATION_DECLINED("Declined organization invitation", AuditCategory.ORGANIZATION),
    ORG_INVITATION_DECLINE("Declined organization invitation", AuditCategory.ORGANIZATION),
    ORG_INVITATION_REVOKED("Revoked organization invitation", AuditCategory.ORGANIZATION),
    ORG_INVITATION_REVOKE("Revoked organization invitation", AuditCategory.ORGANIZATION),
    ORG_MEMBER_ADDED("Added organization member", AuditCategory.ORGANIZATION),
    ORG_MEMBER_ROLE_UPDATED("Updated organization member role", AuditCategory.ORGANIZATION),
    ORG_MEMBER_ROLE_UPD("Updated organization member role", AuditCategory.ORGANIZATION),
    ORG_MEMBER_SUSPENDED("Suspended organization member", AuditCategory.ORGANIZATION),
    ORG_MEMBER_SUSPEND("Suspended organization member", AuditCategory.ORGANIZATION),
    ORG_MEMBER_REACTIVATED("Reactivated organization member", AuditCategory.ORGANIZATION),
    ORG_MEMBER_REACTIVATE("Reactivated organization member", AuditCategory.ORGANIZATION),
    ORG_MEMBER_REMOVED("Removed organization member", AuditCategory.ORGANIZATION),
    ORG_MEMBER_REMOVE("Removed organization member", AuditCategory.ORGANIZATION),
    ORG_OWNERSHIP_TRANSFER("Transferred organization ownership", AuditCategory.ORGANIZATION),
    ORG_TRUST_ESTABLISH("Established organization trust", AuditCategory.ORGANIZATION),
    ORG_TRUST_REVOKE("Revoked organization trust", AuditCategory.ORGANIZATION),

    // Collaboration Lifecycle
    COLLABORATION_INVITE("Invited to collaboration", AuditCategory.COLLABORATION),
    COLLABORATION_ACCEPTED("Accepted collaboration", AuditCategory.COLLABORATION),
    COLLABORATION_REVOKED("Revoked collaboration", AuditCategory.COLLABORATION),
    COLLAB_CREATE("Created collaboration", AuditCategory.COLLABORATION),
    COLLAB_INVITE("Invited to collaboration", AuditCategory.COLLABORATION),
    COLLAB_ACCEPT("Accepted collaboration", AuditCategory.COLLABORATION),
    COLLAB_DECLINE("Declined collaboration", AuditCategory.COLLABORATION),
    COLLAB_MEMBER_UPD("Updated collaboration member", AuditCategory.COLLABORATION),
    COLLAB_MEMBER_REMOVE("Removed collaboration member", AuditCategory.COLLABORATION),
    COLLAB_RESOURCE_ALLOCATE("Allocated collaboration resource", AuditCategory.COLLABORATION),
    COLLAB_RESOURCE_REVOKE("Revoked collaboration resource", AuditCategory.COLLABORATION),
    COLLAB_ARCHIVE("Archived collaboration", AuditCategory.COLLABORATION),
    CHANNEL_CREATE("Created collaboration channel", AuditCategory.COLLABORATION),
    CHANNEL_MESSAGE_POST("Posted collaboration message", AuditCategory.COLLABORATION),
    CHANNEL_MESSAGE_DELETE("Deleted collaboration message", AuditCategory.COLLABORATION),

    // Security & Policies
    POLICY_CREATE("Created security policy", AuditCategory.SECURITY),
    POLICY_UPDATE("Updated security policy", AuditCategory.SECURITY),
    BLOCKCHAIN_ANCHOR_SUCCESS("Anchored audit record to blockchain", AuditCategory.SECURITY),
    BLOCKCHAIN_ANCHOR_FAILED("Blockchain anchor failed", AuditCategory.SECURITY),
    SECURITY_EVENT("Security alert / Tamper event", AuditCategory.SECURITY),

    // Fallbacks
    LEGACY_CUSTOM("Custom legacy action", AuditCategory.SECURITY),
    UNKNOWN("Unknown action", AuditCategory.SECURITY);

    private final String displayAction;
    private final AuditCategory category;

    AuditEventType(String displayAction, AuditCategory category) {
        this.displayAction = displayAction;
        this.category = category;
    }

    public String getDisplayAction() {
        return displayAction;
    }

    public AuditCategory getCategory() {
        return category;
    }

    public static AuditEventType fromRaw(String raw) {
        return fromRaw(raw, null);
    }

    public static AuditEventType fromRaw(String raw, String resourceType) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        String normalized = raw.trim().toUpperCase();

        // 1. Direct enum match
        try {
            return AuditEventType.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
        }

        // 2. Exact taxonomy mapping for historical/raw actions
        // Team mappings
        if ("CREATE_TEAM".equals(normalized)) return TEAM_CREATE;
        if ("UPDATE_GROUP".equals(normalized)) return TEAM_RENAME;
        if ("ARCHIVE_GROUP".equals(normalized)) return TEAM_ARCHIVE;
        if ("RESTORE_GROUP".equals(normalized)) return TEAM_RESTORE;
        if ("TRANSFER_MANAGER".equals(normalized)) return TEAM_MANAGER_TRANSFER;
        if ("MEMBER_ACCESS_GRANTED".equals(normalized)) return TEAM_DELEGATION_GRANT;
        if ("MEMBER_ACCESS_UPDATED".equals(normalized)) return TEAM_DELEGATION_UPDATE;
        if ("MEMBER_ACCESS_REVOKED".equals(normalized)) return TEAM_DELEGATION_REVOKE;
        if ("TEAM_WORKSPACE_ALLOCATED".equals(normalized)) return TEAM_ALLOCATE_WORKSPACE;
        if ("TEAM_DEALLOCATION".equals(normalized)) return TEAM_DEALLOCATE;
        if ("ADD_MEMBER".equals(normalized) && "GROUP".equalsIgnoreCase(resourceType)) return TEAM_MEMBER_ADD;
        if ("REMOVE_MEMBER".equals(normalized) && "GROUP".equalsIgnoreCase(resourceType)) return TEAM_MEMBER_REMOVE;

        // Organization mappings
        if ("CREATE_ORGANIZATION".equals(normalized)) return ORG_CREATE;
        if ("INVITE_MEMBER".equals(normalized)) return ORG_MEMBER_INVITE;
        if ("ACCEPT_INVITATION".equals(normalized)) return ORG_INVITATION_ACCEPTED;
        if ("DECLINE_INVITATION".equals(normalized)) return ORG_INVITATION_DECLINED;
        if ("REVOKE_INVITATION".equals(normalized) || "EXPIRED_INVITATION".equals(normalized)) return ORG_INVITATION_REVOKED;
        if ("UPDATE_MEMBER_ROLE".equals(normalized)) return ORG_MEMBER_ROLE_UPDATED;
        if ("SUSPEND_MEMBER".equals(normalized)) return ORG_MEMBER_SUSPENDED;
        if ("REACTIVATE_MEMBER".equals(normalized)) return ORG_MEMBER_REACTIVATED;
        if ("REMOVE_MEMBER".equals(normalized)) return ORG_MEMBER_REMOVED;
        if ("TRANSFER_ORG_OWNERSHIP".equals(normalized)) return ORG_OWNERSHIP_TRANSFER;
        if ("ORG_TRUST_ESTABLISHED".equals(normalized)) return ORG_TRUST_ESTABLISH;
        if ("ORG_TRUST_REVOKED".equals(normalized)) return ORG_TRUST_REVOKE;

        // Workspace mappings
        if ("WORKSPACE_CREATED".equals(normalized)) return WORKSPACE_CREATED;
        if ("WORKSPACE_UPDATED".equals(normalized)) return WORKSPACE_UPDATED;
        if ("WORKSPACE_ARCHIVED".equals(normalized)) return WORKSPACE_ARCHIVED;
        if ("WORKSPACE_RESTORED".equals(normalized)) return WORKSPACE_RESTORED;
        if ("WORKSPACE_DELETED".equals(normalized)) return WORKSPACE_DELETED;
        if ("WORKSPACE_MANAGER_ASSIGNED".equals(normalized)) return WORKSPACE_MANAGER_ASSIGNED;
        if ("WORKSPACE_MANAGER_REMOVED".equals(normalized)) return WORKSPACE_MANAGER_REMOVED;

        // Permission & Sharing mappings
        if ("UPDATE_PERMISSION".equals(normalized)) return PERMISSION_UPDATED;
        if ("EXPIRED_PERMISSION".equals(normalized)) return PERMISSION_EXPIRE;
        if ("CREATED_SHARE_LINK".equals(normalized)) return SHARE_LINK_CREATE;
        if ("REVOKED_SHARE_LINK".equals(normalized)) return SHARE_LINK_REVOKE;
        if ("SHARE_LINK_ACCESSED".equals(normalized)) return SHARE_LINK_ACCESS;
        if ("SHARE_LINK_EXPIRED".equals(normalized)) return SHARE_LINK_EXPIRE;
        if ("GRANTED_USER".equals(normalized)) return PERMISSION_GRANTED;
        if ("GRANTED_ORG".equals(normalized)) return ORG_SHARE_GRANT;
        if ("REVOKED".equals(normalized)) return PERMISSION_REVOKED;
        if ("ACCESS_REQUEST_APPROVED".equals(normalized)) return ACCESS_REQUEST_APPROVED;
        if ("ACCESS_REQUEST_DENIED".equals(normalized)) return ACCESS_REQUEST_DENIED;

        // Content renames based on resource type
        if ("RENAME_METADATA".equals(normalized)) {
            if ("FOLDER".equalsIgnoreCase(resourceType)) {
                return FOLDER_RENAME;
            }
            return DOCUMENT_RENAME;
        }

        // Security & Policies
        if ("ORGANIZATION_POLICY_UPDATED".equals(normalized) || "PERSONAL_POLICY_UPDATED".equals(normalized) || "POLICY_UPDATED".equals(normalized) || "POLICY_UPDATE".equals(normalized)) return POLICY_UPDATE;
        if ("ORGANIZATION_POLICY_CREATED".equals(normalized) || "PERSONAL_POLICY_CREATED".equals(normalized) || "POLICY_CREATED".equals(normalized) || "POLICY_CREATE".equals(normalized)) return POLICY_CREATE;
        if ("TAMPER_ALERT".equals(normalized)) return SECURITY_EVENT;

        return LEGACY_CUSTOM;
    }

    public static AuditEventType fromAction(Action action, String resourceType) {
        if (action == null) {
            return UNKNOWN;
        }
        return switch (action) {
            case PREVIEW -> DOCUMENT_PREVIEW;
            case DOWNLOAD, DOWNLOAD_PRESIGNED -> DOCUMENT_DOWNLOAD;
            case UPDATE -> DOCUMENT_UPDATE;
            case DELETE, DELETE_DOCUMENT -> DOCUMENT_DELETE;
            case VIEW_METADATA -> "FOLDER".equalsIgnoreCase(resourceType) ? FOLDER_VIEW_METADATA : DOCUMENT_VIEW_METADATA;
            case INITIATE_UPLOAD -> DOCUMENT_INITIATE_UPLOAD;
            case CONFIRM_UPLOAD -> DOCUMENT_UPLOAD;
            case INITIATE_NEW_VERSION -> VERSION_INITIATE;
            case CONFIRM_NEW_VERSION -> VERSION_NEW;
            case RESTORE_VERSION -> VERSION_RESTORE;
            case RENAME_METADATA -> "FOLDER".equalsIgnoreCase(resourceType) ? FOLDER_RENAME : DOCUMENT_RENAME;
            case MOVE_FOLDER -> FOLDER_MOVE;
            case MOVE_DOCUMENT -> DOCUMENT_MOVE;
            case CREATE_FOLDER -> FOLDER_CREATE;
            case DELETE_FOLDER -> FOLDER_DELETE;
            case FILE_REQUEST_CREATED -> FILE_REQUEST_CREATE;
            case FILE_REQUEST_UPLOADED -> FILE_REQUEST_UPLOAD;
            case FILE_REQUEST_FULFILLED -> FILE_REQUEST_FULFILL;
            case FILE_REQUEST_CANCELLED -> FILE_REQUEST_CANCEL;
        };
    }
}
