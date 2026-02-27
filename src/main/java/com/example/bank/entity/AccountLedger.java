package com.example.bank.entity;

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
                columnNames = {"transfer_id", "account_id", "type"}
        )
)
public class AccountLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_id", nullable = false, updatable = false)
    private String transferId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    private EntryType type;

    @Column(updatable = false)
    private Long amount;

    private LocalDateTime createdAt;

    public AccountLedger(
            String transferId,
            Long accountId,
            EntryType type,
            Long amount
    ) {
        this.transferId = transferId;
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.createdAt = LocalDateTime.now();
    }

}
