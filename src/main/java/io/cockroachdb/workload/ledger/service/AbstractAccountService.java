package io.cockroachdb.workload.ledger.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

import io.cockroachdb.workload.common.aspect.NotTransactional;
import io.cockroachdb.workload.common.aspect.TransactionBoundary;
import io.cockroachdb.workload.common.util.Money;
import io.cockroachdb.workload.common.util.RandomData;
import io.cockroachdb.workload.ledger.model.Account;
import io.cockroachdb.workload.ledger.model.AccountSummary;
import io.cockroachdb.workload.ledger.model.AccountType;
import io.cockroachdb.workload.ledger.repository.AccountRepository;

public abstract class AbstractAccountService implements AccountService {
    protected abstract AccountRepository getAccountRepository();

    @Override
    @NotTransactional
    public int createAccounts(String region,
                              Money initialBalance,
                              int numAccounts,
                              int batchSize) {
        Assert.isTrue(!TransactionSynchronizationManager.isActualTransactionActive(), "Transaction active");

        AtomicInteger counter = new AtomicInteger();

        Supplier<Account> accountSupplier = () -> Account.builder()
                .withId(UUID.randomUUID())
                .withRegion(region)
                .withName("user:" + counter.incrementAndGet())
                .withDescription(RandomData.randomLoreIpsum(5, 10, false))
                .withBalance(initialBalance)
                .withAccountType(AccountType.ASSET)
                .withInsertedAt(LocalDateTime.now())
                .withUpdatedAt(LocalDateTime.now())
                .withAllowNegative(false)
                .withClosed(false)
                .build();

        for (int i = 0; i < numAccounts; i += batchSize) {
            if (i + batchSize > numAccounts) {
                batchSize = numAccounts - i;
            }
            getAccountRepository().createAccounts(batchSize, accountSupplier);
        }

        return counter.get();
    }

    @Override
    @TransactionBoundary
    public List<Account> findAccountsByRegion(String region, int offset, int limit) {
        Assert.isTrue(TransactionSynchronizationManager.isActualTransactionActive(), "Transaction not active");
        return getAccountRepository().findAccountsByRegion(region, offset, limit);
    }

    @Override
    public Money getBalance(UUID id) {
        Assert.isTrue(!TransactionSynchronizationManager.isActualTransactionActive(), "Transaction active");
        return getAccountRepository().getBalance(id);
    }

    @Override
    public Money getBalanceSnapshot(UUID id) {
        Assert.isTrue(!TransactionSynchronizationManager.isActualTransactionActive(), "Transaction active");
        return getAccountRepository().getBalanceSnapshot(id);
    }

    @Override
    public List<Currency> getCurrencies() {
        return getAccountRepository().getCurrencies();
    }

    @Override
    @TransactionBoundary(followerRead = true)
    public Money getTotalBalance(Currency currency) {
        Assert.isTrue(TransactionSynchronizationManager.isActualTransactionActive(), "Transaction not active");
        return getAccountRepository().getTotalBalance(currency);
    }

    @Override
    @TransactionBoundary(followerRead = true)
    public AccountSummary accountSummary(String region) {
        Assert.isTrue(TransactionSynchronizationManager.isActualTransactionActive(), "Transaction not active");
        return getAccountRepository().accountSummary(region);
    }
}
