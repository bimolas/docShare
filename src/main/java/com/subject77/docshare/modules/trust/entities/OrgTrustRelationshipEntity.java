package com.subject77.docshare.modules.trust.entities;

import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.user.entities.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "org_trust_relationships")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrgTrustRelationshipEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_org_code", referencedColumnName = "org_code", nullable = false)
    private OrganizationEntity sourceOrganization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_org_code", referencedColumnName = "org_code", nullable = false)
    private OrganizationEntity targetOrganization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TrustStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id", referencedColumnName = "id", nullable = false)
    private UserEntity requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id", referencedColumnName = "id")
    private UserEntity approvedBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant expiresAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isActive() {
        return status == TrustStatus.ACTIVE && (expiresAt == null || expiresAt.isAfter(Instant.now()));
    }
}
