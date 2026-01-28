package com.example.bank.domain;


public class Account {
    private Long id;
    private int balance;

    public Account(long id, int balance) {
        this.id = id;
        this.balance = balance;
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
        balance += amount;
    }

    public int getBalance() {
        return balance;
    }

    public Long getId() {
        return id;
    }
}
