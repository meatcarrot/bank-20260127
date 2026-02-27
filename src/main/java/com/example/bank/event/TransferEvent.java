package com.example.bank.event;

import com.example.bank.entity.TransferStatus;
import lombok.Getter;

import java.time.LocalDateTime;

// record로 불변 객체를 만든다
public record TransferEvent(
        String transferId,
        Long fromAccountId,
        Long toAccountId,
        Long amount,
        TransferStatus transferStatus,
        LocalDateTime requestedAt
) {
}
