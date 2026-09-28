package com.example.bank.controller;

import com.example.bank.DTO.TransferRequest;
import com.example.bank.exception.SystemException;
import com.example.bank.transfer.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @PostMapping("/transfer")
    public String transfer(
            @RequestBody @Valid TransferRequest request,
            Authentication authentication
    ) throws SystemException {

        Long loginUserId = (Long) authentication.getPrincipal();

        transferService.requestTransfer(
                loginUserId,
                request.fromId(),
                request.toId(),
                request.amount()
        );

        return "처리 완료";
    }
}