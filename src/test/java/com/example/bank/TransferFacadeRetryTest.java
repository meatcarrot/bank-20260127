package com.example.bank;

import com.example.bank.domain.Account;
import com.example.bank.transfer.TransferEvent;
import com.example.bank.transfer.TransferFacade;
import com.example.bank.transfer.TransferService;
import com.example.bank.transfer.TransferStatus;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferFacadeRetryTest {

    @Mock
    private TransferService transferService;

    @InjectMocks
    private TransferFacade transferFacade;

    @Test
    void test_retry() {
        TransferEvent event = new TransferEvent(
                "tx-1",
                1L,
                3L,
                1000L,
                TransferStatus.PENDING,
                LocalDateTime.now()
        );

        doThrow(new ObjectOptimisticLockingFailureException(Account.class, 3L))
                .doThrow(new OptimisticLockException("optimistic lock"))
                .doNothing()
                .when(transferService).processTransfer(event);

        transferFacade.processTransferWithRetry(event);

        verify(transferService, times(3)).processTransfer(event);
    }
}