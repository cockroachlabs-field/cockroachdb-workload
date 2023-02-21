package io.cockroachdb.workload.order.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.cockroachdb.workload.order.model.Order;

public interface OrderRepository {
    void insertOrders(List<Order> orders, boolean includeJson);

    Optional<Order> readOrder(UUID id, boolean followerReads);

    Optional<UUID> findLowestId();

    List<Order> findOrders(UUID fromId, int limit);
}
