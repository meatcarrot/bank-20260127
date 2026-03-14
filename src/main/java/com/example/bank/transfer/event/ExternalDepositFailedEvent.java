package com.example.bank.transfer.event;

import java.time.LocalDateTime;

public record ExternalDepositFailedEvent(
        String transferId,
        String reason,
        LocalDateTime failedAt
) {
}
