package com.example.bank.service;

import com.example.bank.domain.Account;
import com.example.bank.domain.TransferCompletedEvent;
import com.example.bank.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import org.springframework.context.ApplicationEventPublisher;

import org.springframework.transaction.support.TransactionSynchronizationManager;

// 로그 서비스 분리(프록시 타야함)
@Service
class LogService {

    @Transactional(propagation = REQUIRES_NEW)
    public void logWithdraw() {
        System.out.println("===== LogService.logWithdraw =====");
        System.out.println("트랜잭션 활성화 여부: "
                + TransactionSynchronizationManager.isActualTransactionActive());
        System.out.println("트랜잭션 이름: "
                + TransactionSynchronizationManager.getCurrentTransactionName());
    }
}

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final LogService logService;
    // 로그를 위한 서비스를 주입하자

    public TransferService(AccountRepository accountRepository,
                           LogService logService,
                           ApplicationEventPublisher eventPublisher){
        this.accountRepository = accountRepository;
        this.logService = logService;
        this.eventPublisher = eventPublisher;
    }
    //rollbackFor을 통해서 모든 exception이 다 트랜젝션이 되도록 하는게 일반적
    @Transactional(rollbackFor = Exception.class)
    public void transfer(Long fromId, Long toId, int amount) throws TransferFailException {
        Account from = accountRepository.findById(fromId)
                .orElseThrow(()-> new IllegalArgumentException("계좌 없음"));
        Account to = accountRepository.findById(toId)
                .orElseThrow(()-> new IllegalArgumentException(("계좌 없음")));


        // ⭐ 성공 시에만 이벤트 발행
        eventPublisher.publishEvent(
                new TransferCompletedEvent(
                        from.getId(),
                        to.getId(),
                        amount
                )
        );

        from.withdraw(amount);

        logService.logWithdraw();// 출금

//        테스트용 실패
//        if (true) {
//            throw new TransferFailException("체크 예외 실패");
//        }

        to.deposit(amount);
    }

    // checked exception임. 이건 트랜젝션이 되지 않는다
    public class TransferFailException extends Exception {
        public TransferFailException(String message) {
            super(message);
        }
    }
}
