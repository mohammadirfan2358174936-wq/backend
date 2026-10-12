package com.example.eventmanagement.exception;

/** 404 — the requested user does not exist. */
public class UserNotFoundException extends ApiException {
    public UserNotFoundException(Long id) {
        super(ErrorCode.USER_NOT_FOUND, "User %d does not exist.".formatted(id));
    }

    public UserNotFoundException(String identifier) {
        super(ErrorCode.USER_NOT_FOUND, "No account found for “%s”.".formatted(identifier));
    }
}
