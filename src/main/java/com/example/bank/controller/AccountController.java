package com.example.bank.controller;

import com.example.bank.domain.Account;
import com.example.bank.account.AccountRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountRepository accountRepository;

    public AccountController(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @GetMapping("/{id}")
    public Account getAccount(@PathVariable Long id) {
        return accountRepository.findById(id)
                .orElseThrow(()-> new IllegalArgumentException("계좌 없음"));
    }
}
