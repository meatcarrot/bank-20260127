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

    // 이건 순수 실패용 컨트롤러
    @PostMapping("/external-transfer/fail")
    public String simulateExternalDepositFail(@RequestParam String transferId) {
        eventPublisher.publishEvent(
                new ExternalDepositFailedEvent(
                        transferId,
                        "타행 입금 실패 테스트",
                        LocalDateTime.now()
                )
        );

        return "실패 이벤트 발행 완료: " + transferId;
    }

    // 강제로 보상 대기상태로 바꿔서 실행시키는 컨트롤러
    @PostMapping("/external-transfer/fail-force")
    public String simulateExternalDepositFailForce(@RequestParam String transferId) {
        TransferLedger transfer = transferLedgerRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("송금 요청 없음"));

        transfer.markCompensating();

        eventPublisher.publishEvent(
                new ExternalDepositFailedEvent(
                        transferId,
                        "타행 입금 실패 강제 테스트",
                        LocalDateTime.now()
                )
        );

        return "강제 실패 이벤트 발행 완료: " + transferId;
    }
}