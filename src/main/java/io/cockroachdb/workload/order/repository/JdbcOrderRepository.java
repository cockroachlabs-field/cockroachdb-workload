package io.cockroachdb.workload.order.repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.jdbc.JdbcUpdateAffectedIncorrectNumberOfRowsException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.cockroachdb.workload.Profiles;
import io.cockroachdb.workload.common.util.Money;
import io.cockroachdb.workload.order.model.Address;
import io.cockroachdb.workload.order.model.Country;
import io.cockroachdb.workload.order.model.Customer;
import io.cockroachdb.workload.order.model.Order;
import io.cockroachdb.workload.order.model.ShipmentStatus;
import jakarta.persistence.Table;

@Repository
@Profiles.Order
public class JdbcOrderRepository implements OrderRepository {
    private final JdbcTemplate jdbcTemplate;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final ObjectMapper objectMapper;

    public JdbcOrderRepository(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
    }

    private String findTableName() {
        Table t = AnnotationUtils.findAnnotation(Order.class, Table.class);
        if (t == null) {
            throw new IllegalArgumentException("No @Table annotation found for: " + Order.class.getName());
        }
        return t.name();
    }

    @Override
    public void insertOrders(List<Order> orders, boolean includeJson) {
        if (orders.isEmpty()) {
            return;
        }

        String tableName = findTableName();

        final String query = "INSERT INTO " + tableName + " ("
                + "id,"
                + "order_number,"
                + "bill_address1,"
                + "bill_address2,"
                + "bill_city,"
                + "bill_country_name,"
                + "bill_postcode,"
                + "bill_to_first_name,"
                + "bill_to_last_name,"
                + "deliv_to_first_name,"
                + "deliv_to_last_name,"
                + "deliv_address1,"
                + "deliv_address2,"
                + "deliv_city,"
                + "deliv_country_name,"
                + "deliv_postcode,"
                + "status,"
                + "amount,"
                + "currency,"
                + "customer_id,"
                + "payment_method_id,"
                + "date_placed,"
                + "date_updated,"
                + "customer_profile)"
                + "values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        int[] rv = jdbcTemplate.batchUpdate(query, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int entityId) throws SQLException {
                Order order = orders.get(entityId);
                int i = 1;
                ps.setObject(i++, order.getId());
                ps.setInt(i++, order.getOrderNumber());
                ps.setString(i++, order.getBillAddress().getAddress1());
                ps.setString(i++, order.getBillAddress().getAddress2());
                ps.setString(i++, order.getBillAddress().getCity());
                ps.setString(i++, order.getBillAddress().getCountry().getCode());
                ps.setString(i++, order.getBillAddress().getPostcode());
                ps.setString(i++, order.getBillToFirstName());
                ps.setString(i++, order.getBillToLastName());
                ps.setString(i++, order.getDeliverToFirstName());
                ps.setString(i++, order.getDeliverToLastName());
                ps.setString(i++, order.getDeliveryAddress().getAddress1());
                ps.setString(i++, order.getDeliveryAddress().getAddress2());
                ps.setString(i++, order.getDeliveryAddress().getCity());
                ps.setString(i++, order.getDeliveryAddress().getCountry().getCode());
                ps.setString(i++, order.getDeliveryAddress().getPostcode());
                ps.setString(i++, order.getStatus().name());
                ps.setBigDecimal(i++, order.getTotalPrice().getAmount());
                ps.setString(i++, order.getTotalPrice().getCurrency().getCurrencyCode());
                ps.setObject(i++, order.getCustomerId());
                ps.setObject(i++, order.getPaymentMethod());
                ps.setObject(i++, order.getDatePlaced());
                ps.setObject(i++, order.getDateUpdated());

                Customer customer = order.getCustomer();
                if (customer != null && includeJson) {
                    try {
                        ps.setObject(i++,
                                objectMapper.writer().writeValueAsString(customer),
                                java.sql.Types.OTHER);
                    } catch (JsonProcessingException e) {
                        throw new SQLException("Error serializing json", e);
                    }
                } else {
                    ps.setNull(i++, Types.NULL);
                }
            }

