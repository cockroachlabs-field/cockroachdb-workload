package io.cockroachdb.workload.ledger.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import io.cockroachdb.workload.Profiles;
import io.cockroachdb.workload.ledger.model.Transaction;

@Profiles.Ledger
@Repository
public class JpaTransactionRepositoryImpl implements TransactionRepository {
    @Autowired
    private TransactionJpaRepository transactionRepository;

    @Autowired
    private TransactionItemJpaRepository transactionItemRepository;

    @Override
    public Transaction createTransaction(Transaction transaction) {
        transactionItemRepository.saveAll(transaction.getItems());
        return transactionRepository.save(transaction);
    }
}
