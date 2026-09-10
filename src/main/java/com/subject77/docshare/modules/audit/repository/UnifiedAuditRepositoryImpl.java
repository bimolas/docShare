package com.subject77.docshare.modules.audit.repository;

import com.subject77.docshare.modules.audit.dto.AuditProvenanceDto;
import com.subject77.docshare.modules.audit.dto.UnifiedAuditEventResponseDto;
import com.subject77.docshare.modules.audit.dto.UnifiedAuditQueryCriteria;
import com.subject77.docshare.modules.audit.entities.Action;
import com.subject77.docshare.modules.audit.entities.AuditEventType;
import com.subject77.docshare.modules.audit.enums.AuditCategory;
import com.subject77.docshare.modules.audit.enums.AuditViewType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class UnifiedAuditRepositoryImpl implements UnifiedAuditRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public Page<UnifiedAuditEventResponseDto> queryUnifiedAuditEvents(
            UnifiedAuditQueryCriteria criteria,
            List<Long> folderDescendantIds,
            String currentUserId,
            boolean canViewSensitiveMetadata,
            Pageable pageable) {

        List<String> accessWhere = new ArrayList<>();
        List<String> permWhere = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (currentUserId != null) {
            params.addValue("currentUserId", currentUserId);
        }

        // 1. Storage-specific Scope Predicates
        buildScopePredicates(criteria, folderDescendantIds, accessWhere, permWhere, params);

        // 2. Narrowing Filters
        buildFilterPredicates(criteria, accessWhere, permWhere, params);

        String accessWhereClause = accessWhere.isEmpty() ? "1 = 1" : String.join(" AND ", accessWhere);
        String permWhereClause = permWhere.isEmpty() ? "1 = 1" : String.join(" AND ", permWhere);

        // 3. Exact Count Query
        String countSql = """
            SELECT (
                (SELECT COUNT(*) FROM access_logs a WHERE %s)
                +
                (SELECT COUNT(*) FROM permission_audit_logs p WHERE %s)
            ) AS total_count
        """.formatted(accessWhereClause, permWhereClause);

        Long totalCount = jdbcTemplate.queryForObject(countSql, params, Long.class);
        if (totalCount == null || totalCount == 0L) {
            return new PageImpl<>(List.of(), pageable, 0L);
        }

        // 4. Data Query with Database-Level UNION ALL
        int limit = pageable.isPaged() ? Math.min(pageable.getPageSize(), 100) : 20;
        long offset = pageable.isPaged() ? pageable.getOffset() : 0L;
        params.addValue("limit", limit);
        params.addValue("offset", offset);

        String dataSql = """
            SELECT * FROM (
                SELECT
                    a.id AS raw_id,
                    'TELEMETRY' AS source,
                    a.timestamp AS timestamp,
                    CAST(a.action AS VARCHAR) AS action,
                    a.actor_user_id AS actor_user_id,
                    a.accessor_email AS actor_email,
                    a.document_id AS document_id,
                    a.document_file_hash AS file_hash,
                    a.folder_id AS folder_id,
                    a.workspace_id AS workspace_id,
                    a.org_code AS org_code,
                    a.resource_type AS resource_type,
                    a.resource_id AS resource_id,
                    a.group_id AS group_id,
                    a.collaboration_id AS collaboration_id,
                    CAST(NULL AS VARCHAR) AS space_type,
                    CAST(NULL AS VARCHAR) AS previous_value,
                    CAST(NULL AS VARCHAR) AS new_value,
                    CAST(NULL AS VARCHAR) AS reason,
                    a.details AS details,
                    a.tx_hash AS blockchain_tx_hash,
                    CAST(NULL AS VARCHAR) AS blockchain_status,
                    a.ip_address AS ip_address,
                    a.user_agent AS user_agent,
                    CAST(NULL AS VARCHAR) AS record_hash
                FROM access_logs a
                WHERE %s

                UNION ALL

                SELECT
                    p.id AS raw_id,
                    'GOVERNANCE' AS source,
                    p.timestamp AS timestamp,
                    p.action AS action,
                    p.actor_user_id AS actor_user_id,
                    p.granted_to_email AS actor_email,
                    CASE WHEN p.target_type = 'DOCUMENT' THEN p.target_id ELSE NULL END AS document_id,
                    p.target_file_hash AS file_hash,
                    p.folder_id AS folder_id,
                    p.workspace_id AS workspace_id,
                    p.org_code AS org_code,
                    p.target_type AS resource_type,
                    p.target_id AS resource_id,
                    p.group_id AS group_id,
                    p.collaboration_id AS collaboration_id,
                    CAST(p.space_type AS VARCHAR) AS space_type,
                    p.previous_value AS previous_value,
                    p.new_value AS new_value,
                    p.reason AS reason,
                    p.details AS details,
                    p.blockchain_tx_hash AS blockchain_tx_hash,
                    CAST(p.blockchain_status AS VARCHAR) AS blockchain_status,
                    CAST(NULL AS VARCHAR) AS ip_address,
                    CAST(NULL AS VARCHAR) AS user_agent,
                    p.record_hash AS record_hash
                FROM permission_audit_logs p
                WHERE %s
            ) unified_events
            ORDER BY timestamp DESC, source DESC, raw_id DESC
            LIMIT :limit OFFSET :offset
        """.formatted(accessWhereClause, permWhereClause);

        List<UnifiedAuditEventResponseDto> results = jdbcTemplate.query(
                dataSql,
                params,
                (rs, rowNum) -> mapRowToDto(rs, canViewSensitiveMetadata)
        );

        return new PageImpl<>(results, pageable, totalCount);
    }

    private void buildScopePredicates(
            UnifiedAuditQueryCriteria criteria,
            List<Long> folderDescendantIds,
            List<String> accessWhere,
            List<String> permWhere,
            MapSqlParameterSource params) {

        switch (criteria.getScopeType()) {
            case PERSONAL -> {
                // access_logs: org_code IS NULL, workspace_id IS NULL, group_id IS NULL, collaboration_id IS NULL, and resource owned by currentUserId
                accessWhere.add("""
                    a.org_code IS NULL AND a.workspace_id IS NULL AND a.group_id IS NULL AND a.collaboration_id IS NULL AND (
                        a.document_id IN (SELECT d.id FROM documents d WHERE d.owner_org_code IS NULL AND d.uploaded_by_user_id = :currentUserId)
                        OR a.folder_id IN (SELECT f.id FROM folders f WHERE f.owner_org_code IS NULL AND f.created_by_user_id = :currentUserId)
                        OR (a.resource_type = 'DOCUMENT' AND a.resource_id IN (SELECT d.id FROM documents d WHERE d.owner_org_code IS NULL AND d.uploaded_by_user_id = :currentUserId))
                        OR (a.resource_type = 'FOLDER' AND a.resource_id IN (SELECT f.id FROM folders f WHERE f.owner_org_code IS NULL AND f.created_by_user_id = :currentUserId))
                    )
                """);

                // permission_audit_logs: space_type = PERSONAL or (space_type IS NULL and org_code IS NULL), group_id IS NULL, collaboration_id IS NULL, workspace_id IS NULL
                permWhere.add("""
                    (p.space_type = 'PERSONAL' OR (p.space_type IS NULL AND p.org_code IS NULL))
                    AND p.workspace_id IS NULL AND p.group_id IS NULL AND p.collaboration_id IS NULL AND (
                        p.granted_by_user_id = :currentUserId
                        OR (p.target_type = 'DOCUMENT' AND p.target_id IN (SELECT d.id FROM documents d WHERE d.owner_org_code IS NULL AND d.uploaded_by_user_id = :currentUserId))
                        OR (p.target_type = 'FOLDER' AND p.target_id IN (SELECT f.id FROM folders f WHERE f.owner_org_code IS NULL AND f.created_by_user_id = :currentUserId))
                    )
                """);
            }
            case ORGANIZATION -> {
                params.addValue("orgCode", criteria.getOrgCode());
                accessWhere.add("a.org_code = :orgCode");
                permWhere.add("p.org_code = :orgCode");
            }
            case WORKSPACE -> {
                params.addValue("workspaceId", criteria.getWorkspaceId());
                accessWhere.add("a.workspace_id = :workspaceId");
                permWhere.add("p.workspace_id = :workspaceId");
            }
            case FOLDER -> {
                params.addValue("folderId", criteria.getFolderId());
                if (folderDescendantIds != null && !folderDescendantIds.isEmpty()) {
                    params.addValue("folderDescendantIds", folderDescendantIds);
                    accessWhere.add("""
                        (a.folder_id IN (:folderDescendantIds)
                         OR (a.resource_type = 'FOLDER' AND a.resource_id IN (:folderDescendantIds))
                         OR a.document_id IN (SELECT d.id FROM documents d WHERE d.folder_id IN (:folderDescendantIds))
                         OR (a.resource_type = 'DOCUMENT' AND a.resource_id IN (SELECT d.id FROM documents d WHERE d.folder_id IN (:folderDescendantIds))))
                    """);
                    permWhere.add("""
                        (p.folder_id IN (:folderDescendantIds)
                         OR (p.target_type = 'FOLDER' AND p.target_id IN (:folderDescendantIds))
                         OR (p.target_type = 'DOCUMENT' AND p.target_id IN (SELECT d.id FROM documents d WHERE d.folder_id IN (:folderDescendantIds))))
                    """);
                } else {
                    accessWhere.add("(a.folder_id = :folderId OR (a.resource_type = 'FOLDER' AND a.resource_id = :folderId))");
                    permWhere.add("(p.folder_id = :folderId OR (p.target_type = 'FOLDER' AND p.target_id = :folderId))");
                }
            }
            case DOCUMENT -> {
                params.addValue("documentId", criteria.getDocumentId());
                // Storage-specific document predicates: access_logs uses document_id / resource_id
                accessWhere.add("(a.document_id = :documentId OR (a.resource_type = 'DOCUMENT' AND a.resource_id = :documentId))");
                // permission_audit_logs uses target_type = 'DOCUMENT' AND target_id = :documentId
                permWhere.add("(p.target_type = 'DOCUMENT' AND p.target_id = :documentId)");
            }
            case TEAM -> {
                params.addValue("teamId", criteria.getTeamId());
                // access_logs does not hold team governance events; exclude telemetry
                accessWhere.add("1 = 0");
                // permission_audit_logs strictly bound to canonical team governance actions
                permWhere.add("""
                    (p.group_id = :teamId OR (p.target_type = 'GROUP' AND p.target_id = :teamId))
                    AND p.action IN (
                        'CREATE_TEAM', 'UPDATE_GROUP', 'ARCHIVE_GROUP', 'RESTORE_GROUP',
                        'ADD_MEMBER', 'REMOVE_MEMBER', 'TRANSFER_MANAGER',
                        'TEAM_WORKSPACE_ALLOCATED', 'TEAM_DEALLOCATION',
                        'MEMBER_ACCESS_GRANTED', 'MEMBER_ACCESS_UPDATED', 'MEMBER_ACCESS_REVOKED',
                        'TEAM_CREATE', 'TEAM_RENAME', 'TEAM_ARCHIVE', 'TEAM_RESTORE',
                        'TEAM_MEMBER_ADD', 'TEAM_MEMBER_REMOVE', 'TEAM_MANAGER_TRANSFER',
                        'TEAM_ALLOCATE_WORKSPACE', 'TEAM_DEALLOCATE',
                        'TEAM_DELEGATION_GRANT', 'TEAM_DELEGATION_UPDATE', 'TEAM_DELEGATION_REVOKE'
                    )
                """);
            }
            case COLLABORATION -> {
                params.addValue("collaborationId", criteria.getCollaborationId());
                accessWhere.add("(a.collaboration_id = :collaborationId OR (a.resource_type = 'COLLABORATION' AND a.resource_id = :collaborationId))");
                permWhere.add("(p.collaboration_id = :collaborationId OR (p.target_type = 'COLLABORATION' AND p.target_id = :collaborationId))");
            }
        }
    }

    private void buildFilterPredicates(
            UnifiedAuditQueryCriteria criteria,
            List<String> accessWhere,
            List<String> permWhere,
            MapSqlParameterSource params) {

        // View filter
        if (criteria.getView() == AuditViewType.CONTENT) {
            permWhere.add("1 = 0");
        } else if (criteria.getView() == AuditViewType.GOVERNANCE) {
            accessWhere.add("1 = 0");
        }

        // Category filter
        if (criteria.getCategory() != null) {
            applyCategoryFilter(criteria.getCategory(), accessWhere, permWhere);
        }

        // Action filter
        if (criteria.getAction() != null && !criteria.getAction().isBlank()) {
            params.addValue("filterAction", criteria.getAction().trim().toUpperCase());
            accessWhere.add("CAST(a.action AS VARCHAR) = :filterAction");
            permWhere.add("(p.action = :filterAction OR UPPER(p.action) = :filterAction)");
        }

        // Actor filter (user ID or email)
        if (criteria.getActor() != null && !criteria.getActor().isBlank()) {
            params.addValue("filterActor", criteria.getActor().trim());
            accessWhere.add("(a.actor_user_id = :filterActor OR LOWER(a.accessor_email) = LOWER(:filterActor))");
            permWhere.add("(p.actor_user_id = :filterActor OR LOWER(p.granted_to_email) = LOWER(:filterActor))");
        }

        // Search filter (case-insensitive details/fileHash/reason)
        if (criteria.getSearch() != null && !criteria.getSearch().isBlank()) {
            params.addValue("searchPattern", "%" + criteria.getSearch().trim().toLowerCase() + "%");
            accessWhere.add("(LOWER(a.details) LIKE :searchPattern OR LOWER(a.document_file_hash) LIKE :searchPattern)");
            permWhere.add("(LOWER(p.details) LIKE :searchPattern OR LOWER(p.target_file_hash) LIKE :searchPattern OR LOWER(p.reason) LIKE :searchPattern)");
        }

        // Date range filters
        if (criteria.getFrom() != null) {
            params.addValue("fromTimestamp", Timestamp.from(criteria.getFrom()));
            accessWhere.add("a.timestamp >= :fromTimestamp");
            permWhere.add("p.timestamp >= :fromTimestamp");
        }
        if (criteria.getTo() != null) {
            params.addValue("toTimestamp", Timestamp.from(criteria.getTo()));
            accessWhere.add("a.timestamp <= :toTimestamp");
            permWhere.add("p.timestamp <= :toTimestamp");
        }
    }

    private void applyCategoryFilter(
            AuditCategory category,
            List<String> accessWhere,
            List<String> permWhere) {

        switch (category) {
            case CONTENT -> {
                permWhere.add("""
                    p.target_type IN ('DOCUMENT', 'FOLDER') AND p.action IN (
                        'MOVE_DOCUMENT', 'MOVE_FOLDER', 'RENAME_METADATA',
                        'DOCUMENT_RENAME', 'DOCUMENT_MOVE', 'FOLDER_RENAME', 'FOLDER_MOVE'
                    )
                """);
            }
            case ACCESS -> {
                accessWhere.add("CAST(a.action AS VARCHAR) LIKE 'FILE_REQUEST_%'");
                permWhere.add("""
                    (p.action LIKE 'PERMISSION_%' OR p.action IN (
                        'GRANTED_USER', 'GRANTED_ORG', 'UPDATE_PERMISSION', 'REVOKED', 'EXPIRED_PERMISSION',
                        'ACCESS_REQUEST_APPROVED', 'ACCESS_REQUEST_DENIED', 'ACCESS_REQUEST_SUBMITTED',
                        'ACCESS_REQUEST_CREATED', 'ACCESS_REQUEST_CANCELLED'
                    ))
                """);
            }
            case SHARING -> {
                accessWhere.add("1 = 0");
                permWhere.add("""
                    (p.action LIKE 'SHARE_LINK_%' OR p.action IN (
                        'CREATED_SHARE_LINK', 'REVOKED_SHARE_LINK', 'SHARE_LINK_ACCESSED', 'SHARE_LINK_EXPIRED',
                        'GRANTED_ORG', 'ORG_SHARE_GRANT', 'ORG_SHARE_REVOKE'
                    ))
                """);
            }
            case TEAM -> {
                accessWhere.add("1 = 0");
                permWhere.add("""
                    (p.action LIKE 'TEAM_%' OR p.action IN (
                        'CREATE_TEAM', 'UPDATE_GROUP', 'ARCHIVE_GROUP', 'RESTORE_GROUP', 'TRANSFER_MANAGER',
                        'MEMBER_ACCESS_GRANTED', 'MEMBER_ACCESS_UPDATED', 'MEMBER_ACCESS_REVOKED',
                        'TEAM_WORKSPACE_ALLOCATED', 'TEAM_DEALLOCATION', 'ADD_MEMBER', 'REMOVE_MEMBER'
                    ))
                """);
            }
            case WORKSPACE -> {
                accessWhere.add("1 = 0");
                permWhere.add("p.action LIKE 'WORKSPACE_%'");
            }
            case ORGANIZATION -> {
                accessWhere.add("1 = 0");
                permWhere.add("""
                    (p.action LIKE 'ORG_%' OR p.action IN (
                        'CREATE_ORGANIZATION', 'INVITE_MEMBER', 'ACCEPT_INVITATION', 'DECLINE_INVITATION',
                        'REVOKE_INVITATION', 'UPDATE_MEMBER_ROLE', 'SUSPEND_MEMBER', 'REACTIVATE_MEMBER',
                        'REMOVE_MEMBER', 'TRANSFER_ORG_OWNERSHIP', 'ORG_TRUST_ESTABLISHED', 'ORG_TRUST_REVOKED'
                    ))
                """);
            }
            case COLLABORATION -> {
                accessWhere.add("a.collaboration_id IS NOT NULL");
                permWhere.add("(p.collaboration_id IS NOT NULL OR p.action LIKE 'COLLAB_%' OR p.action LIKE 'CHANNEL_%')");
            }
            case SECURITY -> {
                accessWhere.add("1 = 0");
                permWhere.add("(p.action LIKE 'POLICY_%' OR p.action = 'TAMPER_ALERT' OR p.action LIKE 'BLOCKCHAIN_%')");
            }
        }
    }

    private UnifiedAuditEventResponseDto mapRowToDto(ResultSet rs, boolean canViewSensitiveMetadata) throws SQLException {
        long rawId = rs.getLong("raw_id");
        String source = rs.getString("source");
        String rawAction = rs.getString("action");
        String resourceType = rs.getString("resource_type");
        Timestamp timestamp = rs.getTimestamp("timestamp");

        // 1. Resolve canonical action and category
        AuditEventType eventType;
        if ("TELEMETRY".equals(source)) {
            Action actionEnum = null;
            try {
                actionEnum = Action.valueOf(rawAction);
            } catch (Exception ignored) {
            }
            eventType = AuditEventType.fromAction(actionEnum, resourceType);
        } else {
            eventType = AuditEventType.fromRaw(rawAction, resourceType);
        }

        // 2. Synthetic ID
        String syntheticId = ("TELEMETRY".equals(source) ? "TEL-" : "GOV-") + rawId;

        // 3. Actor
        String actorUserId = rs.getString("actor_user_id");
        String actorEmail = rs.getString("actor_email");
        UnifiedAuditEventResponseDto.AuditActorDto actor = UnifiedAuditEventResponseDto.AuditActorDto.builder()
                .userId(actorUserId)
                .email(actorEmail != null ? actorEmail : "SYSTEM")
                .build();

        // 4. Target
        Long resourceId = rs.getObject("resource_id") != null ? rs.getLong("resource_id") : null;
        Long docId = rs.getObject("document_id") != null ? rs.getLong("document_id") : null;
        Long folderId = rs.getObject("folder_id") != null ? rs.getLong("folder_id") : null;
        Long targetId = resourceId != null ? resourceId : (docId != null ? docId : folderId);

        String targetType = resourceType;
        if (targetType == null) {
            if (docId != null) targetType = "DOCUMENT";
            else if (folderId != null) targetType = "FOLDER";
            else if (rs.getObject("group_id") != null) targetType = "TEAM";
            else if (rs.getObject("workspace_id") != null) targetType = "WORKSPACE";
            else if (rs.getObject("collaboration_id") != null) targetType = "COLLABORATION";
        }

        String rawDetails = rs.getString("details");
        String newVal = rs.getString("new_value");
        String resourceName = extractResourceNameFromDetails(rawDetails);

        if (resourceName == null && newVal != null && !newVal.isBlank() && ("GROUP".equalsIgnoreCase(targetType) || "TEAM".equalsIgnoreCase(targetType))) {
            resourceName = newVal;
        }

        if (resourceName == null && ("ORGANIZATION".equalsIgnoreCase(targetType) || "WORKSPACE".equalsIgnoreCase(targetType)) && (eventType == AuditEventType.POLICY_UPDATE || eventType == AuditEventType.POLICY_CREATE)) {
            resourceName = "Access Policy";
        }

        UnifiedAuditEventResponseDto.AuditTargetDto target = UnifiedAuditEventResponseDto.AuditTargetDto.builder()
                .id(targetId)
                .type(targetType)
                .fileHash(rs.getString("file_hash"))
                .name(resourceName)
                .build();

        // 5. Location
        String orgCode = rs.getString("org_code");
        String spaceType = rs.getString("space_type");
        if (spaceType == null) {
            spaceType = orgCode != null ? "ORGANIZATION" : "PERSONAL";
        }

        Long wsId = rs.getObject("workspace_id") != null ? rs.getLong("workspace_id") : null;
        UnifiedAuditEventResponseDto.AuditLocationDto location = UnifiedAuditEventResponseDto.AuditLocationDto.builder()
                .spaceType(spaceType)
                .orgCode(orgCode)
                .workspaceId(wsId)
                .folderId(folderId)
                .build();

        // 6. Changes
        String prevVal = rs.getString("previous_value");
        String reason = rs.getString("reason");

        UnifiedAuditEventResponseDto.AuditChangesDto changes = null;
        if (prevVal != null || newVal != null || reason != null || rawDetails != null) {
            changes = UnifiedAuditEventResponseDto.AuditChangesDto.builder()
                    .previousValue(prevVal)
                    .newValue(newVal)
                    .reason(reason)
                    .details(sanitizeDetails(rawDetails))
                    .build();
        }

        // 7. Result
        UnifiedAuditEventResponseDto.AuditResultDto result = UnifiedAuditEventResponseDto.AuditResultDto.builder()
                .status("SUCCESS")
                .blockchainStatus(rs.getString("blockchain_status"))
                .blockchainTxHash(rs.getString("blockchain_tx_hash"))
                .build();

        // 8. Technical Metadata (Privileged)
        UnifiedAuditEventResponseDto.AuditMetadataDto metadata = UnifiedAuditEventResponseDto.AuditMetadataDto.builder()
                .ipAddress(canViewSensitiveMetadata ? rs.getString("ip_address") : null)
                .userAgent(canViewSensitiveMetadata ? rs.getString("user_agent") : null)
                .recordHash(rs.getString("record_hash"))
                .build();

        return UnifiedAuditEventResponseDto.builder()
                .id(syntheticId)
                .rawId(rawId)
                .source(source)
                .category(eventType.getCategory())
                .action(eventType.name())
                .actionDisplay(eventType.getDisplayAction())
                .actor(actor)
                .target(target)
                .location(location)
                .changes(changes)
                .timestamp(timestamp != null ? timestamp.toInstant() : Instant.now())
                .result(result)
                .metadata(metadata)
                .build();
    }

    @Override
    public Optional<AuditProvenanceDto> findDeletedResourceProvenance(String resourceType, Long resourceId) {
        if (resourceType == null || resourceId == null) {
            return Optional.empty();
        }
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("resourceId", resourceId);

        if ("DOCUMENT".equalsIgnoreCase(resourceType)) {
            String sql = """
                SELECT org_code, workspace_id, actor_user_id, 'DOCUMENT' as res_type
                FROM (
                    SELECT a.org_code, a.workspace_id, a.actor_user_id, a.timestamp
                    FROM access_logs a
                    WHERE a.document_id = :resourceId OR (a.resource_type = 'DOCUMENT' AND a.resource_id = :resourceId)
                    UNION ALL
                    SELECT p.org_code, p.workspace_id, p.actor_user_id, p.timestamp
                    FROM permission_audit_logs p
                    WHERE p.target_type = 'DOCUMENT' AND p.target_id = :resourceId
                ) prov
                ORDER BY timestamp DESC
                LIMIT 1
            """;
            List<AuditProvenanceDto> list = jdbcTemplate.query(sql, params, (rs, rowNum) -> AuditProvenanceDto.builder()
                    .resourceType("DOCUMENT")
                    .resourceId(resourceId)
                    .orgCode(rs.getString("org_code"))
                    .workspaceId(rs.getObject("workspace_id") != null ? rs.getLong("workspace_id") : null)
                    .spaceType(rs.getString("org_code") != null ? "ORGANIZATION" : "PERSONAL")
                    .actorUserId(rs.getString("actor_user_id"))
                    .build());
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        } else if ("FOLDER".equalsIgnoreCase(resourceType)) {
            String sql = """
                SELECT org_code, workspace_id, actor_user_id, 'FOLDER' as res_type
                FROM (
                    SELECT a.org_code, a.workspace_id, a.actor_user_id, a.timestamp
                    FROM access_logs a
                    WHERE a.folder_id = :resourceId OR (a.resource_type = 'FOLDER' AND a.resource_id = :resourceId)
                    UNION ALL
                    SELECT p.org_code, p.workspace_id, p.actor_user_id, p.timestamp
                    FROM permission_audit_logs p
                    WHERE p.target_type = 'FOLDER' AND p.target_id = :resourceId
                ) prov
                ORDER BY timestamp DESC
                LIMIT 1
            """;
            List<AuditProvenanceDto> list = jdbcTemplate.query(sql, params, (rs, rowNum) -> AuditProvenanceDto.builder()
                    .resourceType("FOLDER")
                    .resourceId(resourceId)
                    .orgCode(rs.getString("org_code"))
                    .workspaceId(rs.getObject("workspace_id") != null ? rs.getLong("workspace_id") : null)
                    .spaceType(rs.getString("org_code") != null ? "ORGANIZATION" : "PERSONAL")
                    .actorUserId(rs.getString("actor_user_id"))
                    .build());
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        } else if ("WORKSPACE".equalsIgnoreCase(resourceType)) {
            String sql = """
                SELECT org_code, workspace_id, actor_user_id, 'WORKSPACE' as res_type
                FROM (
                    SELECT a.org_code, a.workspace_id, a.actor_user_id, a.timestamp
                    FROM access_logs a
                    WHERE a.workspace_id = :resourceId
                    UNION ALL
                    SELECT p.org_code, p.workspace_id, p.actor_user_id, p.timestamp
                    FROM permission_audit_logs p
                    WHERE p.workspace_id = :resourceId OR (p.target_type = 'WORKSPACE' AND p.target_id = :resourceId)
                ) prov
                ORDER BY timestamp DESC
                LIMIT 1
            """;
            List<AuditProvenanceDto> list = jdbcTemplate.query(sql, params, (rs, rowNum) -> AuditProvenanceDto.builder()
                    .resourceType("WORKSPACE")
                    .resourceId(resourceId)
                    .orgCode(rs.getString("org_code"))
                    .workspaceId(resourceId)
                    .spaceType("ORGANIZATION")
                    .actorUserId(rs.getString("actor_user_id"))
                    .build());
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        }
        return Optional.empty();
    }

    private String extractResourceNameFromDetails(String details) {
        if (details == null || details.isBlank()) {
            return null;
        }
        // Match "Created team '<name>' with initial manager..."
        if (details.startsWith("Created team '")) {
            int endIdx = details.indexOf("'", 14);
            if (endIdx > 14) {
                return details.substring(14, endIdx);
            }
        }
        // Match "Deleted folder: <name>" or "Deleted document: <name>" or "Uploaded document: <name>"
        String lower = details.toLowerCase();
        int idx = lower.indexOf(": ");
        if (idx != -1 && idx + 2 < details.length()) {
            String candidate = details.substring(idx + 2).trim();
            // Don't treat configuration key=value pairs (like allowAccessRequests=true) or JSON as resource names
            if (!candidate.isBlank() && !candidate.contains("=") && !candidate.startsWith("{") && candidate.length() < 255) {
                return candidate;
            }
        }
        return null;
    }

    private String sanitizeDetails(String details) {
        if (details == null || details.isBlank()) {
            return details;
        }
        return details.replaceAll("(?i)(shareLinkToken|token|password|secret|bearer)\\s*[:=]\\s*[^,;\\s}\\]]+", "$1=[REDACTED]");
    }
}
