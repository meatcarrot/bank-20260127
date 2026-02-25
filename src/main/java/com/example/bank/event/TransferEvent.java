package com.example.bank.event;

import java.time.LocalDateTime;

// record로 불변 객체를 만든다
public record TransferEvent(
        String transferId,
        Long fromAccountId,
        Long toAccountId,
        int amount,
        LocalDateTime requestedAt
) {
}
