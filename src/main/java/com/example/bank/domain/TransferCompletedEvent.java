package com.example.bank.domain;

import org.springframework.cglib.core.Local;

import java.time.LocalDateTime;

public class TransferCompletedEvent {
    private final Long fromAccountId;
    private final Long toAccountId;
    private final int amount;
    private final LocalDateTime timestamp;

    public TransferCompletedEvent(Long fromAccountId, Long toAccountId, int amount, LocalDateTime now) {
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.timestamp = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "TransferCompletedEvent{" +
                "fromAccountId=" + fromAccountId +
                ", toAccountId=" + toAccountId +
                ", amount=" + amount +
                ", timestamp=" + timestamp +
                '}';
    }

}
