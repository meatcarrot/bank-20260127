package com.example.bank.transfer;

import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransferFacade {

    private final TransferService transferService;

    public void processTransferWithRetry(TransferEvent event) {
        int maxRetry = 5;
        int attempt = 0;

        while (true) {
            try {
                attempt++;
                transferService.processTransfer(event);
                return;
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException e) {
                log.warn("낙관적 락 충돌 발생 - transferId={}, attempt={}", event.transferId(), attempt, e);

                if (attempt >= maxRetry) {
                    throw e;
                }

                backoff(attempt);
            }
        }
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(20L * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("재시도 대기 중 인터럽트 발생", e);
        }
    }
}