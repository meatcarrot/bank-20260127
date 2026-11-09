package com.example.bank.integration;

import com.example.bank.account.AccountRepository;
import com.example.bank.config.KafkaConfig;
import com.example.bank.controller.test.InternalReplayTestController;
import com.example.bank.domain.Account;
import com.example.bank.ledger.AccountLedger;
import com.example.bank.ledger.AccountLedgerRepository;
import com.example.bank.ledger.EntryType;
import com.example.bank.outbox.*;
import com.example.bank.transfer.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@Tag("integration")
@EnabledIfEnvironmentVariable(named = "BANK_INTEGRATION_TEST", matches = "true")
@ActiveProfiles({"test", "partition-integration"})
@SpringBootTest(classes = TransferPartitionIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
                "logging.level.org.apache.kafka=WARN"})
class TransferPartitionIntegrationTest {
    // The opt-in suite only ever creates/drops this dedicated integration database's schema.
    private static final String DATABASE_URL = "jdbc:mysql://localhost:13306/bank_key_it";
    private static final String BROKERS = "localhost:19092";

    @Configuration(proxyBeanMethods = false)
    @Profile("partition-integration")
    @EnableAutoConfiguration
    @EntityScan("com.example.bank")
    @EnableJpaRepositories("com.example.bank")
    @Import({KafkaConfig.class, TransferService.class, TransferFacade.class,
            OutboxRelayer.class, OutboxStatusService.class, InternalReplayTestController.class})
    static class TestApplication {
        // Import production transaction/event listeners, but no background Scheduler or Consumer.
    }

