package com.example.bank.entity;

public enum TransferStatus {
    PENDING,  // 송금 요청 들어왔지만 아직 처리 전
    SUCCESS,  // 송금 성공
    FAILED    // 송금 실패
}
