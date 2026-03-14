package com.example.bank.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

//
@Service
@RequiredArgsConstructor
public class OutboxStatusService {

    private final OutboxRepository outboxRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(Long id) {
        outboxRepository.findById(id).ifPresent(event -> {
            event.markAsSent();
        });
    }
}