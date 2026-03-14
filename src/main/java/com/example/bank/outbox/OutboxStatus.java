package com.example.bank.outbox;

public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED
}
