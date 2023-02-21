package io.cockroachdb.workload.order.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.Session;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.cockroachdb.workload.Profiles;
import io.cockroachdb.workload.order.model.Order;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Repository
@Profiles.Order
@Transactional(propagation = Propagation.REQUIRED)
public class JpaOrderRepository implements OrderRepository {
    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertOrders(List<Order> orders, boolean includeJson) {
        // Default is 64
        if (orders.size() != 64) {
            Session session = em.unwrap(Session.class);
            session.setJdbcBatchSize(orders.size());
        }
        orders.forEach(order -> {
            if (!includeJson) {
                order.setCustomer(null);
            }
            em.persist(order);
        }); // transparent
    }

    @Override
    public Optional<Order> readOrder(UUID id, boolean followerReads) {
        if (followerReads) {
            em.createNativeQuery("SET TRANSACTION AS OF SYSTEM TIME follower_read_timestamp()").executeUpdate();
        }
        return em
                .createQuery("select o from Order o where o.id = :id", Order.class)
                .setParameter("id", id)
                .getResultStream()
                .findFirst();
    }

    @Override
    public Optional<UUID> findLowestId() {
        return em
                .createQuery("select min(o.id) from Order o", UUID.class)
                .getResultStream()
                .findFirst();
    }

    @Override
    public List<Order> findOrders(UUID fromId, int limit) {
        return em.createQuery("select o from Order o where o.id > :fromId order by id", Order.class)
                .setParameter("fromId", fromId)
                .setMaxResults(limit)
                .getResultList();
    }
}
