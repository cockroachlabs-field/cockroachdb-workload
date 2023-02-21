package io.cockroachdb.workload.ledger;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.springframework.data.util.Pair;
import org.springframework.shell.standard.ShellCommandGroup;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import io.cockroachdb.workload.Profiles;
import io.cockroachdb.workload.common.util.DurationFormat;
import io.cockroachdb.workload.common.util.Money;
import io.cockroachdb.workload.common.util.RandomData;
import io.cockroachdb.workload.ledger.model.Account;
import io.cockroachdb.workload.ledger.service.AccountService;

@ShellComponent
@ShellCommandGroup("Workload")
@Profiles.Ledger
public class BalanceWorkload extends AbstractLedgerWorkload {
    @ShellMethod(value = "Run balance query workload (read-only)")
    public void balance(
            @ShellOption(help = "number of threads per region", defaultValue = "1") int threads,
            @ShellOption(help = "use JPA over JDBC (default)", defaultValue = "false") boolean jpa,
            @ShellOption(help = "account regions to use (all|gateway|<any>)", defaultValue = "all") String regions,
            @ShellOption(help = "max number of accounts per region", defaultValue = "5000") int limit,
            @ShellOption(help = "execution duration", defaultValue = "45m") String duration,
            @ShellOption(help = "use follower reads", defaultValue = "false") boolean followerReads
    ) {
        final Duration runtimeDuration = DurationFormat.parseDuration(duration);
        final AccountService accountService = getAccountService(jpa ? "jpa" : "jdbc");
        final List<String> resolvedRegions = resolveRegions(regions);

        getConsole().infof("Resolved %d regions [%s]", resolvedRegions.size(), resolvedRegions);

        resolvedRegions
                .forEach(region -> {
                    final List<Account> regionAccounts = Collections
                            .unmodifiableList(accountService.findAccountsByRegion(region, 0, limit));
                    if (regionAccounts.isEmpty()) {
                        getConsole().infof("No accounts found for region %s!", region);
                        return;
                    }

                    getConsole().infof("Region %s with %d accounts", region, regionAccounts.size());

                    final Runnable unitOfWork = () -> {
                        Account account = RandomData.selectRandom(regionAccounts);
                        Money balance;
                        if (followerReads) {
                            balance = accountService.getBalanceSnapshot(account.getId());
                        } else {
                            balance = accountService.getBalance(account.getId());
                        }

                        Pair<UUID, Money> pair = Pair.of(account.getId(), balance);
                        if (pair.getSecond().isNegative()) {
                            getConsole().warnf("OMG!! negative balance (%s) detected for account id %s",
                                    pair.getSecond(), pair.getFirst());
                        }
                    };

                    IntStream.rangeClosed(1, threads).forEach(value -> {
                        getConsole().infof("Starting thread %d for region %s", value, region);
                        getExecutorTemplate().submit(
                                "balance reader #" + value + " (" + region + ")",
                                unitOfWork, runtimeDuration);
                    });
                });
    }
}
