package com.example.bank.repository;

import com.example.bank.entity.AccountLedger;
import com.example.bank.entity.EntryType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountLedgerRepository
        extends JpaRepository<AccountLedger, Long> {

    boolean existsByTransactionIdAndAccountIdAndType(
            String transactionId,
            Long accountId,
            EntryType type
    );
}
