package com.example.bank.transfer;

import java.time.LocalDateTime;

public class TransferCompletedEvent {
    private final Long fromAccountId;
    private final Long toAccountId;
    private final Long amount;
    private final LocalDateTime timestamp;

    public TransferCompletedEvent(Long fromAccountId, Long toAccountId, Long amount, LocalDateTime now) {
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
