package com.example.bank.service;

import com.example.bank.entity.AccountLedger;
import com.example.bank.entity.EntryType;
import com.example.bank.entity.TransferLedger;
import com.example.bank.entity.TransferStatus;
import com.example.bank.repository.AccountLedgerRepository;
import com.example.bank.repository.TransferLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalTransferService {

    private final TransferLedgerRepository transferLedgerRepository;
    private final AccountLedgerRepository accountLedgerRepository;


    @Transactional
    public void compensateWithdraw(String transferId){
        log.info("보상 트랜잭션 시작: transferId={}", transferId);

        TransferLedger transfer = transferLedgerRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("송금 요청이 존재하지 않습니다."));

        log.info("보상 대상 현재 상태: transferId={}, status={}",
                transferId, transfer.getStatus());

        if (transfer.getStatus() == TransferStatus.SUCCESS) {
            log.warn("이미 성공한 거래라 보상 불가: transferId={}", transferId);
            return;
        }

        if (transfer.getStatus() == TransferStatus.COMPENSATED) {
            log.info("이미 보상 완료된 거래입니다: transferId={}", transferId);
            return;
        }

        boolean alreadyCompensatedLedger =
                accountLedgerRepository.existsByTransactionIdAndAccountIdAndType(
                        transferId,
                        transfer.getFromAccountId(),
                        EntryType.CREDIT
                );

        log.info("기존 보상 ledger 존재 여부: transferId={}, exists={}",
                transferId, alreadyCompensatedLedger);

        if (alreadyCompensatedLedger) {
            transfer.markCompensated();
            log.info("이미 보상 ledger가 존재합니다. transferId={}", transferId);
            return;
        }

        transfer.markCompensating();

        accountLedgerRepository.save(new AccountLedger(
                transferId,
                transfer.getFromAccountId(),
                EntryType.CREDIT,
                transfer.getAmount()
        ));

        transfer.markCompensated();

        log.info("보상 트랜잭션 완료: transferId={}", transferId);
    }

}