    @DynamicPropertySource
    static void integrationProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> DATABASE_URL);
        properties.add("spring.datasource.username", () -> "bank_it");
        properties.add("spring.datasource.password", () -> "bank_it");
        properties.add("spring.kafka.bootstrap-servers", () -> BROKERS);
    }

    @Autowired private AccountRepository accounts;
    @Autowired private AccountLedgerRepository accountLedgers;
    @Autowired private TransferLedgerRepository transferLedgers;
    @Autowired private OutboxRepository outboxes;
    @Autowired private TransferService service;
    @Autowired private OutboxRelayer relayer;
    @Autowired private InternalReplayTestController replay;
    @Autowired private ObjectMapper mapper;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ApplicationContext context;

    @BeforeAll
    static void provisionTopicUsingProductionDefinition() throws Exception {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BROKERS))) {
            if (!admin.listTopics().names().get(15, TimeUnit.SECONDS).contains("transfer-topic")) {
                admin.createTopics(List.of(new KafkaConfig(BROKERS).transferTopic())).all().get(15, TimeUnit.SECONDS);
            }
            assertThat(admin.describeTopics(List.of("transfer-topic")).allTopicNames()
                    .get(15, TimeUnit.SECONDS).get("transfer-topic").partitions()).hasSize(3);
        }
    }

    @BeforeEach
    void clearIsolatedDatabase() {
        assertThat(context.getBeansOfType(TransferConsumer.class)).isEmpty();
        assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
        accountLedgers.deleteAll();
        outboxes.deleteAll();
        transferLedgers.deleteAll();
        accounts.deleteAll();
    }

    @Test
    void afterCommitSchedulerAndReplayRecordTheSameAccountKeysInTheSameKafkaPartitions() throws Exception {
        Account first = accounts.saveAndFlush(new Account(1L, 10_000L));
        Account second = accounts.saveAndFlush(new Account(1L, 10_000L));
        Account recipient = accounts.saveAndFlush(new Account(2L, 0L));

        try (KafkaConsumer<String, String> consumer = consumerAtEnd()) {
            service.requestTransfer(1L, first.getId(), recipient.getId(), 100L);
            service.requestTransfer(1L, first.getId(), recipient.getId(), 200L);
            service.requestTransfer(1L, second.getId(), recipient.getId(), 300L);
            List<OutboxEvent> saved = outboxes.findAll();
            assertThat(saved).hasSize(3);
            assertThat(saved).extracting(OutboxEvent::getAggregateId).doesNotHaveDuplicates();
            assertThat(saved).extracting(OutboxEvent::getPartitionKey)
                    .containsExactlyInAnyOrder(first.getId().toString(), first.getId().toString(), second.getId().toString());
            Map<String, String> originalPayloads = new HashMap<>();
            for (OutboxEvent outbox : saved) {
                originalPayloads.put(outbox.getAggregateId(), outbox.getPayload());
                TransferEvent event = mapper.readValue(outbox.getPayload(), TransferEvent.class);
                assertThat(event.transferId()).isEqualTo(outbox.getAggregateId());
                assertThat(event.toAccountId()).isEqualTo(recipient.getId());
                assertThat(event.transferStatus()).isEqualTo(TransferStatus.PENDING);
                assertThat(transferLedgers.findById(event.transferId())).isPresent();
            }
            Map<String, Integer> partitionsByAccount = new HashMap<>();
            assertRecords(readRecords(consumer, 3), originalPayloads, partitionsByAccount);
            awaitSent(3);

            // Simulate a pre-migration row and PENDING republication in the real database.
            Long legacyId = saved.get(0).getId();
            jdbc.update("UPDATE outbox_event SET partition_key = NULL WHERE id = ?", legacyId);
            jdbc.update("UPDATE outbox_event SET status = 'PENDING'");
            relayer.retryFailedEvents();
            assertRecords(readRecords(consumer, 3), originalPayloads, partitionsByAccount);
            awaitSent(3);
            assertThat(outboxes.findById(legacyId).orElseThrow().getPartitionKey()).isNull();

            Map<String, String> replayPayloads = new HashMap<>();
            for (TransferLedger ledger : transferLedgers.findAll()) {
                replay.replay(ledger.getTransferId());
                replayPayloads.put(ledger.getTransferId(), mapper.writeValueAsString(new TransferEvent(
                        ledger.getTransferId(), ledger.getFromAccountId(), ledger.getToAccountId(),
                        ledger.getAmount(), TransferStatus.PENDING, ledger.getCreatedAt())));
            }
            assertRecords(readRecords(consumer, 3), replayPayloads, partitionsByAccount);
            assertThat(partitionsByAccount).hasSize(2);
            System.out.println("KAFKA_PARTITIONS " + partitionsByAccount + "; records=9; paths=AFTER_COMMIT,SCHEDULER,REPLAY");
        }
    }

    @Test
    void malformedLegacyRowRemainsPendingWhileValidLegacyRowIsActuallyPublished() throws Exception {
        String transferId = UUID.randomUUID().toString();
        String payload = mapper.writeValueAsString(new TransferEvent(transferId, 101L, 102L, 100L,
                TransferStatus.PENDING, LocalDateTime.now()));
        OutboxEvent invalid = outboxes.saveAndFlush(
                OutboxEvent.create("TRANSFER", "invalid", null, "TransferCreated", "not-json"));
        OutboxEvent valid = outboxes.saveAndFlush(
                OutboxEvent.create("TRANSFER", transferId, null, "TransferCreated", payload));
        try (KafkaConsumer<String, String> consumer = consumerAtEnd()) {
            relayer.retryFailedEvents();
            assertRecords(readRecords(consumer, 1), Map.of(transferId, payload), new HashMap<>());
            awaitSent(1);
            assertThat(outboxes.findById(invalid.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(outboxes.findById(valid.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENT);
            assertThat(consumer.poll(Duration.ofSeconds(2)).isEmpty()).isTrue();
        }
    }

    @Test
    void mysqlUniqueConstraintAllowsOnlyOneConcurrentDuplicateAccountLedgerInsert() throws Exception {
        Account account = accounts.saveAndFlush(new Account(1L, 1_000L));
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Throwable> failures = concurrent(2, () -> {
            awaitBarrier(barrier);
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    accountLedgers.saveAndFlush(new AccountLedger("duplicate", account.getId(), EntryType.DEBIT, 100L)));
        });
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(accountLedgers.count()).isEqualTo(1);

        // All three columns participate in uniqueness; distinct type/account/transaction is allowed.
        accountLedgers.saveAndFlush(new AccountLedger("duplicate", account.getId(), EntryType.CREDIT, 100L));
        accountLedgers.saveAndFlush(new AccountLedger("other", account.getId(), EntryType.DEBIT, 100L));
        accountLedgers.saveAndFlush(new AccountLedger("duplicate", account.getId() + 1, EntryType.DEBIT, 100L));
        assertThat(accountLedgers.count()).isEqualTo(4);
    }

    @Test
    void optimisticLockRejectsOneOfTwoUpdatesThatReadTheSameMysqlVersion() throws Exception {
        Account account = accounts.saveAndFlush(new Account(1L, 1_000L));
        Long originalVersion = jdbc.queryForObject("SELECT version FROM account WHERE id = ?", Long.class, account.getId());
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Throwable> failures = concurrent(2, () ->
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    Account loaded = accounts.findById(account.getId()).orElseThrow();
                    awaitBarrier(barrier);
                    loaded.deposit(100L);
                }));
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        Account result = accounts.findById(account.getId()).orElseThrow();
        assertThat(result.getBalance()).isEqualTo(1_100L);
        assertThat(jdbc.queryForObject("SELECT version FROM account WHERE id = ?", Long.class, account.getId()))
                .isEqualTo(originalVersion + 1);
    }

    @Test
    void constraintFailureRollsBackBalancesAndTransferStatus() {
        Account from = accounts.saveAndFlush(new Account(1L, 1_000L));
        Account to = accounts.saveAndFlush(new Account(2L, 0L));
        transferLedgers.saveAndFlush(new TransferLedger("rollback", from.getId(), to.getId(), TransferStatus.PENDING, 100L));
        accountLedgers.saveAndFlush(new AccountLedger("rollback", from.getId(), EntryType.DEBIT, 100L));

        assertThatThrownBy(() -> service.processTransfer(new TransferEvent("rollback", from.getId(), to.getId(),
                100L, TransferStatus.PENDING, LocalDateTime.now())))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(accounts.findById(from.getId()).orElseThrow().getBalance()).isEqualTo(1_000L);
        assertThat(accounts.findById(to.getId()).orElseThrow().getBalance()).isZero();
        assertThat(transferLedgers.findById("rollback").orElseThrow().getStatus()).isEqualTo(TransferStatus.PENDING);
        assertThat(accountLedgers.count()).isEqualTo(1);
    }

    @Test
    void repeatedSuccessfulTransferDoesNotApplyBalancesOrAccountLedgersTwice() {
        Account from = accounts.saveAndFlush(new Account(1L, 1_000L));
        Account to = accounts.saveAndFlush(new Account(2L, 0L));
        transferLedgers.saveAndFlush(new TransferLedger("idempotent", from.getId(), to.getId(), TransferStatus.PENDING, 100L));
        TransferEvent event = new TransferEvent("idempotent", from.getId(), to.getId(), 100L,
                TransferStatus.PENDING, LocalDateTime.now());

        service.processTransfer(event);
        service.processTransfer(event);

        assertThat(accounts.findById(from.getId()).orElseThrow().getBalance()).isEqualTo(900L);
        assertThat(accounts.findById(to.getId()).orElseThrow().getBalance()).isEqualTo(100L);
        assertThat(transferLedgers.findById("idempotent").orElseThrow().getStatus()).isEqualTo(TransferStatus.SUCCESS);
        assertThat(accountLedgers.count()).isEqualTo(2);
    }

    private KafkaConsumer<String, String> consumerAtEnd() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BROKERS);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "partition-it-" + UUID.randomUUID());
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config);
        try {
            assertThat(consumer.partitionsFor("transfer-topic", Duration.ofSeconds(15))).hasSize(3);
            List<TopicPartition> partitions = List.of(new TopicPartition("transfer-topic", 0),
                    new TopicPartition("transfer-topic", 1), new TopicPartition("transfer-topic", 2));
            consumer.assign(partitions);
            consumer.seekToEnd(partitions);
            partitions.forEach(partition -> consumer.position(partition, Duration.ofSeconds(15)));
            return consumer;
        } catch (RuntimeException | AssertionError failure) {
            consumer.close();
            throw failure;
        }
    }

    private List<ConsumerRecord<String, String>> readRecords(KafkaConsumer<String, String> consumer, int count) {
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (records.size() < count && System.nanoTime() < deadline) {
            consumer.poll(Duration.ofMillis(250)).forEach(records::add);
        }
        assertThat(records).hasSize(count);
        return records;
    }

    private void assertRecords(List<ConsumerRecord<String, String>> records, Map<String, String> payloads,
                               Map<String, Integer> partitionsByAccount) throws Exception {
        Set<String> transferIds = new HashSet<>();
        for (ConsumerRecord<String, String> record : records) {
            TransferEvent event = mapper.readValue(record.value(), TransferEvent.class);
            assertThat(transferIds.add(event.transferId())).isTrue();
            assertThat(record.value()).isEqualTo(payloads.get(event.transferId()));
            assertThat(record.key()).isEqualTo(String.valueOf(event.fromAccountId()));
            Integer previousPartition = partitionsByAccount.putIfAbsent(record.key(), record.partition());
            if (previousPartition != null) {
                assertThat(record.partition()).isEqualTo(previousPartition);
            }
        }
        assertThat(transferIds).containsExactlyInAnyOrderElementsOf(payloads.keySet());
    }

    private void awaitSent(long count) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            if (outboxes.findByStatus(OutboxStatus.SENT).size() == count) {
                return;
            }
            Thread.sleep(50);
        }
        assertThat(outboxes.findByStatus(OutboxStatus.SENT)).hasSize((int) count);
    }

    private static void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new IllegalStateException("Concurrent test did not reach barrier", failure);
        }
    }

    private static List<Throwable> concurrent(int count, Runnable action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                tasks.add(executor.submit(action));
            }
            List<Throwable> failures = new ArrayList<>();
            for (Future<?> task : tasks) {
                try {
                    task.get(20, TimeUnit.SECONDS);
                } catch (ExecutionException failure) {
                    failures.add(failure.getCause());
                }
            }
            return failures;
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
