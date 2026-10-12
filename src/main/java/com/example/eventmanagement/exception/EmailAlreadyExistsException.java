package com.example.eventmanagement.exception;

/** 409 — an account already exists for this email. */
public class EmailAlreadyExistsException extends ApiException {
    public EmailAlreadyExistsException(String email) {
        super(ErrorCode.EMAIL_ALREADY_EXISTS, "An account already exists for %s.".formatted(email));
    }
}
