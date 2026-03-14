package com.example.bank.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountLedgerRepository
        extends JpaRepository<AccountLedger, Long> {

    boolean existsByTransactionIdAndAccountIdAndType(
            String transactionId,
            Long accountId,
            EntryType type
    );
}
