package com.example.bank.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.DialectOverride;

@Entity
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long balance;

    //낙관적 락을 위한 버전 정보 추가
    @Version
    private Long version = 0L;

    // DB -> 객체로 복원함
    // 그래서 JPA는 기본 생성자가 꼭 필요
    // JPA를 위한 생성자는 열어두되, 외부에서 막 쓰는 건 막는다.
    // private는 JPA가 접근을 못함
    protected Account() {

    }

    public Account(Long balance) {
        this.balance = balance;
    }

    public Long getId(){
        return id;
    }

    public Long getBalance() {
        return balance;
    }

    public void withdraw(Long amount){
        if (amount <= 0){
            throw new IllegalArgumentException("인출액은 0원 이상");
        }
        if (balance < amount){
            throw new IllegalArgumentException("잔액부족");
        }
        balance -= amount;
    }

    public void deposit(Long amount){
        if (amount < 0){
            throw new IllegalArgumentException("입금액은 0원 이상");
        }
        this.balance += amount;
    }

}
