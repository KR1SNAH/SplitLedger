package com.splitledger.category;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.splitledger.common.ApiException;
import com.splitledger.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(UUID userId) {
        return categoryRepository.findVisibleTo(userId).stream().map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse create(UUID userId, CategoryRequest request) {
        String name = request.name().trim();
        ensureNameAvailable(userId, name, null);
        Category category = new Category(userRepository.getReferenceById(userId), name, normalizeColor(request.color()));
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(UUID userId, UUID categoryId, CategoryRequest request) {
        Category category = loadOwned(userId, categoryId);
        String name = request.name().trim();
        ensureNameAvailable(userId, name, categoryId);
        category.setName(name);
        category.setColor(normalizeColor(request.color()));
        return CategoryResponse.from(category);
    }

    /** Expenses in the deleted category become uncategorized (ON DELETE SET NULL). */
    @Transactional
    public void delete(UUID userId, UUID categoryId) {
        categoryRepository.delete(loadOwned(userId, categoryId));
    }

    /** A category the user may assign to an expense: built-in or their own. */
    @Transactional(readOnly = true)
    public Category loadAssignable(UUID userId, UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .filter(c -> c.isVisibleTo(userId))
                .orElseThrow(() -> ApiException.badRequest("Unknown category"));
    }

    private Category loadOwned(UUID userId, UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .filter(c -> c.isVisibleTo(userId))
                .orElseThrow(() -> ApiException.notFound("Category not found"));
        if (category.isSystem()) {
            throw ApiException.forbidden("Built-in categories cannot be changed");
        }
        return category;
    }

    /** Names must be unique across built-ins and the user's own categories, ignoring case. */
    private void ensureNameAvailable(UUID userId, String name, UUID excludeId) {
        boolean taken = categoryRepository.findVisibleTo(userId).stream()
                .anyMatch(c -> !c.getId().equals(excludeId) && c.getName().equalsIgnoreCase(name));
        if (taken) {
            throw ApiException.conflict("A category named \"" + name + "\" already exists");
        }
    }

    private static String normalizeColor(String color) {
        return color.toLowerCase(Locale.ROOT);
    }
}
