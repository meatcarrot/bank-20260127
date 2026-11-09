package com.example.bank.transfer;

import com.example.bank.account.AccountRepository;
import com.example.bank.config.KafkaConfig;
import com.example.bank.ledger.AccountLedgerRepository;
import com.example.bank.outbox.OutboxCreatedEvent;
import com.example.bank.outbox.OutboxEvent;
import com.example.bank.outbox.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceOutboxTest {
    @Mock private AccountRepository accounts;
    @Mock private ApplicationEventPublisher publisher;
    @Mock private AccountLedgerRepository accountLedgers;
    @Mock private TransferLedgerRepository transferLedgers;
    @Mock private OutboxRepository outboxes;
    private final ObjectMapper mapper = new KafkaConfig("localhost:9092").objectMapper();
    private TransferService service;

    @BeforeEach
    void setUp() {
        service = new TransferService(accounts, publisher, accountLedgers, transferLedgers, mapper, outboxes);
    }

    @Test
    void storesAccountKeySeparatelyFromTransactionIdAndPreservesTransferDetails() throws Exception {
        when(accounts.existsById(anyLong())).thenReturn(true);
        when(accounts.existsByIdAndUserId(anyLong(), anyLong())).thenReturn(true);
        AtomicLong nextId = new AtomicLong();
        when(outboxes.saveAndFlush(any())).thenAnswer(invocation -> {
            OutboxEvent event = invocation.getArgument(0);
            ReflectionTestUtils.setField(event, "id", nextId.incrementAndGet());
            return event;
        });

        service.requestTransfer(1L, 10L, 30L, 100L);
        service.requestTransfer(1L, 10L, 30L, 100L);
        service.requestTransfer(1L, 20L, 30L, 100L);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        ArgumentCaptor<TransferLedger> ledgerCaptor = ArgumentCaptor.forClass(TransferLedger.class);
        verify(outboxes, times(3)).saveAndFlush(outboxCaptor.capture());
        verify(transferLedgers, times(3)).save(ledgerCaptor.capture());
        List<OutboxEvent> saved = outboxCaptor.getAllValues();
        assertThat(saved).extracting(OutboxEvent::getPartitionKey).containsExactly("10", "10", "20");
        assertThat(saved).extracting(OutboxEvent::getAggregateId).doesNotHaveDuplicates();
        for (int i = 0; i < saved.size(); i++) {
            OutboxEvent outbox = saved.get(i);
            TransferLedger ledger = ledgerCaptor.getAllValues().get(i);
            TransferEvent payload = mapper.readValue(outbox.getPayload(), TransferEvent.class);
            assertThat(outbox.getAggregateId()).isEqualTo(ledger.getTransferId());
            assertThat(payload.transferId()).isEqualTo(ledger.getTransferId());
            assertThat(payload.fromAccountId()).isEqualTo(ledger.getFromAccountId());
            assertThat(payload.toAccountId()).isEqualTo(30L);
            assertThat(payload.amount()).isEqualTo(100L);
            assertThat(payload.transferStatus()).isEqualTo(TransferStatus.PENDING);
            assertThat(payload.requestedAt()).isNotNull();
            verify(publisher).publishEvent(new OutboxCreatedEvent(outbox.getId()));
        }
    }

    @Test
    void alreadySuccessfulTransferRemainsIdempotent() {
        TransferLedger ledger = new TransferLedger("tx-1", 10L, 30L, TransferStatus.SUCCESS, 100L);
        when(transferLedgers.findById("tx-1")).thenReturn(Optional.of(ledger));

        service.processTransfer(new TransferEvent("tx-1", 10L, 30L, 100L,
                TransferStatus.PENDING, LocalDateTime.now()));

        verifyNoInteractions(accounts, accountLedgers, publisher, outboxes);
        assertThat(ledger.getStatus()).isEqualTo(TransferStatus.SUCCESS);
    }
}
