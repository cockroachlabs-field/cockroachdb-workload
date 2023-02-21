package io.cockroachdb.workload.ledger;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.springframework.shell.standard.ShellCommandGroup;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import io.cockroachdb.workload.Profiles;
import io.cockroachdb.workload.common.util.CockroachFacts;
import io.cockroachdb.workload.common.util.DurationFormat;
import io.cockroachdb.workload.common.util.Money;
import io.cockroachdb.workload.common.util.RandomData;
import io.cockroachdb.workload.ledger.model.Account;
import io.cockroachdb.workload.ledger.model.TransferRequest;
import io.cockroachdb.workload.ledger.service.AccountService;
import io.cockroachdb.workload.ledger.service.BadRequestException;
import io.cockroachdb.workload.ledger.service.TransferService;

@ShellComponent
@ShellCommandGroup("Workload")
@Profiles.Ledger
public class TransferWorkload extends AbstractLedgerWorkload {
    @ShellMethod(value = "Run ledger transfer workload")
    public void transfer(
            @ShellOption(help = "number of threads per region", defaultValue = "1") int threads,
            @ShellOption(help = "use JPA over JDBC (default)", defaultValue = "false") boolean jpa,
            @ShellOption(help = "account regions to use (all|gateway|<any>)", defaultValue = "all") String regions,
            @ShellOption(help = "max number of accounts per region", defaultValue = "5000") int limit,
            @ShellOption(help = "number of account legs per region (multiple of 2)", defaultValue = "2") int legs,
            @ShellOption(help = "execution duration", defaultValue = "45m") String duration
    ) {
        if (legs % 2 != 0) {
            throw new BadRequestException("Accounts per region must be a multiple of 2: " + legs);
        }

        final TransferService transactionService = getTransactionService(jpa ? "jpa" : "jdbc");
        final AccountService accountService = getAccountService(jpa ? "jpa" : "jdbc");
        final Duration runtimeDuration = DurationFormat.parseDuration(duration);
        final List<String> resolvedRegions = resolveRegions(regions);
        final String firstRegion = resolvedRegions.iterator().next();

        getConsole().infof("Resolved %d regions [%s]", resolvedRegions.size(), resolvedRegions);

        resolvedRegions
                .forEach(region -> {
                    final List<Account> regionAccounts = Collections
                            .unmodifiableList(accountService.findAccountsByRegion(region, 0, limit));
                    if (regionAccounts.isEmpty()) {
                        getConsole().infof("No accounts found!\n");
                        return;
                    }

                    getConsole().infof("Region %s with %d accounts", region, regionAccounts.size());

                    final Runnable unitOfWork = () -> {
                        TransferRequest.Builder requestBuilder = TransferRequest.builder()
                                .withId(UUID.randomUUID())
                                .withRegion(firstRegion)
                                .withTransactionType("ABC")
                                .withBookingDate(LocalDate.now())
                                .withTransferDate(LocalDate.now());

                        final List<Account> temporalAccounts = new ArrayList<>(regionAccounts);
                        final Currency currency = temporalAccounts.get(0).getBalance().getCurrency();
                        final Money transferAmount = RandomData.randomMoneyBetween("1.00", "10.00", currency);

                        IntStream.range(0, legs).forEach(value -> {
                            final boolean debit = value % 2 == 0;

                            // Debits gravitate towards accounts with the highest balance
                            Account account = debit
                                    ? RandomData.selectRandomWeighted(temporalAccounts)
                                    : RandomData.selectRandom(temporalAccounts);
                            temporalAccounts.remove(account);

                            final Money amount = debit ? transferAmount.negate() : transferAmount;

                            requestBuilder
                                    .addLeg()
                                    .withIdAndRegion(account.getId(), account.getRegion())
                                    .withAmount(amount)
                                    .withNote(CockroachFacts.nextFact())
                                    .then();
                        });

                        transactionService.processTransferRequest(requestBuilder.build());
                    };

                    IntStream.rangeClosed(1, threads).forEach(value -> {
                        getConsole().infof("Starting thread %d for region %s", value, region);
                        getExecutorTemplate().submit(
                                "transfer writer #" + value + " (" + region + ")",
                                unitOfWork, runtimeDuration);
                    });
                });
    }
}
