package com.example.bank.controller;

import com.example.bank.DTO.TransferRequest;
import com.example.bank.transfer.TransferService;
import com.example.bank.exception.SystemException;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
public class TransferController {
    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/transfer")
    public String transfer(@RequestBody @Valid TransferRequest request) throws SystemException {
        transferService.requestTransfer(
                request.fromId(),
                request.toId(),
                request.amount()
        );
        return "처리 완료";
    }

}

