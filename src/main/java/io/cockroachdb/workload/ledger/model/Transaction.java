package io.cockroachdb.workload.ledger.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * Represents a monetary transaction (balance update) between at least two different accounts.
 * <p>
 * JPA annotations are only used by JPA server implementation.
 */
@Entity
@Table(name = "transaction")
public class Transaction extends AbstractEntity<UUID> {
    @Id
    @Column(updatable = false)
    private UUID id;

    @Column(updatable = false)
    private String region;

    @Column(name = "transaction_type")
    private String transactionType;

    @Column(name = "transfer_date", nullable = false, updatable = false)
    private LocalDate transferDate;

    @Column(name = "booking_date", nullable = false, updatable = false)
    private LocalDate bookingDate;

    @OneToMany(orphanRemoval = true, mappedBy = "transaction", fetch = FetchType.LAZY)
    private List<TransactionItem> items;

    public Transaction() {
    }

    protected Transaction(UUID id,
                          String region,
                          String transactionType,
                          LocalDate bookingDate,
                          LocalDate transferDate,
                          List<TransactionItem> items) {
        this.id = id;
        this.region = region;
        this.transactionType = transactionType;
        this.bookingDate = bookingDate;
        this.transferDate = transferDate;
        this.items = items;

        items.forEach(item -> {
            item.setId(new TransactionItem.Id(
                    Objects.requireNonNull(item.getAccount().getId()),
                    Objects.requireNonNull(id)
            ));
            item.setTransaction(this);
        });
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getRegion() {
        return region;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public LocalDate getTransferDate() {
        return transferDate;
    }

    public LocalDate getBookingDate() {
        return bookingDate;
    }

    public List<TransactionItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<TransactionItem> items = new ArrayList<>();

        private UUID transactionId;

        private String region;

        private String transferType;

        private LocalDate bookingDate;

        private LocalDate transferDate;

        public Builder withId(UUID id) {
            this.transactionId = id;
            return this;
        }

        public Builder withRegion(String region) {
            this.region = region;
            return this;
        }

        public Builder withTransferType(String transferType) {
            this.transferType = transferType;
            return this;
        }

        public Builder withBookingDate(LocalDate bookingDate) {
            this.bookingDate = bookingDate;
            return this;
        }

        public Builder withTransferDate(LocalDate transferDate) {
            this.transferDate = transferDate;
            return this;
        }

        public TransactionItem.Builder andItem() {
            return TransactionItem.builder(this, items::add);
        }

        public Transaction build() {
            return new Transaction(transactionId, region, transferType, bookingDate, transferDate, items);
        }
    }
}
