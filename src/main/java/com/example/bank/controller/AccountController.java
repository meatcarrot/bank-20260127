package com.example.bank.controller;

import com.example.bank.domain.Account;
import com.example.bank.repository.AccountRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountRepository accountRepository;

    public AccountController() {
        this.accountRepository = new AccountRepository();
    }

    @GetMapping("/{id}")
    public Account getAccount(@PathVariable Long id) {
        return accountRepository.findById(id);
    }
}
