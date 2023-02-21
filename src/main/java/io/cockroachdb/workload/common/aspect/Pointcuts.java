package io.cockroachdb.workload.common.aspect;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

@Aspect
public class Pointcuts {
    @Pointcut("within(io.cockroachdb..*) && @within(transactionBoundary) || @annotation(transactionBoundary)")
    public void anyTransactionBoundaryOperation(TransactionBoundary transactionBoundary) {
    }
}
