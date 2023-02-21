package io.cockroachdb.workload.ledger.model;

import java.time.LocalDateTime;
import java.util.UUID;

import io.cockroachdb.workload.common.util.Money;
import io.cockroachdb.workload.common.util.WeightedItem;
import io.cockroachdb.workload.ledger.service.NegativeBalanceException;
import jakarta.persistence.*;

/**
 * Represents a monetary account like asset, liability, expense, capital accounts and so forth.
 * <p>
 * JPA annotations are only used by JPA server implementation.
 */
@Entity
@Table(name = "account")
public class Account extends AbstractEntity<UUID> implements WeightedItem {
    @Id
    @Column(updatable = false)
    private UUID id;

    @Column(updatable = false)
    private String region;

    @Column
    private String name;

    @Column
    @Basic(fetch = FetchType.LAZY)
    private String description;

    @Convert(converter = AccountTypeConverter.class)
    @Column(name = "account_type", updatable = false, nullable = false)
    private AccountType accountType;

    @Column(name = "inserted_at", updatable = false)
    @Basic(fetch = FetchType.LAZY)
    private LocalDateTime insertedAt;

    @Column(name = "updated_at")
    @Basic(fetch = FetchType.LAZY)
    private LocalDateTime updatedAt;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "balance")),
            @AttributeOverride(name = "currency", column = @Column(name = "currency"))
    })
    private Money balance;

    @Column(nullable = false)
    private boolean closed;

    @Column(name = "allow_negative", nullable = false)
    private int allowNegative;

    protected Account() {
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public double getWeight() {
        return Math.max(0, balance.getAmount().doubleValue());
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getRegion() {
        return region;
    }

    public void addAmount(Money amount) {
        Money newBalance = getBalance().plus(amount);
        if (getAllowNegative() == 0 && newBalance.isNegative()) {
            throw new NegativeBalanceException(toDisplayString());
        }
        this.balance = newBalance;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Money getBalance() {
        return balance;
    }

    public LocalDateTime getInsertedAt() {
        return insertedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    public int getAllowNegative() {
        return allowNegative;
    }

    public String toDisplayString() {
        return toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Account)) {
            return false;
        }

        Account that = (Account) o;

        if (!id.equals(that.id)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    public static final class Builder {
        private final Account instance = new Account();

        public Builder withId(UUID accountId) {
            this.instance.id = accountId;
            return this;
        }

        public Builder withRegion(String region) {
            this.instance.region = region;
            return this;
        }

        public Builder withName(String name) {
            this.instance.name = name;
            return this;
        }

        public Builder withBalance(Money balance) {
            this.instance.balance = balance;
            return this;
        }

        public Builder withAccountType(AccountType accountType) {
            this.instance.accountType = accountType;
            return this;
        }

        public Builder withClosed(boolean closed) {
            this.instance.closed = closed;
            return this;
        }

        public Builder withAllowNegative(boolean allowNegative) {
            this.instance.allowNegative = allowNegative ? 1 : 0;
            return this;
        }

        public Builder withDescription(String description) {
            this.instance.description = description;
            return this;
        }

        public Builder withInsertedAt(LocalDateTime dateTime) {
            this.instance.insertedAt = dateTime;
            return this;
        }

        public Builder withUpdatedAt(LocalDateTime dateTime) {
            this.instance.updatedAt = dateTime;
            return this;
        }

        public Account build() {
            return instance;
        }
    }
}
