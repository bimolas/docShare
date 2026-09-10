package com.subject77.docshare.modules.user.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;

@Entity
@Table(name = "user_organizations", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "org_code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserOrganizationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_code", referencedColumnName = "org_code", nullable = false)
    private OrganizationEntity organization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MembershipStatus status = MembershipStatus.ACTIVE;

    @Column(nullable = false, updatable = false)
    private Instant joinedAt;

    @Column
    private Instant statusUpdatedAt;

    @Column
    private String updatedByUserId;

    @Column
    private String statusReason;

    @PrePersist
    protected void onJoin() {
        if (joinedAt == null) {
            joinedAt = Instant.now();
        }
        if (status == null) {
            status = MembershipStatus.ACTIVE;
        }
    }
}
