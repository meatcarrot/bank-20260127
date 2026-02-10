package com.example.bank.service;

import com.example.bank.domain.Account;
import com.example.bank.domain.TransferCompletedEvent;
import com.example.bank.domain.TransferLogEvent;
import com.example.bank.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;


@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TransferService(AccountRepository accountRepository,
                           ApplicationEventPublisher eventPublisher){
        this.accountRepository = accountRepository;
        this.eventPublisher = eventPublisher;
    }
    // rollbackFor을 통해서 시스템 예외는 롤백이 되도록
    // noRollBackFor를 통해서 비지니스적 예외는 롤백 안 되도록
    @Transactional(
            rollbackFor = SystemException.class,
            noRollbackFor = BuissnessException.class)
    public void transfer(Long fromId, Long toId, int amount) throws SystemException {
        Account from = accountRepository.findByIdWithOptimisticLock(fromId)
                .orElseThrow(()-> new IllegalArgumentException("계좌 없음"));
        Account to = accountRepository.findByIdWithOptimisticLock(toId)
                .orElseThrow(()-> new IllegalArgumentException(("계좌 없음")));


        from.withdraw(amount);
        to.deposit(amount);
        // 송금 성공 시에만 실제 출금된 로그를 남김
        eventPublisher.publishEvent(
                new TransferLogEvent(
                        from.getId(),
                        to.getId(),
                        amount,
                        LocalDateTime.now()
                )
        );

        //테스트용 실패
//        if (true) {
//            throw new SystemException("체크 예외 실패");
//        }

        // ⭐ 성공 시에만 송금 완료 이벤트 발행
        eventPublisher.publishEvent(
                new TransferCompletedEvent(
                        from.getId(),
                        to.getId(),
                        amount
                )
        );
    }

    // checked exception임. 이건 트랜젝션이 되지 않는다
    public class SystemException  extends Exception {
        public SystemException (String message) {
            super(message);
        }
    }

    public class BuissnessException extends Exception {
        public BuissnessException(String message) {
            super(message);
        }
    }

}


