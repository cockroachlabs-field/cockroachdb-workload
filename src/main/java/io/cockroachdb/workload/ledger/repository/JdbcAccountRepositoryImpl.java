package io.cockroachdb.workload.ledger.repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

import io.cockroachdb.workload.Profiles;
import io.cockroachdb.workload.common.aspect.NotTransactional;
import io.cockroachdb.workload.common.util.Money;
import io.cockroachdb.workload.ledger.model.Account;
import io.cockroachdb.workload.ledger.model.AccountSummary;
import io.cockroachdb.workload.ledger.model.AccountType;

@Profiles.Ledger
@Repository
public class JdbcAccountRepositoryImpl implements AccountRepository {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    @NotTransactional // Use implicit
    public void createAccounts(int numAccounts, int batchSize, Supplier<Account> accountSupplier) {
        Assert.isTrue(!TransactionSynchronizationManager.isActualTransactionActive(), "TX active");

        for (int i = 0; i < numAccounts; i += batchSize) {
            if (i + batchSize > numAccounts) {
                batchSize = numAccounts - i;
            }

            final int currentBatch = batchSize;

            jdbcTemplate.batchUpdate(
                    "INSERT INTO account "
                            + "(region, balance, currency, name, description, account_type, closed, allow_negative) "
                            + "VALUES(?,?,?,?,?,?,?,?)",
                    new BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement ps, int i) throws SQLException {
                            Account account = accountSupplier.get();

                            ps.setString(1, account.getRegion());
                            ps.setBigDecimal(2, account.getBalance().getAmount());
                            ps.setString(3, account.getBalance().getCurrency().getCurrencyCode());
                            ps.setString(4, account.getName());
                            ps.setString(5, account.getDescription());
                            ps.setString(6, account.getAccountType().getCode());
                            ps.setBoolean(7, account.isClosed());
                            ps.setInt(8, account.getAllowNegative());
                        }

                        @Override
                        public int getBatchSize() {
                            return currentBatch;
                        }
                    });
        }
    }

    @Override
    public Money getBalance(UUID id) {
        return this.jdbcTemplate.queryForObject(
                "SELECT balance,currency "
                        + "FROM account a "
                        + "WHERE id=?",
                (rs, rowNum) -> Money.of(rs.getString(1), rs.getString(2)),
                id
        );
    }

    @Override
    public Money getBalanceSnapshot(UUID id) {
        return this.jdbcTemplate.queryForObject(
                "SELECT balance,currency "
                        + "FROM account a AS OF SYSTEM TIME follower_read_timestamp() "
                        + "WHERE id=?",
                (rs, rowNum) -> Money.of(rs.getString(1), rs.getString(2)),
                id
        );
    }

    @Override
    public List<String> getCurrencies() {
        return this.jdbcTemplate.queryForList(
                "SELECT distinct currency "
                        + "FROM account a "
                        + "WHERE 1=1",
                String.class
        );
    }

    @Override
    public Money getTotalBalance(String currency) {
        BigDecimal balance = this.jdbcTemplate.queryForObject(
                "SELECT sum(balance) "
                        + "FROM account a "
                        + "WHERE currency=?",
                (rs, rowNum) -> rs.getBigDecimal(1),
                currency
        );
        return Money.of(balance, currency);
    }

    @Override
    public void updateBalances(List<Account> accounts) {
        int[] rowsAffected = jdbcTemplate.batchUpdate(
                "UPDATE account "
                        + "SET "
                        + "   balance = ?,"
                        + "   updated_at=clock_timestamp() "
                        + "WHERE id = ? "
                        + "   AND closed=false "
                        + "   AND currency=? "
                        + "   AND (?) * abs(allow_negative-1) >= 0",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        Account account = accounts.get(i);

                        ps.setBigDecimal(1, account.getBalance().getAmount());
                        ps.setObject(2, account.getId());
                        ps.setString(3, account.getBalance().getCurrency().getCurrencyCode());
                        ps.setBigDecimal(4, account.getBalance().getAmount());
                    }

                    @Override
                    public int getBatchSize() {
                        return accounts.size();
                    }
                });

        // Trust but verify
        Arrays.stream(rowsAffected).filter(i -> i != 1).forEach(i -> {
            throw new IncorrectResultSizeDataAccessException(1, i);
        });
    }

    @Override
    public List<Account> findAccountsForUpdate(Set<UUID> ids) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();

        parameters.addValue("ids", new HashSet<>(ids));

        return this.namedParameterJdbcTemplate.query(
                "SELECT * FROM account WHERE id in (:ids) FOR UPDATE",
                parameters,
                (rs, rowNum) -> readAccount(rs));
    }

    @Override
    public List<Account> findAccountsByRegion(String region, int offset, int limit) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("region", region);
        parameters.addValue("offset", offset);
        parameters.addValue("limit", limit);
        return this.namedParameterJdbcTemplate.query(
                "SELECT * FROM account WHERE region=:region "
                        + "OFFSET (:offset) LIMIT (:limit)",
                parameters, (rs, rowNum) -> readAccount(rs));
    }

    @Override
    public AccountSummary accountSummary(String region) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("region", region);

        return namedParameterJdbcTemplate.queryForObject(
                "SELECT "
                        + "  count(a.id) tot_accounts, "
                        + "  sum(a.balance) tot_balance, "
                        + "  min(a.balance) min_balance, "
                        + "  max(a.balance) max_balance, "
                        + "  avg(a.balance) avg_balance "
                        + "FROM account a "
                        + "WHERE a.region=:region",
                parameters,
                (rs, rowNum) -> {
                    AccountSummary summary = new AccountSummary();
                    summary.setNumberOfAccounts(rs.getInt(1));
                    summary.setTotalBalance(rs.getBigDecimal(2));
                    summary.setMinBalance(rs.getBigDecimal(3));
                    summary.setMaxBalance(rs.getBigDecimal(4));
                    summary.setAvgBalance(rs.getBigDecimal(5));
                    return summary;
                });
    }

    private Account readAccount(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("updated_at");
        return Account.builder()
                .withId((UUID) rs.getObject("id"))
                .withRegion(rs.getString("region"))
                .withName(rs.getString("name"))
                .withBalance(Money.of(rs.getString("balance"), rs.getString("currency")))
                .withAccountType(AccountType.of(rs.getString("account_type")))
                .withDescription(rs.getString("description"))
                .withClosed(rs.getBoolean("closed"))
                .withAllowNegative(rs.getInt("allow_negative") > 0)
                .withInsertedAt(rs.getTimestamp("inserted_at").toLocalDateTime())
                .withUpdatedAt(ts != null ? ts.toLocalDateTime() : null)
                .build();
    }
}
