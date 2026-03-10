package com.example.bank.repository;

import com.example.bank.domain.Account;
import com.example.bank.entity.OutboxEvent;
import com.example.bank.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxRepository
        extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByStatus(OutboxStatus outboxStatus);
}
