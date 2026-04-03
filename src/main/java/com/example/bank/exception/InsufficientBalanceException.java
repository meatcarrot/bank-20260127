package com.example.bank.exception;

// 계좌 잔고 부족 예외
public class InsufficientBalanceException extends BusinessException {
    public InsufficientBalanceException(String message) {
        super(message);
    }
}
