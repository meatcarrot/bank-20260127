package com.example.bank.outbox;

import com.example.bank.config.KafkaConfig;
import com.example.bank.transfer.TransferEvent;
import com.example.bank.transfer.TransferStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelayerTest {
    @Mock private OutboxRepository repository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @Mock private OutboxStatusService statusService;

    private final ObjectMapper mapper = new KafkaConfig("localhost:9092").objectMapper();
    private OutboxRelayer relayer;

    @BeforeEach
    void setUp() {
        relayer = new OutboxRelayer(repository, kafkaTemplate, statusService, mapper);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void immediateAndScheduledPublishingUseTheSameAccountKeyAndOriginalPayload(boolean legacy) throws Exception {
        OutboxEvent first = outbox(1L, "tx-1", 10L, legacy);
        OutboxEvent second = outbox(2L, "tx-2", 10L, legacy);
        OutboxEvent third = outbox(3L, "tx-3", 20L, legacy);
        List<OutboxEvent> events = List.of(first, second, third);
        when(kafkaTemplate.send(eq("transfer-topic"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(repository.findByStatus(OutboxStatus.PENDING)).thenReturn(events);

        for (OutboxEvent event : events) {
            when(repository.findById(event.getId())).thenReturn(Optional.of(event));
            relayer.publishImmediately(new OutboxCreatedEvent(event.getId()));
        }
        relayer.retryFailedEvents();

        for (OutboxEvent event : events) {
            TransferEvent original = mapper.readValue(event.getPayload(), TransferEvent.class);
            verify(kafkaTemplate, times(2)).send("transfer-topic",
                    String.valueOf(original.fromAccountId()), event.getPayload());
            verify(statusService, times(2)).markSent(event.getId());
            assertThat(event.getAggregateId()).isEqualTo(original.transferId());
            assertThat(event.getPartitionKey()).isEqualTo(legacy ? null : String.valueOf(original.fromAccountId()));
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "not-json", "null", "{}",
            "{\"transferId\":\"tx-1\"}",
            "{\"transferId\":\"tx-1\",\"fromAccountId\":null}",
            "{\"transferId\":\"tx-1\",\"fromAccountId\":0}",
            "{\"transferId\":\"tx-1\",\"fromAccountId\":-1}",
            "{\"transferId\":\"tx-1\",\"fromAccountId\":1.5}",
            "{\"transferId\":\"tx-1\",\"fromAccountId\":9223372036854775808}",
            "{\"transferId\":\"tx-1\",\"fromAccountId\":\"invalid\"}",
            "{\"fromAccountId\":10}",
            "{\"transferId\":\"wrong-transaction\",\"fromAccountId\":10}"
    })
    void unrecoverableLegacyDataIsNotPublishedOrMarkedSent(String payload) {
        OutboxEvent event = OutboxEvent.create("TRANSFER", "tx-1", null, "TransferCreated", payload);
        when(repository.findByStatus(OutboxStatus.PENDING)).thenReturn(List.of(event));

        relayer.retryFailedEvents();

        verifyNoInteractions(kafkaTemplate, statusService);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void invalidRowDoesNotPreventOtherPendingRowsFromBeingPublished() throws Exception {
        OutboxEvent invalid = OutboxEvent.create("TRANSFER", "tx-bad", " ", "TransferCreated", "{}");
        OutboxEvent valid = outbox(2L, "tx-2", 10L, true);
        when(repository.findByStatus(OutboxStatus.PENDING)).thenReturn(List.of(invalid, valid));
        when(kafkaTemplate.send("transfer-topic", "10", valid.getPayload()))
                .thenReturn(CompletableFuture.completedFuture(null));

        relayer.retryFailedEvents();

        verify(kafkaTemplate).send("transfer-topic", "10", valid.getPayload());
        verifyNoMoreInteractions(kafkaTemplate);
        verify(statusService).markSent(2L);
        verifyNoMoreInteractions(statusService);
        assertThat(invalid.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void publishingFailuresRemainPendingAndRetryWithTheSameKey(boolean synchronousFailure) throws Exception {
        OutboxEvent event = outbox(1L, "tx-1", 10L, false);
        when(repository.findById(1L)).thenReturn(Optional.of(event));
        when(repository.findByStatus(OutboxStatus.PENDING)).thenReturn(List.of(event));
        var send = when(kafkaTemplate.send("transfer-topic", "10", event.getPayload()));
        if (synchronousFailure) {
            send.thenThrow(new IllegalStateException("broker unavailable"))
                    .thenReturn(CompletableFuture.completedFuture(null));
        } else {
            send.thenReturn(CompletableFuture.<SendResult<String, String>>failedFuture(
                    new IllegalStateException("broker unavailable")))
                    .thenReturn(CompletableFuture.completedFuture(null));
        }

        relayer.publishImmediately(new OutboxCreatedEvent(1L));
        verifyNoInteractions(statusService);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);

        relayer.retryFailedEvents();
        verify(kafkaTemplate, times(2)).send("transfer-topic", "10", event.getPayload());
        verify(statusService).markSent(1L);
    }

    private OutboxEvent outbox(Long id, String transferId, Long fromAccountId, boolean legacy) throws Exception {
        TransferEvent event = new TransferEvent(transferId, fromAccountId, 30L, 100L,
                TransferStatus.PENDING, LocalDateTime.of(2026, 11, 10, 12, 0));
        OutboxEvent outbox = OutboxEvent.create("TRANSFER", transferId,
                legacy ? null : String.valueOf(fromAccountId), "TransferCreated", mapper.writeValueAsString(event));
        ReflectionTestUtils.setField(outbox, "id", id);
        return outbox;
    }
}
