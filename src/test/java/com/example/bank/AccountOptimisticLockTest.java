package com.example.bank;

import com.example.bank.account.AccountRepository;
import com.example.bank.domain.Account;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false"
})
@ActiveProfiles("test")
@Import(AccountOptimisticLockTest.TestConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Tag("integration")
class AccountOptimisticLockTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OptimisticLockProbeService probeService;

    private Long accountId;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();

        // 네 Account 생성 방식에 맞게 수정 필요할 수 있음
        Account account = new Account(10000L);
        Account saved = accountRepository.save(account);
        accountId = saved.getId();
    }

    @Test
    void 같은_계좌를_동시에_수정하면_한쪽은_낙관적락_예외가_난다() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        CountDownLatch done = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger(0);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        Runnable task = () -> {
            try {
                probeService.depositWithBarrier(accountId, 1000L, barrier);
                successCount.incrementAndGet();
            } catch (Throwable t) {
                failures.add(t);
            } finally {
                done.countDown();
            }
        };

        executor.submit(task);
        executor.submit(task);

        done.await();
        executor.shutdown();

        Account result = accountRepository.findById(accountId).orElseThrow();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0))
                .isInstanceOfAny(
                        ObjectOptimisticLockingFailureException.class,
                        OptimisticLockException.class
                );
        assertThat(result.getBalance()).isEqualTo(11000L);
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        OptimisticLockProbeService optimisticLockProbeService(AccountRepository accountRepository) {
            return new OptimisticLockProbeService(accountRepository);
        }
    }

    @RequiredArgsConstructor
    static class OptimisticLockProbeService {
        private final AccountRepository accountRepository;

        @Transactional
        public void depositWithBarrier(Long accountId, Long amount, CyclicBarrier barrier) throws Exception {
            Account account = accountRepository.findById(accountId).orElseThrow();

            // 둘 다 같은 version을 읽은 뒤 동시에 수정하게 유도
            barrier.await();

            account.deposit(amount);
        }
    }
}