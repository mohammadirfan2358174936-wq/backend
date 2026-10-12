package com.example.eventmanagement.entity;

public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    REFUNDED;

    public boolean isSettled() {
        return this == SUCCESS;
    }
}
