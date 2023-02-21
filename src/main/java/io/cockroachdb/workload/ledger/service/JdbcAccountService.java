package io.cockroachdb.workload.ledger.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import io.cockroachdb.workload.Profiles;
import io.cockroachdb.workload.ledger.repository.AccountRepository;

@Profiles.Ledger
@Repository
public class JdbcAccountService extends AbstractAccountService {
    @Autowired
    @Qualifier("jdbcAccountRepositoryImpl")
    private AccountRepository accountRepository;

    @Override
    protected AccountRepository getAccountRepository() {
        return accountRepository;
    }
}
