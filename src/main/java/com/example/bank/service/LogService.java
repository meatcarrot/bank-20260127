package com.example.bank.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

// 로그 서비스 분리(프록시 타야함)
@Service
public class LogService {

    @Transactional(propagation = REQUIRES_NEW)
    public void logWithdraw() {
        System.out.println("===== LogService.logWithdraw =====");
        System.out.println("트랜잭션 활성화 여부: "
                + TransactionSynchronizationManager.isActualTransactionActive());
        System.out.println("트랜잭션 이름: "
                + TransactionSynchronizationManager.getCurrentTransactionName());
    }
}
