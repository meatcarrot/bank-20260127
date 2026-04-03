package com.example.bank.exception;

// 비지니스 로직상 허용하는 예외
public class BusinessException extends RuntimeException{
    public BusinessException (String message) {
        super(message);
    }
}
