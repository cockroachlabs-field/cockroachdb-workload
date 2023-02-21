package io.cockroachdb.workload.ledger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import io.cockroachdb.workload.common.command.AbstractCommand;
import io.cockroachdb.workload.ledger.repository.JdbcMetadataRepository;
import io.cockroachdb.workload.ledger.service.AccountService;
import io.cockroachdb.workload.ledger.service.TransferService;

public abstract class AbstractLedgerWorkload extends AbstractCommand {
    @Autowired
    @Qualifier("jpaAccountService")
    private AccountService jpaAccountService;

    @Autowired
    @Qualifier("jdbcAccountService")
    private AccountService jdbcAccountService;

    @Autowired
    @Qualifier("jpaTransferService")
    private TransferService jpaTransactionService;

    @Autowired
    @Qualifier("jdbcTransferService")
    private TransferService jdbcTransactionService;

    @Autowired
    private JdbcMetadataRepository jdbcMetadataRepositoryImpl;

    protected AccountService getAccountService(String api) {
        return "jdbc".equals(api) ? this.jdbcAccountService : this.jpaAccountService;
    }

    protected TransferService getTransactionService(String api) {
        return "jdbc".equals(api) ? this.jdbcTransactionService : this.jpaTransactionService;
    }

    protected List<String> resolveRegions(String regions) {
        List<String> available = jdbcMetadataRepositoryImpl.getRegions();

        List<String> resolved = new ArrayList<>();
        if ("all".equals(regions)) {
            resolved.addAll(available);
        } else if ("gateway".equals(regions)) {
            resolved.add(jdbcMetadataRepositoryImpl.getGatewayRegion());
        } else {
            Arrays.stream(regions.split(",")).forEach(r -> {
                if (available.contains(r)) {
                    resolved.add(r);
                } else {
                    throw new IllegalArgumentException("No such region [" + r + "] in " + available);
                }
            });
        }

        return resolved;
    }
}
