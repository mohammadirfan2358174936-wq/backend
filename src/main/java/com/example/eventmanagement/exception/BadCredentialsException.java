package com.example.eventmanagement.exception;

/** 401 — the submitted credentials did not authenticate. */
public class BadCredentialsException extends ApiException {
    public BadCredentialsException() {
        super(ErrorCode.BAD_CREDENTIALS);
    }
}
