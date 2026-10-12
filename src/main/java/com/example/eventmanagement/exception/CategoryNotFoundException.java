package com.example.eventmanagement.exception;

/** 404 — the requested category does not exist. */
public class CategoryNotFoundException extends ApiException {
    public CategoryNotFoundException(Long id) {
        super(ErrorCode.CATEGORY_NOT_FOUND, "Category %d does not exist.".formatted(id));
    }

    public CategoryNotFoundException(String slug) {
        super(ErrorCode.CATEGORY_NOT_FOUND, "Category “%s” does not exist.".formatted(slug));
    }
}
