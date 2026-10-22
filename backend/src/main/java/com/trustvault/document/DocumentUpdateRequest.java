package com.trustvault.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentUpdateRequest(
    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title cannot exceed 255 characters")
    String title,

    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    String description,

    String categoryId,

    @Size(max = 500, message = "Tags cannot exceed 500 characters")
    String tags
) {}
