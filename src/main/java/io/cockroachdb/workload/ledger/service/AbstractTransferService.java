package io.cockroachdb.workload.ledger.service;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.util.Pair;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

import io.cockroachdb.workload.common.aspect.TransactionBoundary;
import io.cockroachdb.workload.common.util.Money;
import io.cockroachdb.workload.ledger.model.Account;
import io.cockroachdb.workload.ledger.model.Transaction;
import io.cockroachdb.workload.ledger.model.TransferRequest;
import io.cockroachdb.workload.ledger.repository.AccountRepository;
import io.cockroachdb.workload.ledger.repository.TransactionRepository;

public abstract class AbstractTransferService implements TransferService {
    protected abstract AccountRepository getAccountRepository();

    protected abstract TransactionRepository getTransactionRepository();

    @Override
    @TransactionBoundary
    public void processTransferRequest(TransferRequest request) {
        Assert.isTrue(TransactionSynchronizationManager.isActualTransactionActive(),
                "No transaction context - check Spring profile settings");

        if (request.getAccountLegs().size() < 2) {
            throw new BadRequestException("Must have at least two account legs");
        }

        // Coalesce multi-legged transactions
        final Map<UUID, Pair<Money, String>> legs = coalesce(request);

        // Lookup accounts with authoritative reads
        final List<Account> accounts = getAccountRepository().findAccountsForUpdate(legs.keySet());

        final Transaction.Builder transactionBuilder = Transaction.builder()
                .withId(request.getId())
                .withRegion(request.getRegion())
                .withTransferType(request.getTransactionType())
                .withBookingDate(request.getBookingDate())
                .withTransferDate(request.getTransferDate());

        legs.forEach((accountId, value) -> {
            final Money amount = value.getFirst();

            Account account = accounts.stream().filter(a -> Objects.equals(a.getId(), accountId))
                    .findFirst().orElseThrow(() -> new NoSuchAccountException(accountId.toString()));

            transactionBuilder
                    .andItem()
                    .withRegion(request.getRegion())
                    .withAccount(account)
                    .withRunningBalance(account.getBalance())
                    .withAmount(amount)
                    .withNote(value.getSecond())
                    .then();

            account.addAmount(amount);
        });

        getAccountRepository().updateBalances(accounts);
        getTransactionRepository().createTransaction(transactionBuilder.build());
    }

    private Map<UUID, Pair<Money, String>> coalesce(TransferRequest request) {
        final Map<UUID, Pair<Money, String>> legs = new HashMap<>();
        final Map<Currency, BigDecimal> amounts = new HashMap<>();

        // Compact accounts and verify that total balance for the legs with the same currency is zero
        request.getAccountLegs().forEach(leg -> {
            legs.compute(leg.getId(),
                    (key, amount) -> (amount == null)
                            ? Pair.of(leg.getAmount(), leg.getNote())
                            : Pair.of(amount.getFirst().plus(leg.getAmount()), leg.getNote()));
            amounts.compute(leg.getAmount().getCurrency(),
                    (currency, amount) -> (amount == null)
                            ? leg.getAmount().getAmount() : leg.getAmount().getAmount().add(amount));
        });

        // The sum of debits for all accounts must equal the corresponding sum of credits (per currency)
        amounts.forEach((key, value) -> {
            if (value.compareTo(BigDecimal.ZERO) != 0) {
                throw new BadRequestException("Unbalanced transaction: currency ["
                        + key + "], amount sum [" + value + "]");
            }
        });

        return legs;
    }
}
