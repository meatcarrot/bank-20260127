package com.example.bank.outbox;

import com.example.bank.transfer.TransferEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayer {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxStatusService outboxStatusService;
    private final ObjectMapper objectMapper;

    // 1. 트랜잭션 성공 직후 즉시 실행 (실시간성)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishImmediately(OutboxCreatedEvent event){
        log.info("AFTER_COMMIT 이벤트 수신: outboxId={}", event.outboxId());

        OutboxEvent outbox = outboxRepository.findById(event.outboxId())
                .orElseThrow(() -> new IllegalArgumentException("Outbox 없음"));

        sendToKafka(outbox);
    }

    // 2. 주기적으로 전송 실패건도 재시도
    @Scheduled(fixedDelay = 5000)
    public void retryFailedEvents() {
        List<OutboxEvent> failedEvents = outboxRepository.findByStatus(OutboxStatus.PENDING);
        log.info("Outbox 재시도 스케줄러 실행 - pending 건수={}", failedEvents.size());

        for (OutboxEvent event : failedEvents) {
            sendToKafka(event);
        }
    }

    private void sendToKafka(OutboxEvent outbox){
        log.info("Kafka 전송 시도: outboxId={}, aggregateId={}", outbox.getId(), outbox.getAggregateId());

        try {
            kafkaTemplate.send(
                    "transfer-topic",
                    resolvePartitionKey(outbox),
                    outbox.getPayload()
            ).whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Kafka 전송 성공: outboxId={}", outbox.getId());
                    outboxStatusService.markSent(outbox.getId());
                } else {
                    log.error("Kafka 전송 실패: outboxId={}", outbox.getId(), ex);
                }
            });
        } catch (Exception e) {
            log.error("Outbox publish failed: outboxId={}, aggregateId={}",
                    outbox.getId(), outbox.getAggregateId(), e);
        }
    }

    private String resolvePartitionKey(OutboxEvent outbox) throws JsonProcessingException {
        if (outbox.getPartitionKey() != null) {
            if (outbox.getPartitionKey().isBlank()) {
                throw new IllegalArgumentException("Outbox partitionKey is blank");
            }
            return outbox.getPartitionKey();
        }

        // Legacy rows: recover only from the original event, never from aggregateId.
        TransferEvent event = objectMapper.readerFor(TransferEvent.class)
                .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .readValue(outbox.getPayload());
        if (event == null || event.fromAccountId() == null || event.fromAccountId() <= 0
                || event.transferId() == null || !event.transferId().equals(outbox.getAggregateId())) {
            throw new IllegalArgumentException("Cannot safely recover Outbox partitionKey");
        }
        return String.valueOf(event.fromAccountId());
    }

}

