package com.example.bank.repository;

import com.example.bank.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface AccountRepository
    extends JpaRepository<Account, Long> {

    // 비관적 락
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdWithPessimisticLock(@Param("id") Long id);


    // 낙관적 락
    @Lock(LockModeType.OPTIMISTIC)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdWithOptimisticLock(@Param("id") Long id);

}



