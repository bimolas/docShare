package com.subject77.docshare.modules.organization.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

import com.subject77.docshare.modules.user.entities.UserEntity;

@Entity
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationEntity {

    @Id
    @Column(name = "org_code")
    private String orgCode;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", referencedColumnName = "id", nullable = false)
    private UserEntity createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null)
            createdAt = Instant.now();
    }
}
