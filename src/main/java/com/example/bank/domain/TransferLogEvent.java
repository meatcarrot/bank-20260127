package com.example.bank.domain;

import org.springframework.cglib.core.Local;

import java.time.LocalDateTime;

public class TransferLogEvent {
    private Long fromAccountId;
    private Long toAccountId;
    private int amount;
    private LocalDateTime timestamp;

    public TransferLogEvent(Long fromAccountId, Long toAccountId, int amount, LocalDateTime now) {
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.timestamp = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "TransferLogEvent{" +
                "fromAccountId=" + fromAccountId +
                ", toAccountId=" + toAccountId +
                ", amount=" + amount +
                ", timestamp=" + timestamp +
                '}';
    }

}
