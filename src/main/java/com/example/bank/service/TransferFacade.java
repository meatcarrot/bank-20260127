package com.example.bank.service;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
public class TransferFacade {

    private final TransferService transferService;

    public TransferFacade(TransferService transferService) {
        this.transferService = transferService;
    }

    public void transferWithRetry(Long fromId, Long toId, int amount) {
        while (true) {
            try {
                transferService.transfer(fromId, toId, amount);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ie) {

                }
            } catch (TransferService.SystemException e) {
                throw new RuntimeException("시스템 예외 발생",e);
            } catch (Exception e) {
                // 위에서 잡지 못하는 예외를 처리해줘야 100개 다 성공이다
                // ⭐ 핵심: 커넥션 부족이나 트랜잭션 생성 실패 시에도 재시도하도록 설정
                // 로그에 찍힌 "Could not open JPA EntityManager"를 잡기 위함
                if (e.getMessage().contains("EntityManager") || e.getMessage().contains("Connection")) {
                    backoff();
                    continue;
                }
                throw new RuntimeException("예상치 못한 치명적 에러", e);
            }
        }
    }

    private void backoff() {
        try { Thread.sleep(20); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
}
