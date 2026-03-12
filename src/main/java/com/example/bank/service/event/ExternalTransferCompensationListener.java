package com.example.bank.service.event;

import com.example.bank.event.ExternalDepositFailedEvent;
import com.example.bank.service.ExternalTransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExternalTransferCompensationListener {

    private final ExternalTransferService externalTransferService;

    @EventListener
    public void handleExternalDepositFailed(ExternalDepositFailedEvent event) {
        log.info("외부 입금 실패 이벤트 수신: transferId={}, reason={}",
                event.transferId(), event.reason());

        externalTransferService.compensateWithdraw(event.transferId());
    }
}