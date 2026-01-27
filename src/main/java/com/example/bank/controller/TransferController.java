package com.example.bank.controller;

import com.example.bank.domain.Account;
import com.example.bank.repository.AccountRepository;
import com.example.bank.service.TransferService;
import org.springframework.web.bind.annotation.*;

@RestController
public class TransferController {
    private final TransferService transferService;

    public TransferController() {
        this.transferService =
                new TransferService(new com.example.bank.repository.AccountRepository());
    }

    @PostMapping("/transfer")
    public String transfer(@RequestParam Long fromId,
                           @RequestParam Long toId,
                           @RequestParam int amount) {
        transferService.transfer(fromId, toId, amount);
        return "이체 완료";
    }

}

