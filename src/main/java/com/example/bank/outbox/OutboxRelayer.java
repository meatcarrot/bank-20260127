package com.example.bank.outbox;


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
                    String.valueOf(outbox.getAggregateId()),
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
            log.error("전송 로직 에러", e);
        }
    }

}

