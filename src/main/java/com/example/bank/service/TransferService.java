package com.example.bank.service;

import com.example.bank.domain.Account;
import com.example.bank.domain.TransferCompletedEvent;
import com.example.bank.event.TransferEvent;
import com.example.bank.event.TransferProducer;
import com.example.bank.repository.AccountRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.UUID;


@Service
@Slf4j
public class TransferService {

    private final AccountRepository accountRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TransferProducer transferProducer;

    public TransferService(AccountRepository accountRepository,
                           ApplicationEventPublisher eventPublisher, TransferProducer transferProducer){
        this.accountRepository = accountRepository;
        this.eventPublisher = eventPublisher;
        this.transferProducer = transferProducer;
    }
    // rollbackFor을 통해서 시스템 예외는 롤백이 되도록
    // noRollBackFor를 통해서 비지니스적 예외는 롤백 안 되도록
    @Transactional
    public String requestTransfer(Long fromId, Long toId, int amount) throws SystemException {

        String transferId = UUID.randomUUID().toString();

        TransferEvent event = new TransferEvent(
                transferId,
                fromId,
                toId,
                amount,
                LocalDateTime.now()
        );

        transferProducer.send(event);

        // ⭐ 성공 시에만 송금 완료 이벤트 발행
        // ** consumer가 추가할 부분**
//        eventPublisher.publishEvent(
//                new TransferCompletedEvent(
//                        fromId,
//                        toId,
//                        amount
//                )
//        );

        return transferId;
    }

    @Transactional(
            rollbackFor = SystemException.class,
            noRollbackFor = BuissnessException.class)
    public void processTransfer(TransferEvent event){

        log.info("실제 송금 처리 시작: ID={}", event.transferId());

        // 1. 보내는 사람 계좌 조회
        Account fromAccount = accountRepository.findById(event.fromAccountId())
                .orElseThrow(()-> new IllegalArgumentException("계좌 없음"));
        // 2. 받는 사람 계좌 조회
        Account toAccount = accountRepository.findById(event.toAccountId())
                .orElseThrow(()-> new IllegalArgumentException(("계좌 없음")));
        // 3. 비지니스 로직 수행
        fromAccount.withdraw(event.amount());
        toAccount.deposit(event.amount());

        // 4. (선택사항) 처리 완료 로그 남기기
        log.info("송금 처리 완료! {}원이 {} -> {}로 이동했습니다",
                event.amount(), fromAccount.getId(), toAccount.getId());

        // 송금 성공 시에만 송금 완료 이벤트를 발행
        eventPublisher.publishEvent(
                new TransferCompletedEvent(
                        fromAccount.getId(),
                        toAccount.getId(),
                        event.amount(),
                        LocalDateTime.now()
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


