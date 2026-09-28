package com.example.bank.domain;

import com.example.bank.exception.InsufficientBalanceException;
import jakarta.persistence.*;

@Entity
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    private Long balance;

    // 낙관적 락을 위한 버전 정보
    @Version
    private Long version = 0L;

    protected Account() {
    }

    public Account(Long userId, Long balance) {
        if (userId == null) {
            throw new IllegalArgumentException("userId는 필수입니다");
        }

        if (balance == null || balance < 0) {
            throw new IllegalArgumentException("잔액은 0원 이상이어야 합니다");
        }

        this.userId = userId;
        this.balance = balance;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getBalance() {
        return balance;
    }

    public void withdraw(Long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("인출액은 0원 이상");
        }

        if (balance < amount) {
            throw new InsufficientBalanceException("잔액부족");
        }

        balance -= amount;
    }

    public void deposit(Long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("입금액은 0원 이상");
        }

        this.balance += amount;
    }
}