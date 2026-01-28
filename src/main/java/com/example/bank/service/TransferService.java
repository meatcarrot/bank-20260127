package com.example.bank.service;

import com.example.bank.domain.Account;
import com.example.bank.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
//rollbackFor을 통해서 모든 exception이 다 트랜젝션이 되도록 하는게 일반적
@Transactional(rollbackFor = Exception.class)
public class TransferService {

    private final AccountRepository accountRepository;

    public TransferService(AccountRepository accountRepository){
        this.accountRepository = accountRepository;
    }

//    @Transactional
//    public void transfer(Long fromId, Long toId, int amount) {
//        Account from = accountRepository.findById(fromId)
//                .orElseThrow(()-> new IllegalArgumentException("계좌 없음"));
//        Account to = accountRepository.findById(toId)
//                .orElseThrow(()-> new IllegalArgumentException(("계좌 없음")));
//
//        from.withdraw(amount);
//        if(true) {
//            throw new RuntimeException(("강제 실패"));
//        }
//        to.deposit(amount);
//    }

    public void transfer(Long fromId, Long toId, int amount) throws TransferFailException {
        Account from = accountRepository.findById(fromId)
                .orElseThrow(()-> new IllegalArgumentException("계좌 없음"));
        Account to = accountRepository.findById(toId)
                .orElseThrow(()-> new IllegalArgumentException(("계좌 없음")));

        from.withdraw(amount);   // 출금
        throw new TransferFailException("체크 예외 실패");
        // deposit(toId, amount);
    }

    // checked exception임. 이건 트랜젝션이 되지 않는다
    public class TransferFailException extends Exception {
        public TransferFailException(String message) {
            super(message);
        }
    }
}
