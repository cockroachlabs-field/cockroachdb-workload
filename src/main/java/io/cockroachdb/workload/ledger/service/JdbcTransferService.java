package io.cockroachdb.workload.ledger.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import io.cockroachdb.workload.Profiles;
import io.cockroachdb.workload.ledger.repository.AccountRepository;
import io.cockroachdb.workload.ledger.repository.TransactionRepository;

@Profiles.Ledger
@Service
public class JdbcTransferService extends AbstractTransferService {
    @Autowired
    @Qualifier("jdbcAccountRepositoryImpl")
    private AccountRepository accountRepository;

    @Autowired
    @Qualifier("jdbcTransactionRepositoryImpl")
    private TransactionRepository transactionRepository;

    @Override
    protected AccountRepository getAccountRepository() {
        return accountRepository;
    }

    @Override
    protected TransactionRepository getTransactionRepository() {
        return transactionRepository;
    }
}
