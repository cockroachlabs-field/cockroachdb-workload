package io.cockroachdb.workload.ledger.service;

import java.util.Currency;
import java.util.List;
import java.util.UUID;

import io.cockroachdb.workload.common.util.Money;
import io.cockroachdb.workload.ledger.model.Account;
import io.cockroachdb.workload.ledger.model.AccountSummary;

public interface AccountService {
    int createAccounts(String region,
                       Money initialBalance,
                       int numAccounts,
                       int batchSize);

    List<Account> findAccountsByRegion(String region, int offset, int limit);

    Money getBalance(UUID id);

    Money getBalanceSnapshot(UUID id);

    List<Currency> getCurrencies();

    Money getTotalBalance(Currency currency);

    AccountSummary accountSummary(String region);
}
