package com.example.bank.transfer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransferLedgerRepository
        extends JpaRepository<TransferLedger, String> {

}

