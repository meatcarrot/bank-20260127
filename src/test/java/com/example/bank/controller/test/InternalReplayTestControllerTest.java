package com.example.bank.controller.test;

import com.example.bank.config.KafkaConfig;
import com.example.bank.transfer.TransferEvent;
import com.example.bank.transfer.TransferLedger;
import com.example.bank.transfer.TransferLedgerRepository;
import com.example.bank.transfer.TransferStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalReplayTestControllerTest {
    @Mock private TransferLedgerRepository repository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void replayUsesSourceAccountKeyAndPreservesTheTransactionPayload() throws Exception {
        ObjectMapper mapper = new KafkaConfig("localhost:9092").objectMapper();
        InternalReplayTestController controller = new InternalReplayTestController(repository, kafkaTemplate, mapper);
        List<TransferLedger> transfers = List.of(
                new TransferLedger("tx-1", 10L, 30L, TransferStatus.SUCCESS, 100L),
                new TransferLedger("tx-2", 10L, 40L, TransferStatus.SUCCESS, 200L),
                new TransferLedger("tx-3", 20L, 30L, TransferStatus.SUCCESS, 300L));

        for (TransferLedger transfer : transfers) {
            when(repository.findById(transfer.getTransferId())).thenReturn(Optional.of(transfer));
            controller.replay(transfer.getTransferId());

            TransferEvent expected = new TransferEvent(transfer.getTransferId(), transfer.getFromAccountId(),
                    transfer.getToAccountId(), transfer.getAmount(), TransferStatus.PENDING, transfer.getCreatedAt());
            verify(kafkaTemplate).send("transfer-topic", String.valueOf(transfer.getFromAccountId()),
                    mapper.writeValueAsString(expected));
        }
        verifyNoMoreInteractions(kafkaTemplate);
    }
}
