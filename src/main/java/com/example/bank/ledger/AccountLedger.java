package com.example.bank.ledger;

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
                columnNames = {"transaction_id", "account_id", "type"}
        )
)
public class AccountLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private String transactionId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    private EntryType type;

    @Column(updatable = false)
    private Long amount;

    private LocalDateTime createdAt;

    public AccountLedger(
            String transactionId,
            Long accountId,
            EntryType type,
            Long amount
    ) {
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.createdAt = LocalDateTime.now();
    }

}
