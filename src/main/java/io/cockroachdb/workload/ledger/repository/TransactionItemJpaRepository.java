package io.cockroachdb.workload.ledger.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import io.cockroachdb.workload.Profiles;
import io.cockroachdb.workload.ledger.model.TransactionItem;

@Profiles.Ledger
public interface TransactionItemJpaRepository extends JpaRepository<TransactionItem, TransactionItem.Id>,
        JpaSpecificationExecutor<TransactionItem> {
}
