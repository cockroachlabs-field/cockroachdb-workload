package io.cockroachdb.workload.ledger.repository;

import io.cockroachdb.workload.ledger.model.Transaction;

public interface TransactionRepository {
    Transaction createTransaction(Transaction transaction);
}
