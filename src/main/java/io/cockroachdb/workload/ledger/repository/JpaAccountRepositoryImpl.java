package io.cockroachdb.workload.ledger.repository;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

import io.cockroachdb.workload.Profiles;
import io.cockroachdb.workload.common.aspect.NotTransactional;
import io.cockroachdb.workload.common.aspect.TransactionBoundary;
import io.cockroachdb.workload.common.util.Money;
import io.cockroachdb.workload.ledger.model.Account;
import io.cockroachdb.workload.ledger.model.AccountSummary;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Profiles.Ledger
@Repository
public class JpaAccountRepositoryImpl implements AccountRepository {
    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private AccountJpaRepository accountRepository;

    @Override
    @NotTransactional
    public Money getBalance(UUID id) {
        return accountRepository.findBalanceById(id);
    }

    @Override
    @NotTransactional
    public Money getBalanceSnapshot(UUID id) {
        return accountRepository.findBalanceSnapshotById(id);
    }

    @Override
    @TransactionBoundary
    public void createAccounts(int numAccounts, Supplier<Account> accountSupplier) {
        Assert.isTrue(TransactionSynchronizationManager.isActualTransactionActive(), "TX not active");

        Session session = entityManager.unwrap(Session.class);
        session.setJdbcBatchSize(numAccounts);

        IntStream.range(0, numAccounts).forEach(
                i -> accountRepository.save(accountSupplier.get()));

        accountRepository.flush();
    }

    @Override
    public void updateBalances(List<Account> accounts) {
        // No-op, expect batch updates via transparent persistence
    }

    @Override
    public List<Account> findAccountsForUpdate(Set<UUID> ids) {
        return accountRepository.findAllForUpdate(new HashSet<>(ids));
    }

    @Override
    public List<Account> findAccountsByRegion(String region, int offset, int limit) {
        return entityManager.createQuery("SELECT a FROM Account a WHERE a.region=?1",
                        Account.class)
                .setParameter(1, region)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    public List<Currency> getCurrencies() {
        return entityManager.createQuery(
                        "SELECT distinct a.balance.currency FROM Account a", Currency.class)
                .getResultList();
    }

    @Override
    public Money getTotalBalance(Currency currency) {
        BigDecimal balance = entityManager.createQuery(
                        "SELECT sum(a.balance.amount) FROM Account a where a.balance.currency=?1", BigDecimal.class)
                .setParameter(1, currency)
                .getSingleResult();
        return Money.of(balance, currency);
    }

    @Override
    public AccountSummary accountSummary(String region) {
        return new AccountSummary();
    }
}
