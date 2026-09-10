package com.subject77.docshare.modules.user.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import com.subject77.docshare.modules.organization.entities.OrganizationEntity;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class UserEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(length = 36)
    private String id; // Maps to Keycloak UUID sub

    @Column(unique = true, nullable = false)
    private String email;

    private String firstName;
    private String lastName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_code", referencedColumnName = "org_code")
    private OrganizationEntity organization;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null)
            createdAt = Instant.now();
    }
}
