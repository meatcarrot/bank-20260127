package com.example.bank.repository;

import com.example.bank.entity.AccountLedger;
import com.example.bank.entity.TransferLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransferLedgerRepository
        extends JpaRepository<TransferLedger, String> {

}

