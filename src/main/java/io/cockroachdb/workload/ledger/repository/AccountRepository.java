package io.cockroachdb.workload.ledger.repository;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import io.cockroachdb.workload.common.util.Money;
import io.cockroachdb.workload.ledger.model.Account;
import io.cockroachdb.workload.ledger.model.AccountSummary;

public interface AccountRepository {
    void createAccounts(int numAccounts, Supplier<Account> accountSupplier);

    Money getBalance(UUID id);

    Money getBalanceSnapshot(UUID id);

    List<Currency> getCurrencies();

    Money getTotalBalance(Currency currency);

    List<Account> findAccountsByRegion(String region, int offset, int limit);

    List<Account> findAccountsForUpdate(Set<UUID> ids);

    void updateBalances(List<Account> accounts);

    AccountSummary accountSummary(String region);
}
