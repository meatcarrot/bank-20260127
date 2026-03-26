package com.example.bank.DTO;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;


public record TransferRequest(
        @NotNull Long fromId,
        @NotNull Long toId,
        @NotNull @Positive Long amount
) {}
