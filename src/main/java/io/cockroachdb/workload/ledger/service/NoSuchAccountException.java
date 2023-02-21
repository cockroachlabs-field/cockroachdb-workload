package io.cockroachdb.workload.ledger.service;

public class NoSuchAccountException extends BusinessException {
    public NoSuchAccountException(String name) {
        super("No such account: " + name);
    }
}
