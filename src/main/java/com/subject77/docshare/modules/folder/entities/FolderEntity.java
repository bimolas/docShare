package com.subject77.docshare.modules.folder.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.List;

import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.document.entities.DocumentEntity;

@Entity
@Table(name = "folders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FolderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "subspace_type")
    private SubspaceType subspaceType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_folder_id")
    private FolderEntity parentFolder;

    @OneToMany(mappedBy = "parentFolder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FolderEntity> subFolders;

    @OneToMany(mappedBy = "folder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DocumentEntity> documents;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_org_code", referencedColumnName = "org_code", nullable = true)
    private OrganizationEntity ownerOrg;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", referencedColumnName = "id", nullable = false)
    private UserEntity createdBy;

    @Column(name = "is_default_subspace", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean isDefaultSubspace = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null)
            createdAt = Instant.now();
    }
}
