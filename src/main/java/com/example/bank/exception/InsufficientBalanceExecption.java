package com.example.bank.exception;

// 계좌 잔고 부족 예외
public class InsufficientBalanceExecption extends BuissnessException {
    public InsufficientBalanceExecption(String message) {
        super(message);
    }
}
