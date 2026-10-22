package com.trustvault.document;

import java.time.Instant;

public record DocumentDto(
    String id,
    String title,
    String description,
    CategoryDto category,
    String originalFilename,
    String mimeType,
    Long fileSizeBytes,
    String sha256Hash,
    int version,
    String tags,
    String ownerId,
    String ownerUsername,
    String effectivePermission,
    Instant createdAt,
    Instant updatedAt
) {
    public static DocumentDto fromEntity(Document doc, String effectivePermission) {
        return new DocumentDto(
            doc.getId(),
            doc.getTitle(),
            doc.getDescription(),
            CategoryDto.fromEntity(doc.getCategory()),
            doc.getOriginalFilename(),
            doc.getMimeType(),
            doc.getFileSizeBytes(),
            doc.getSha256Hash(),
            doc.getVersion(),
            doc.getTags(),
            doc.getOwner().getId(),
            doc.getOwner().getUsername(),
            effectivePermission,
            doc.getCreatedAt(),
            doc.getUpdatedAt()
        );
    }
}
