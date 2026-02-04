package com.example.bank.domain;

public class TransferCompletedEvent {

    private final Long fromId;
    private final Long toId;
    private final int amount;
    private final long createdAt;

    public TransferCompletedEvent(Long fromId, Long toId, int amount) {
        this.fromId = fromId;
        this.toId = toId;
        this.amount = amount;
        this.createdAt = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return "TransferCompletedEvent{" +
                "fromId=" + fromId +
                ", toId=" + toId +
                ", amount=" + amount +
                ", createdAt=" + createdAt +
                '}';
    }
}
