package io.cockroachdb.workload.order.model;

import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.cockroachdb.workload.order.model.OrderEntities;

public class OrderEntitiesTest {
    @Test
    public void testCreate() {
        IntStream.rangeClosed(1, 500_000).forEach(value -> {
            OrderEntities.generateOrderEntities(16);
            if (value % 10_000 == 0) {
                System.out.print(".");
            }
        });
    }
}
