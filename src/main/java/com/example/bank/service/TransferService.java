package com.example.bank.service;

import com.example.bank.domain.Account;
import com.example.bank.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TransferService {

    private final AccountRepository accountRepository;

    public TransferService(AccountRepository accountRepository){
        this.accountRepository = accountRepository;
    }


    public void transfer(Long fromId, Long toId, int amount) {
        Account from = accountRepository.findById(fromId)
                .orElseThrow(()-> new IllegalArgumentException("계좌 없음"));
        Account to = accountRepository.findById(toId)
                .orElseThrow(()-> new IllegalArgumentException(("계좌 없음")));

        from.withdraw(amount);
        if(true) {
            throw new RuntimeException(("강제 실패"));
        }
        to.deposit(amount);
    }
}
