package io.cockroachdb.workload.ledger;

import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.shell.standard.ShellCommandGroup;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import io.cockroachdb.workload.Profiles;
import io.cockroachdb.workload.common.DatabasePopulator;
import io.cockroachdb.workload.common.command.Workload;
import io.cockroachdb.workload.common.util.Money;
import io.cockroachdb.workload.common.util.Multiplier;
import io.cockroachdb.workload.ledger.model.AccountSummary;
import io.cockroachdb.workload.ledger.repository.JdbcMetadataRepository;
import io.cockroachdb.workload.ledger.service.AccountService;

@ShellComponent
@ShellCommandGroup("Workload")
@Profiles.Ledger
public class LedgerWorkload extends AbstractLedgerWorkload implements Workload {
    @Autowired
    private JdbcMetadataRepository jdbcMetadataRepositoryImpl;

    @Override
    public String prompt() {
        return "ledger:$ ";
    }

    @ShellMethod(value = "Initialize ledger workload")
    public void init(
            @ShellOption(help = "initial balance", defaultValue = "100000.00") String initialBalance,
            @ShellOption(help = "account currency", defaultValue = "USD") String currency,
            @ShellOption(help = "number of accounts per region", defaultValue = "10k") String accounts,
            @ShellOption(help = "transaction size", defaultValue = "512") int transactionSize,
            @ShellOption(help = "batch size", defaultValue = "64") int batchSize,
            @ShellOption(help = "regions to use (all|gateway|<any>)", defaultValue = "all") String regions,
            @ShellOption(help = "use JPA over JDBC (default)", defaultValue = "false") boolean jpa,
            @ShellOption(help = "drop schema", defaultValue = "false") boolean drop,
            @ShellOption(help = "skip create schema", defaultValue = "false") boolean skip
    ) {
        if (drop) {
            getConsole().info("Dropping tables..");
            DatabasePopulator.executeScripts(getDataSource(), "db/ledger/drop-ledger.sql");
        }

        if (!skip) {
            getConsole().info("Creating tables..");
            DatabasePopulator.executeScripts(getDataSource(), "db/ledger/create-ledger.sql");
        }

        final AccountService accountService = getAccountService(jpa ? "jpa" : "jdbc");
        final int accountsPerRegion = Multiplier.parseInt(accounts);
        final List<String> resolvedRegions = resolveRegions(regions);

        getConsole().successf("Creating %,d accounts in %d regions %s",
                accountsPerRegion,
                resolvedRegions.size(),
                resolvedRegions);

        AtomicInteger total = new AtomicInteger();

        resolvedRegions.forEach(region -> {
            getConsole().successf("Creating %,d accounts in region %s", accountsPerRegion, region);
            int num = accountService.createAccounts(region,
                    Money.of(initialBalance, Currency.getInstance(currency)),
                    accountsPerRegion,
                    transactionSize,
                    batchSize);
            total.addAndGet(num);
        });

        getConsole().successf("Ready for business - %,d account(s) created in total", total.get());
        onPostInit();
    }

    @ShellMethod(value = "Report ledger balance sheet")
    public void report(@ShellOption(help = "use JPA over JDBC (default)", defaultValue = "false") boolean jpa) {
        AtomicInteger totalAccounts = new AtomicInteger();

        jdbcMetadataRepositoryImpl.getRegions().forEach(region -> {
            AccountSummary accountSummary = getAccountService("jdbc").accountSummary(region);

            getConsole().successf("Total balance for region %s", region);
            getConsole().infof("\tnumberOfAccounts: %s", accountSummary.getNumberOfAccounts());
            getConsole().infof("\ttotalBalance: %,.2f", accountSummary.getTotalBalance().doubleValue());
            getConsole().infof("\tminBalance: %,.2f", accountSummary.getMinBalance().doubleValue());
            getConsole().infof("\tmaxBalance: %,.2f", accountSummary.getMaxBalance().doubleValue());
            getConsole().infof("\tavgBalance: %,.2f", accountSummary.getAvgBalance().doubleValue());

            totalAccounts.addAndGet(accountSummary.getNumberOfAccounts());
        });

        AccountService accountService = getAccountService(jpa?"jpa":"jdbc");

        getConsole().successf("Total number of accounts: %,d", totalAccounts.get());
        accountService.getCurrencies().forEach(currency -> {
            getConsole().successf("Total balance for %s: %s", currency, accountService.getTotalBalance(currency));
        });
    }

    @ShellMethod(value = "List region names")
    public void regions() {
        jdbcMetadataRepositoryImpl.getRegions().forEach(region -> {
            getConsole().infof("%s", region);
        });
    }
}

