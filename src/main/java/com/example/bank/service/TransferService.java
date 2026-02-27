package com.example.bank.service;

import com.example.bank.domain.Account;
import com.example.bank.domain.TransferCompletedEvent;
import com.example.bank.entity.AccountLedger;
import com.example.bank.entity.EntryType;
import com.example.bank.entity.TransferLedger;
import com.example.bank.entity.TransferStatus;
import com.example.bank.event.TransferEvent;
import com.example.bank.event.TransferProducer;
import com.example.bank.repository.AccountLedgerRepository;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.TransferLedgerRepository;
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
    private final AccountLedgerRepository accountLedgerRepository;
    private final TransferLedgerRepository transferLedgerRepository;

    public TransferService(AccountRepository accountRepository,
                           ApplicationEventPublisher eventPublisher,
                           TransferProducer transferProducer,
                           TransferLedgerRepository transferLedgerRepository,
                           AccountLedgerRepository accountLedgerRepository){
        this.accountRepository = accountRepository;
        this.eventPublisher = eventPublisher;
        this.transferProducer = transferProducer;
        this.transferLedgerRepository = transferLedgerRepository;
        this.accountLedgerRepository = accountLedgerRepository;
    }


    public String requestTransfer(Long fromId, Long toId, Long amount) throws SystemException {

        // 유효성 검사 (Fail-Fast): 카프카에 던지기 전에 최소한의 체크는 여기서!
        // 보내는 계좌가 존재하는지 정도는 여기서 확인하고 던지는 것이 좋습니다.
        if (!accountRepository.existsById(fromId)) {
            throw new IllegalArgumentException("보내는 계좌가 존재하지 않습니다.");
        }

        // 1. 유니크한 거래 ID 생성
        String transferId = UUID.randomUUID().toString();

        // 2. [추가] 거래 요청서(TransferLedger) 생성 및 저장
        // 이 단계가 성공해야만 카프카로 메시지를 보낼 자격이 생깁니다.
        TransferLedger ledgerRequest = new TransferLedger(
                transferId,
                fromId,
                toId,
                TransferStatus.PENDING, // 처음엔 대기 상태
                amount
        );
        transferLedgerRepository.save(ledgerRequest);

        // 3. [중요] 거래 요청서(TransferLedger)를 PENDING 상태로 먼저 저장
        // 만약 같은 transferId가 들어오면 DB의 Unique 제약조건이 우리를 지켜줍니다.
        TransferEvent event = new TransferEvent(
                transferId,
                fromId,
                toId,
                amount,
                TransferStatus.PENDING,
                LocalDateTime.now()
        );

        transferProducer.send(event);

        return transferId;
    }

    // rollbackFor을 통해서 시스템 예외는 롤백이 되도록
    // noRollBackFor를 통해서 비지니스적 예외는 롤백 안 되도록
    @Transactional(
            rollbackFor = SystemException.class,
            noRollbackFor = BuissnessException.class)
    public void processTransfer(TransferEvent event){

        log.info("실제 송금 처리 시작: ID={}", event.transferId());

        // 0. AccountLedger 2줄 기록 (차변/대변)
        saveAccountLedgers(event);

        // 1. 실제 계좌 업데이트
        // 1-1. 보내는 사람 계좌 조회
        Account fromAccount = accountRepository.findById(event.fromAccountId())
                .orElseThrow(()-> new IllegalArgumentException("계좌 없음"));
        // 1-2. 받는 사람 계좌 조회
        Account toAccount = accountRepository.findById(event.toAccountId())
                .orElseThrow(()-> new IllegalArgumentException(("계좌 없음")));
        // 1-3. 비지니스 로직 수행
        fromAccount.withdraw(event.amount());
        toAccount.deposit(event.amount());

        // 2. TransferLedger(요청서) 상태 변경: PENDING -> SUCESS
        TransferLedger request = transferLedgerRepository.findById(event.transferId())
                .orElseThrow(() -> new IllegalStateException("송금 요청 기록을 찾을 수 없습니다."));
        request.complete();

        // 3. (선택사항) 처리 완료 로그 남기기
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

    private void saveAccountLedgers(TransferEvent event){
        // 출금 기록 (DEBIT)
        accountLedgerRepository.save(new AccountLedger(
                event.transferId(),
                event.fromAccountId(),
                EntryType.DEBIT,
                event.amount()
        ));
        // 입금 기록 (CREDIT) - 여기도 마찬가지!
        accountLedgerRepository.save(new AccountLedger(
                event.transferId(),
                event.toAccountId(),
                EntryType.CREDIT,
                (long) event.amount()
        ));
    }

}


