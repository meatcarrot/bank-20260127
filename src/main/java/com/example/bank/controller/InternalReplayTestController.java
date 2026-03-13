package com.example.bank.controller;

import com.example.bank.entity.TransferLedger;
import com.example.bank.entity.TransferStatus;
import com.example.bank.event.TransferEvent;
import com.example.bank.repository.TransferLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequiredArgsConstructor
@RequestMapping("/test")
public class InternalReplayTestController {

    private final TransferLedgerRepository transferLedgerRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @PostMapping("/replay-transfer")
    public String replay(@RequestParam String transferId) throws Exception {
        TransferLedger transfer = transferLedgerRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("송금 요청 없음"));

        TransferEvent event = new TransferEvent(
                transfer.getTransferId(),
                transfer.getFromAccountId(),
                transfer.getToAccountId(),
                transfer.getAmount(),
                TransferStatus.PENDING,
                transfer.getCreatedAt()
        );

        kafkaTemplate.send(
                "transfer-topic",
                transfer.getTransferId(),
                objectMapper.writeValueAsString(event)
        );

        return "재발행 완료";
    }
}
