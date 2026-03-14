package com.example.bank.transfer;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"transfer_id"}
        )
)
public class TransferLedger {

    @Id
    private String transferId;

    private Long fromAccountId;
    private Long toAccountId;
    private Long amount;

    @Enumerated(EnumType.STRING)
    private TransferStatus status;

    private LocalDateTime createdAt;

    public TransferLedger(
            String transferId,
            Long fromAccountId,
            Long toAccountId,
            TransferStatus status,
            Long amount
    ) {
        this.transferId = transferId;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.status = status;
        this.amount = amount;
        this.createdAt = LocalDateTime.now();
    }
    // 상태 변경을 위한 메서드
    public void complete() {
        this.status = TransferStatus.SUCCESS;
    }
    public void failed() {this.status = TransferStatus.FAILED;}

    public void markCompensating() {
        this.status = TransferStatus.COMPENSATING;
    }

    public void markCompensated() {
        this.status = TransferStatus.COMPENSATED;
    }
}
