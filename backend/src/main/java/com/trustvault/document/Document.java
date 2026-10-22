package com.trustvault.document;

import com.trustvault.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class Document {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "original_filename", length = 255, nullable = false)
    private String originalFilename;

    @Column(name = "sanitized_filename", length = 255, nullable = false)
    private String sanitizedFilename;

    @Column(name = "storage_key", length = 255, nullable = false, unique = true)
    private String storageKey;

    @Column(name = "mime_type", length = 100, nullable = false)
    private String mimeType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "sha256_hash", length = 64, nullable = false)
    private String sha256Hash;

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "tags", length = 500)
    private String tags;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Document() {
        this.id = UUID.randomUUID().toString();
    }

    public Document(User owner, String title, String description, Category category,
                    String originalFilename, String sanitizedFilename, String storageKey,
                    String mimeType, Long fileSizeBytes, String sha256Hash, String tags) {
        this.id = UUID.randomUUID().toString();
        this.owner = owner;
        this.title = title;
        this.description = description;
        this.category = category;
        this.originalFilename = originalFilename;
        this.sanitizedFilename = sanitizedFilename;
        this.storageKey = storageKey;
        this.mimeType = mimeType;
        this.fileSizeBytes = fileSizeBytes;
        this.sha256Hash = sha256Hash;
        this.tags = tags;
        this.version = 1;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getSanitizedFilename() {
        return sanitizedFilename;
    }

    public void setSanitizedFilename(String sanitizedFilename) {
        this.sanitizedFilename = sanitizedFilename;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getSha256Hash() {
        return sha256Hash;
    }

    public void setSha256Hash(String sha256Hash) {
        this.sha256Hash = sha256Hash;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Document document)) return false;
        return Objects.equals(id, document.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
