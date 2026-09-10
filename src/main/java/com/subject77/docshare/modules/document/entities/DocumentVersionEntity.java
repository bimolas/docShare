package com.subject77.docshare.modules.document.entities;

import com.subject77.docshare.modules.user.entities.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "document_versions", uniqueConstraints = {
    @UniqueConstraint(name = "uk_doc_versions_doc_ver", columnNames = {"document_id", "version_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private DocumentEntity document;

    @Column(nullable = false)
    private Integer versionNumber;

    @Column(nullable = false)
    private String s3Bucket;

    @Column(nullable = false)
    private String objectKey;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private Long fileSize;

    @Column(name = "checksum_sha256", nullable = false)
    private String checksumSha256;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_user_id", referencedColumnName = "id", nullable = false)
    private UserEntity uploadedBy;

    @Column(nullable = false, updatable = false)
    private Instant uploadedAt;

    @PrePersist
    protected void onUpload() {
        if (uploadedAt == null) {
            uploadedAt = Instant.now();
        }
    }
}