            @Override
            public int getBatchSize() {
                return orders.size();
            }
        });

        Arrays.stream(rv).forEach(k -> {
            if (k == PreparedStatement.EXECUTE_FAILED) {
                throw new JdbcUpdateAffectedIncorrectNumberOfRowsException(query, 1, k);
            }
        });
    }


    @Override
    public Optional<Order> readOrder(UUID id, boolean followerReads) {
        String tableName = findTableName();

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("id", id);

        if (followerReads) {
            return Optional.ofNullable(namedParameterJdbcTemplate
                    .queryForObject("SELECT * FROM " + tableName + " AS OF SYSTEM TIME follower_read_timestamp() "
                                    + "WHERE id=:id",
                            parameters,
                            orderMapper()
                    ));
        } else {
            return Optional.ofNullable(namedParameterJdbcTemplate
                    .queryForObject("SELECT * FROM " + tableName + " "
                                    + "WHERE id=:id",
                            parameters,
                            orderMapper()
                    ));
        }
    }

    @Override
    public Optional<UUID> findLowestId() {
        String tableName = findTableName();
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        return Optional.ofNullable(namedParameterJdbcTemplate
                .queryForObject("SELECT min(id) FROM " + tableName
                                + " AS OF SYSTEM TIME follower_read_timestamp()",
                        parameters,
                        UUID.class
                ));
    }

    @Override
    public List<Order> findOrders(UUID fromId, int limit) {
        String tableName = findTableName();

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("fromId", fromId);
        parameters.addValue("limit", limit);

        return namedParameterJdbcTemplate
                .query("SELECT * FROM " + tableName
                                + " AS OF SYSTEM TIME follower_read_timestamp()"
                                + " WHERE id > :fromId"
                                + " ORDER BY id "
                                + " LIMIT :limit",
                        parameters,
                        orderMapper());
    }

    private RowMapper<Order> orderMapper() {
        return (rs, rowNum) -> {
            UUID uuid = rs.getObject("id", UUID.class);

            Order order = new Order();
            order.setId(uuid);

            order.setOrderNumber(rs.getInt("order_number"));
            order.setStatus(ShipmentStatus.valueOf(rs.getString("status")));
            order.setDatePlaced(rs.getDate("date_placed").toLocalDate());
            order.setDateUpdated(rs.getDate("date_updated").toLocalDate());
            order.setCustomerId(rs.getObject("customer_id", UUID.class));
            order.setPaymentMethod(rs.getObject("payment_method_id", UUID.class));

            order.setDeliverToFirstName(rs.getString("deliv_to_first_name"));
            order.setDeliverToLastName(rs.getString("deliv_to_last_name"));
            order.setDeliveryAddress((Address.builder()
                    .setAddress1(rs.getString("bill_address1"))
                    .setAddress2(rs.getString("bill_address1"))
                    .setCity(rs.getString("bill_city"))
                    .setCountry(new Country(rs.getString("bill_country_code"), rs.getString("bill_country_name")))
                    .setPostcode(rs.getString("bill_postcode")).build()));

            order.setBillToFirstName(rs.getString(6));
            order.setBillToLastName(rs.getString(6));
            order.setBillAddress(Address.builder()
                    .setAddress1(rs.getString("deliv_address1"))
                    .setAddress2(rs.getString("deliv_address2"))
                    .setCity(rs.getString("deliv_city"))
                    .setCountry(new Country(rs.getString("deliv_country_code"), rs.getString("deliv_country_name")))
                    .setPostcode(rs.getString("deliv_postcode")).build());

            order.setTotalPrice(Money.of(rs.getString("amount"), rs.getString("currency")));

            return order;
        };
    }
}
