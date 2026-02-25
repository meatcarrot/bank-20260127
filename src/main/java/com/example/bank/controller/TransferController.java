package com.example.bank.controller;

import com.example.bank.service.TransferService;
import com.example.bank.service.TransferService.SystemException;
import org.springframework.web.bind.annotation.*;

@RestController
public class TransferController {
    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/transfer")
    public String transfer(@RequestParam Long fromId,
                           @RequestParam Long toId,
                           @RequestParam int amount) throws SystemException {
        transferService.requestTransfer(fromId, toId, amount);
        return "이체 완료";
    }

}

