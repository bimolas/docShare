package com.subject77.docshare.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class DatabaseConstraintInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            // PostgreSQL partial unique index on access_requests for documents
            jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uk_access_request_pending_doc 
                ON access_requests (requester_id, document_id) 
                WHERE status = 'PENDING' AND document_id IS NOT NULL;
            """);

            // PostgreSQL partial unique index on access_requests for folders
            jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uk_access_request_pending_folder 
                ON access_requests (requester_id, folder_id) 
                WHERE status = 'PENDING' AND folder_id IS NOT NULL;
            """);

            // PostgreSQL partial unique index on access_requests for document file hashes
            jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uk_access_request_pending_hash 
                ON access_requests (requester_id, document_file_hash) 
                WHERE status = 'PENDING' AND document_id IS NULL AND document_file_hash IS NOT NULL;
            """);

            // PostgreSQL symmetric unique index on org_trust_relationships
            jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uk_org_trust_symmetric_active_pending 
                ON org_trust_relationships (LEAST(source_org_code, target_org_code), GREATEST(source_org_code, target_org_code)) 
                WHERE status IN ('PENDING', 'ACTIVE');
            """);

            // Fix workspace_type CHECK constraint: drop PROJECT_WORKSPACE if still present
            try {
                jdbcTemplate.execute("""
                    DO $$
                    BEGIN
                        IF EXISTS (
                            SELECT 1 FROM pg_constraint
                            WHERE conrelid = 'workspace_policies'::regclass
                              AND conname = 'workspace_policies_workspace_type_check'
                              AND pg_get_constraintdef(oid) LIKE '%PROJECT_WORKSPACE%'
                        ) THEN
                            ALTER TABLE workspace_policies
                                DROP CONSTRAINT workspace_policies_workspace_type_check;
                            ALTER TABLE workspace_policies
                                ADD CONSTRAINT workspace_policies_workspace_type_check
                                CHECK (workspace_type IN ('PERSONAL', 'ORGANIZATION'));
                        END IF;
                    END
                    $$;
                """);
            } catch (Exception ce) {
                log.warn("Could not update workspace_type CHECK constraint: {}", ce.getMessage());
            }

            // Fix permissions_permission_type_check to include BOUNDARY
            try {
                jdbcTemplate.execute("""
                    DO $$
                    BEGIN
                        IF EXISTS (
                            SELECT 1 FROM pg_constraint
                            WHERE conrelid = 'permissions'::regclass
                              AND conname = 'permissions_permission_type_check'
                              AND pg_get_constraintdef(oid) NOT LIKE '%BOUNDARY%'
                        ) THEN
                            ALTER TABLE permissions
                                DROP CONSTRAINT permissions_permission_type_check;
                            ALTER TABLE permissions
                                ADD CONSTRAINT permissions_permission_type_check
                                CHECK (permission_type IN ('ALLOW', 'EXCLUDE', 'BOUNDARY'));
                        END IF;
                    END
                    $$;
                """);
            } catch (Exception ce) {
                log.warn("Could not update permissions_permission_type_check constraint: {}", ce.getMessage());
            }

            // Fix permissions_grantor_authority_tier_check to include TEAM_MANAGER
            try {
                jdbcTemplate.execute("""
                    DO $$
                    BEGIN
                        IF EXISTS (
                            SELECT 1 FROM pg_constraint
                            WHERE conrelid = 'permissions'::regclass
                              AND conname = 'permissions_grantor_authority_tier_check'
                              AND pg_get_constraintdef(oid) NOT LIKE '%TEAM_MANAGER%'
                        ) THEN
                            ALTER TABLE permissions
                                DROP CONSTRAINT permissions_grantor_authority_tier_check;
                            ALTER TABLE permissions
                                ADD CONSTRAINT permissions_grantor_authority_tier_check
                                CHECK (grantor_authority_tier IN ('SUPER_ADMIN', 'ORG_ADMIN', 'WORKSPACE_MANAGER', 'COLLABORATION_MANAGER', 'PERSONAL_RESOURCE_OWNER', 'ORG_RESOURCE_CREATOR', 'TEAM_MANAGER', 'PROTECTED_LEGACY'));
                        END IF;
                    END
                    $$;
                """);
            } catch (Exception ce) {
                log.warn("Could not update permissions_grantor_authority_tier_check constraint: {}", ce.getMessage());
            }

            log.info("Successfully initialized partial unique constraints and indexes");
        } catch (Exception e) {
            log.warn("Could not create database partial unique indexes: {}", e.getMessage());
        }
    }
}
