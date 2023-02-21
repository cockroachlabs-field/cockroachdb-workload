package io.cockroachdb.workload.ledger.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import io.cockroachdb.workload.Profiles;
import io.cockroachdb.workload.ledger.model.Transaction;

@Profiles.Ledger
public interface TransactionJpaRepository extends JpaRepository<Transaction, UUID>,
        JpaSpecificationExecutor<Transaction> {
}
