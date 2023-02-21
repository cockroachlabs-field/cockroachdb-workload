package io.cockroachdb.workload.ledger.repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import io.cockroachdb.workload.Profiles;
import io.cockroachdb.workload.ledger.model.Transaction;
import io.cockroachdb.workload.ledger.model.TransactionItem;

@Profiles.Ledger
@Repository
public class JdbcTransactionRepositoryImpl implements TransactionRepository {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public Transaction createTransaction(Transaction transaction) {
        final LocalDate bookingDate = transaction.getBookingDate();
        final LocalDate transferDate = transaction.getTransferDate();

        jdbcTemplate.update("INSERT INTO transaction "
                        + "(id,region,booking_date,transfer_date,transaction_type) "
                        + "VALUES(?, ?, ?, ?, ?)",
                transaction.getId(),
                transaction.getRegion(),
                bookingDate != null ? bookingDate : LocalDate.now(),
                transferDate != null ? transferDate : LocalDate.now(),
                transaction.getTransactionType()
        );

        final List<TransactionItem> items = transaction.getItems();

        jdbcTemplate.batchUpdate(
                "INSERT INTO transaction_item "
                        + "(region, transaction_id, account_id, amount, currency, note, running_balance) "
                        + "VALUES(?,?,?,?,?,?,?)", new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        TransactionItem transactionLeg = items.get(i);
                        ps.setString(1, transactionLeg.getRegion());
                        ps.setObject(2, transactionLeg.getId().getTransactionId());
                        ps.setObject(3, transactionLeg.getId().getAccountId());
                        ps.setBigDecimal(4, transactionLeg.getAmount().getAmount());
                        ps.setString(5, transactionLeg.getAmount().getCurrency().getCurrencyCode());
                        ps.setString(6, transactionLeg.getNote());
                        ps.setBigDecimal(7, transactionLeg.getRunningBalance().getAmount());
                    }

                    @Override
                    public int getBatchSize() {
                        return items.size();
                    }
                });

        return transaction;
    }
}
