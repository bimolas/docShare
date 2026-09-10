package com.subject77.docshare.modules.document.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.permission.entities.FileRequestEntity;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileHash;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private String s3Bucket;

    @Column(nullable = false, unique = true)
    private String objectKey;

    @Column(nullable = false)
    private Long fileSize;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private FolderEntity folder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_request_id")
    private FileRequestEntity fileRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_org_code", referencedColumnName = "org_code", nullable = true)
    private OrganizationEntity ownerOrg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_user_id", referencedColumnName = "id", nullable = false)
    private UserEntity uploadedBy;

    @Column(nullable = false, updatable = false)
    private Instant uploadedAt;

    @Version
    private Integer version;

    private Instant lastModifiedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_modified_by_user_id", referencedColumnName = "id")
    private UserEntity lastModifiedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_modified_by_org_code", referencedColumnName = "org_code")
    private OrganizationEntity lastModifiedByOrg;

    @Builder.Default
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DocumentVersionEntity> versions = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_version_id")
    private DocumentVersionEntity currentVersion;

    @PrePersist
    protected void onUpload() {
        if (uploadedAt == null)
            uploadedAt = Instant.now();
        if (status == null)
            status = DocumentStatus.PENDING_UPLOAD;
    }
}
