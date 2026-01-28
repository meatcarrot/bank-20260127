    package com.example.bank.repository;

    import com.example.bank.domain.Account;
    import org.springframework.stereotype.Repository;

    import java.util.HashMap;
    import java.util.Map;

    @Repository
    public class AccountRepository {
        private final Map<Long, Account> store = new HashMap<>();

        public AccountRepository() {
            store.put(1L, new Account(1L, 100000));
            store.put(2L, new Account(2L, 50000));
        }

        public Account findById(Long id) {
            return store.get(id);
        }

    }
