package com.example.bank.transfer.event;

import java.time.LocalDateTime;

public record ExternalTransferRequestedEvent(
        String transferId,
        Long fromAccountId,
        String externalBankCode,
        String externalAccountNo,
        Long amount,
        LocalDateTime requestedAt
) {}
