package com.example.bank.controller;

import com.example.bank.entity.TransferLedger;
import com.example.bank.entity.TransferStatus;
import com.example.bank.event.ExternalDepositFailedEvent;
import com.example.bank.repository.TransferLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@RequestMapping("/test")
public class ExternalTransferTestController {

    private final TransferLedgerRepository transferLedgerRepository;
    private final ApplicationEventPublisher eventPublisher;

    @PostMapping("/external-transfer/fail")
    public String simulateExternalDepositFail(@RequestParam String transferId) {
        TransferLedger transfer = transferLedgerRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("송금 요청 없음"));

        // 테스트용: 외부 입금 실패 직전 상태라고 가정
        transfer.markCompensating();

        eventPublisher.publishEvent(
                new ExternalDepositFailedEvent(
                        transferId,
                        "타행 입금 실패 테스트",
                        LocalDateTime.now()
                )
        );

        return "실패 이벤트 발행 완료: " + transferId;
    }
}