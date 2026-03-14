package com.example.bank.service.event;

import com.example.bank.transfer.TransferCompletedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Component
public class SmsNotificationListener {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendSms(TransferCompletedEvent event) {
        System.out.println("🔥 SMS EVENT START");
        System.out.println("스레드: " + Thread.currentThread().getName());
        System.out.println("문자 발송: " + event);
        System.out.println("🔥 SMS EVENT END");
    }
}
