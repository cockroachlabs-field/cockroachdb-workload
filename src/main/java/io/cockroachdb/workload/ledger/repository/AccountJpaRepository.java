package io.cockroachdb.workload.ledger.repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import io.cockroachdb.workload.Profiles;
import io.cockroachdb.workload.common.util.Money;
import io.cockroachdb.workload.ledger.model.Account;
import jakarta.persistence.LockModeType;

@Profiles.Ledger
public interface AccountJpaRepository extends JpaRepository<Account, UUID> {
    @Query(value = "select a.balance "
            + "from Account a "
            + "where a.id = ?1")
    Money findBalanceById(UUID id);

    @Query(value = "select a.balance "
            + "from account a AS OF SYSTEM TIME follower_read_timestamp() "
            + "where a.id = ?1", nativeQuery = true)
    Money findBalanceSnapshotById(UUID id);

    @Query(value = "select a "
            + "from Account a "
            + "where a.id in (?1)")
    @Lock(LockModeType.PESSIMISTIC_READ)
    List<Account> findAllForUpdate(Set<UUID> ids);
}
