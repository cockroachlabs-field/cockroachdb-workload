package io.cockroachdb.workload.ledger.service;

import io.cockroachdb.workload.ledger.model.TransferRequest;

public interface TransferService {
    void processTransferRequest(TransferRequest transferRequest);
}
