package com.example.bank.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int balance;

    protected Account() {

    }
    public Account(int balance) {
        this.balance = balance;
    }

    public Long getId(){
        return id;
    }

    public int getBalance() {
        return balance;
    }

    public void withdraw(int amount){
        if (amount <= 0){
            throw new IllegalArgumentException("인출액은 0원 이상");
        }
        if (balance < amount){
            throw new IllegalArgumentException("잔액부족");
        }
        balance -= amount;
    }

    public void deposit(int amount){
        if (amount <= 0){
            throw new IllegalArgumentException("입금액은 0원 이상");
        }
        this.balance += amount;
    }

}
