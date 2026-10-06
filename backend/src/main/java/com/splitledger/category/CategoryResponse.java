package com.splitledger.category;

import java.util.UUID;

public record CategoryResponse(UUID id, String name, String color, boolean system) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getColor(), category.isSystem());
    }
}
