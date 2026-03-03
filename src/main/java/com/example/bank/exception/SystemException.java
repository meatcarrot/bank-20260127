package com.example.bank.exception;

// checked exception임. 이건 트랜젝션이 되지 않는다
public class SystemException extends RuntimeException {
    public SystemException (String message) {
        super(message);
    }
}
