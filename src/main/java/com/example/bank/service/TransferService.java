package com.example.bank.service;

import com.example.bank.domain.Account;
import com.example.bank.repository.AccountRepository;
import org.springframework.stereotype.Service;

@Service
public class TransferService {

    private final AccountRepository accountRepository;

    public TransferService(AccountRepository accountRepository){
        this.accountRepository = accountRepository;
    }

    public void transfer(Long fromId, Long toId, int amount) {
        Account from = accountRepository.findById(fromId);
        Account to = accountRepository.findById(toId);

        from.withdraw(amount);
        to.deposit(amount);
    }
}
