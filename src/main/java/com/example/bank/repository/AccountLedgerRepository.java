package com.example.bank.repository;

import com.example.bank.entity.AccountLedger;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountLedgerRepository
        extends JpaRepository<AccountLedger, Long> {
}
