package io.cockroachdb.workload.ledger.model;

import java.io.Serializable;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Transient;

public abstract class AbstractEntity<T extends Serializable> implements Persistable<T> {
    @Transient
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PrePersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
