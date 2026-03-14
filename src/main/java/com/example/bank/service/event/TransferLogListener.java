package com.example.bank.service.event;


import com.example.bank.transfer.TransferCompletedEvent;
import com.example.bank.service.LogService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TransferLogListener {

    private final LogService logService;

    public TransferLogListener(LogService logService){
        this.logService = logService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    //@Transactional(propagation = Propagation.REQUIRES_NEW)
    // 다른 스레드가 되니까 새로운 트랜젝션 열 필요 없다
    // 커넥션 낭비임
    public void handleTransferLog(TransferCompletedEvent event) {
        System.out.println("====송금 트랜젝션 완료 후 로그 기록 시작====");
        logService.logWithdraw();
    }
}
