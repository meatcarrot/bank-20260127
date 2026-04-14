package com.example.bank.transfer;

import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransferFacade {

    private final TransferService transferService;
    private final Random random = new Random();  // ← 추가

    public void processTransferWithRetry(TransferEvent event) {
        int maxRetry = 10;  // ← 5 → 10으로 증가
        int attempt = 0;

        while (true) {
            try {
                attempt++;
                transferService.processTransfer(event);
                return;  // 성공
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException e) {
                log.warn("낙관적 락 충돌 발생 - transferId={}, attempt={}",
                        event.transferId(), attempt, e);

                if (attempt >= maxRetry) {
                    throw e;
                }

                exponentialBackoffWithJitter(attempt);  // ← 변경
            }
        }
    }

    // ✅ 기존: 고정 간격
    // private void backoff(int attempt) {
    //     try {
    //         Thread.sleep(20L * attempt);
    //     } catch (InterruptedException e) {
    //         Thread.currentThread().interrupt();
    //         throw new IllegalStateException("재시도 대기 중 인터럽트 발생", e);
    //     }
    // }

    // ✅ 개선: Exponential Backoff + Random Jitter
    private void exponentialBackoffWithJitter(int attempt) {
        try {
            // 기본 대기 시간: 2^attempt * 50ms
            // 1차: 100ms, 2차: 200ms, 3차: 400ms, 4차: 800ms...
            long baseDelay = (long) Math.pow(2, attempt) * 50;

            // 최대 2초로 제한
            long cappedDelay = Math.min(baseDelay, 2000);

            // Random Jitter: ±30%
            long jitter = (long) (cappedDelay * (0.7 + random.nextDouble() * 0.6));

            log.debug("재시도 대기 중 - attempt={}, delay={}ms", attempt, jitter);
            Thread.sleep(jitter);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("재시도 대기 중 인터럽트 발생", e);
        }
    }
}