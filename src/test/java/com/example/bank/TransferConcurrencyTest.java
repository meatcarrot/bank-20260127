package com.example.bank;

import com.example.bank.domain.Account;
import com.example.bank.repository.AccountRepository;
import com.example.bank.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest//(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class TransferConcurrencyTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void setUp(){
        // 1번 계좌: 10,000원
        Account from = accountRepository.findById(1L).orElseThrow();
        from.deposit(10_000);
        accountRepository.save(from);

        // 2번 계좌: 0원
        Account to = accountRepository.findById(2L).orElseThrow();
        to.deposit(0);
        accountRepository.save(to);
    }

    @Test
    void People100Transfer() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++){
            executorService.submit(() -> {
                try {
                    transferService.transfer(1L, 2L, 100);
                } catch (Exception e) {
                    System.out.println("송금 실패: " + e.getMessage());
                }
                finally {
                    latch.countDown();
                }
            });
        }

        // 100개 스레드 종료 대기
        latch.await();
        executorService.shutdown();

        Account from = accountRepository.findById(1L).orElseThrow();
        Account to = accountRepository.findById(2L).orElseThrow();

        System.out.println("최종 1번 계좌 잔액: " + from.getBalance());
        System.out.println("최종 2번 계좌 잔액: " + to.getBalance());

    }






}
