package com.example.bank;

import com.example.bank.domain.Account;
import com.example.bank.account.AccountRepository;
import com.example.bank.transfer.*;
import com.example.bank.ledger.AccountLedgerRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 100번 동시 송금 테스트
 *
 * 목적: 1번 계좌 → 2번 계좌로 100원씩 100번 동시 송금
 * 검증:
 * - 1번 잔액: 10,000 → 0
 * - 2번 잔액: 0 → 10,000
 * - 낙관적 락 충돌 발생 확인
 * - 재시도로 모두 성공 확인
 */
@Slf4j
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false"  // Kafka 비활성화
})
@ActiveProfiles("test")
class TransferConcurrency100Test {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferLedgerRepository transferLedgerRepository;

    @Autowired
    private AccountLedgerRepository accountLedgerRepository;

    @Autowired
    private TransferService transferService;

    @Autowired
    private TransferFacade transferFacade;

    private Long fromAccountId;
    private Long toAccountId;

    @BeforeEach
    void setUp() {
        // 기존 데이터 모두 삭제
        accountLedgerRepository.deleteAll();
        transferLedgerRepository.deleteAll();
        accountRepository.deleteAll();

        // 1번 계좌: 10,000원
        Account from = new Account(10000L);
        Account savedFrom = accountRepository.save(from);
        fromAccountId = savedFrom.getId();

        // 2번 계좌: 0원
        Account to = new Account(0L);
        Account savedTo = accountRepository.save(to);
        toAccountId = savedTo.getId();

        log.info("테스트 준비 완료 - 1번 계좌: {}원, 2번 계좌: {}원", 10000, 0);
    }

    @Test
    void 백명이_동시에_백원씩_송금하면_낙관적락이_발생하지만_재시도로_모두_성공한다() throws Exception {
        // Given
        int threadCount = 100;
        int amountPerTransfer = 100;  // 100원씩

        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger optimisticLockFailCount = new AtomicInteger(0);
        ConcurrentHashMap<Integer, Exception> failures = new ConcurrentHashMap<>();

        // When: 100개 스레드가 동시에 송금
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    // TransferEvent 생성 (실제 송금 로직 시뮬레이션)
                    String transferId = "tx-" + index;

                    // 1. TransferLedger 먼저 저장 (실제 구조 모방)
                    TransferLedger ledger = new TransferLedger(
                            transferId,
                            fromAccountId,
                            toAccountId,
                            TransferStatus.PENDING,
                            (long) amountPerTransfer
                    );
                    transferLedgerRepository.save(ledger);

                    // 2. TransferEvent 생성
                    TransferEvent event = new TransferEvent(
                            transferId,
                            fromAccountId,
                            toAccountId,
                            (long) amountPerTransfer,
                            TransferStatus.PENDING,
                            LocalDateTime.now()
                    );

                    // 3. Facade를 통해 재시도 포함 처리
                    transferFacade.processTransferWithRetry(event);

                    successCount.incrementAndGet();

                } catch (ObjectOptimisticLockingFailureException e) {
                    optimisticLockFailCount.incrementAndGet();
                    failures.put(index, e);
                    log.error("낙관적 락 실패 (재시도 초과): {}", index);
                } catch (Exception e) {
                    failures.put(index, e);
                    log.error("송금 실패: {}", index, e);
                } finally {
                    latch.countDown();
                }
            });
        }

        // Then: 모든 스레드 완료 대기
        boolean finished = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();

        // 결과 조회
        Account fromAccount = accountRepository.findById(fromAccountId).orElseThrow();
        Account toAccount = accountRepository.findById(toAccountId).orElseThrow();

        long totalSuccess = transferLedgerRepository.count();

        // 결과 출력
        log.info("========== 테스트 결과 ==========");
        log.info("총 시도: {}회", threadCount);
        log.info("성공: {}회", successCount.get());
        log.info("낙관적 락 충돌(재시도 초과): {}회", optimisticLockFailCount.get());
        log.info("기타 실패: {}회", failures.size() - optimisticLockFailCount.get());
        log.info("TransferLedger 총 건수: {}건", totalSuccess);
        log.info("1번 계좌 최종 잔액: {}원 (예상: 0원)", fromAccount.getBalance());
        log.info("2번 계좌 최종 잔액: {}원 (예상: 10,000원)", toAccount.getBalance());
        log.info("==============================");

        // 검증
        assertThat(successCount.get()).isEqualTo(100);  // 100건 모두 성공
        assertThat(fromAccount.getBalance()).isEqualTo(0L);  // 1번: 10,000 - 10,000 = 0
        assertThat(toAccount.getBalance()).isEqualTo(10000L);  // 2번: 0 + 10,000 = 10,000
        assertThat(totalSuccess).isEqualTo(100L);  // TransferLedger 100건
    }

    @Test
    void 동시성_테스트_간단_버전() throws Exception {
        // Given
        int threadCount = 10;  // 작게 시작
        int amountPerTransfer = 1000;

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);

        // When
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    String transferId = "simple-tx-" + index;

                    TransferLedger ledger = new TransferLedger(
                            transferId,
                            fromAccountId,
                            toAccountId,
                            TransferStatus.PENDING,
                            (long) amountPerTransfer
                    );
                    transferLedgerRepository.save(ledger);

                    TransferEvent event = new TransferEvent(
                            transferId,
                            fromAccountId,
                            toAccountId,
                            (long) amountPerTransfer,
                            TransferStatus.PENDING,
                            LocalDateTime.now()
                    );

                    transferFacade.processTransferWithRetry(event);
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    log.error("실패: {}", index, e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Then
        Account fromAccount = accountRepository.findById(fromAccountId).orElseThrow();
        Account toAccount = accountRepository.findById(toAccountId).orElseThrow();

        log.info("간단 테스트 결과:");
        log.info("성공: {}회", successCount.get());
        log.info("1번 잔액: {}원", fromAccount.getBalance());
        log.info("2번 잔액: {}원", toAccount.getBalance());

        assertThat(successCount.get()).isEqualTo(10);
        assertThat(fromAccount.getBalance()).isEqualTo(0L);
        assertThat(toAccount.getBalance()).isEqualTo(10000L);
    }
}