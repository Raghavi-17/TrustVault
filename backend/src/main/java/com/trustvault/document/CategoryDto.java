package com.trustvault.document;

public record CategoryDto(
    String id,
    String name,
    String description
) {
    public static CategoryDto fromEntity(Category category) {
        if (category == null) return null;
        return new CategoryDto(category.getId(), category.getName(), category.getDescription());
    }
}
