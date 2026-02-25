package com.example.bank.service;

import com.example.bank.domain.Account;
import com.example.bank.domain.TransferCompletedEvent;
import com.example.bank.domain.TransferLogEvent;
import com.example.bank.event.TransferEvent;
import com.example.bank.event.TransferProducer;
import com.example.bank.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.UUID;


@Service
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
    @Transactional(
            rollbackFor = SystemException.class,
            noRollbackFor = BuissnessException.class)
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
//        eventPublisher.publishEvent(
//                new TransferCompletedEvent(
//                        fromId,
//                        toId,
//                        amount
//                )
//        );

        return transferId;
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


