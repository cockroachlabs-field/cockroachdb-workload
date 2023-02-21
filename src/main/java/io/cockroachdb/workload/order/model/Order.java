package io.cockroachdb.workload.order.model;

import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.Columns;

import io.cockroachdb.workload.common.jpa.AbstractEntity;
import io.cockroachdb.workload.common.util.Money;
import jakarta.persistence.*;

@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@Table(name = "orders")
public class Order extends AbstractEntity<UUID> {
    @Id
    @Column(updatable = false, nullable = false)
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "order_number")
    private Integer orderNumber;

    @Column(nullable = false, name = "date_updated")
    @Basic(fetch = FetchType.LAZY)
    private LocalDate dateUpdated;

    @Column(nullable = false, name = "date_placed")
    @Basic(fetch = FetchType.LAZY)
    private LocalDate datePlaced;

    @Column(name = "bill_to_first_name")
    private String billToFirstName;

    @Column(name = "bill_to_last_name")
    private String billToLastName;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "address1",
                    column = @Column(name = "bill_address1", length = 255)),
            @AttributeOverride(name = "address2",
                    column = @Column(name = "bill_address2", length = 255)),
            @AttributeOverride(name = "city",
                    column = @Column(name = "bill_city", length = 255)),
            @AttributeOverride(name = "postcode",
                    column = @Column(name = "bill_postcode", length = 16)),
            @AttributeOverride(name = "country.code",
                    column = @Column(name = "bill_country_code", length = 16)),
            @AttributeOverride(name = "country.name",
                    column = @Column(name = "bill_country_name", length = 16))
    })
    private Address billAddress;

    @Column(name = "deliv_to_first_name")
    private String deliverToFirstName;

    @Column(name = "deliv_to_last_name")
    private String deliverToLastName;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "address1",
                    column = @Column(name = "deliv_address1", length = 255)),
            @AttributeOverride(name = "address2",
                    column = @Column(name = "deliv_address2", length = 255)),
            @AttributeOverride(name = "city",
                    column = @Column(name = "deliv_city", length = 255)),
            @AttributeOverride(name = "postcode",
                    column = @Column(name = "deliv_postcode", length = 16)),
            @AttributeOverride(name = "country.code",
                    column = @Column(name = "deliv_country_code", length = 16)),
            @AttributeOverride(name = "country.name",
                    column = @Column(name = "deliv_country_name", length = 16))
    })
    private Address deliveryAddress;

    @Columns(columns = {
            @Column(name = "total_price_amount", nullable = false, updatable = false),
            @Column(name = "total_price_currency", nullable = false, updatable = false, length = 3)
    })
    private Money totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 64)
    private ShipmentStatus status = ShipmentStatus.PLACED;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "payment_method_id")
    private UUID paymentMethod;

    @Column(name = "customer_profile")
    @Convert(converter = CustomerConverter.class)
    private Customer customer;

    public Order() {

    }

    public Customer getCustomer() {
        return customer;
    }

    public Order setCustomer(Customer customer) {
        this.customer = customer;
        return this;
    }

    public Order setId(UUID id) {
        this.id = id;
        return this;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public Integer getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(Integer orderNumber) {
        this.orderNumber = orderNumber;
    }

    public LocalDate getDatePlaced() {
        return datePlaced;
    }

    public String getBillToFirstName() {
        return billToFirstName;
    }

    public String getBillToLastName() {
        return billToLastName;
    }

    public Address getBillAddress() {
        return billAddress;
    }

    public String getDeliverToFirstName() {
        return deliverToFirstName;
    }

    public String getDeliverToLastName() {
        return deliverToLastName;
    }

    public Address getDeliveryAddress() {
        return deliveryAddress;
    }

    public Money getTotalPrice() {
        return totalPrice;
    }

    public ShipmentStatus getStatus() {
        return status;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getPaymentMethod() {
        return paymentMethod;
    }

    public LocalDate getDateUpdated() {
        return dateUpdated;
    }

    public void setDatePlaced(LocalDate datePlaced) {
        this.datePlaced = datePlaced;
    }

    public Order setDateUpdated(LocalDate dateUpdated) {
        this.dateUpdated = dateUpdated;
        return this;
    }

    public void setBillToFirstName(String billToFirstName) {
        this.billToFirstName = billToFirstName;
    }

    public void setBillToLastName(String billToLastName) {
        this.billToLastName = billToLastName;
    }

    public void setBillAddress(Address billAddress) {
        this.billAddress = billAddress;
    }

    public void setDeliverToFirstName(String deliverToFirstName) {
        this.deliverToFirstName = deliverToFirstName;
    }

    public void setDeliverToLastName(String deliverToLastName) {
        this.deliverToLastName = deliverToLastName;
    }

    public void setDeliveryAddress(Address deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public void setTotalPrice(Money totalPrice) {
        this.totalPrice = totalPrice;
    }

    public void setStatus(ShipmentStatus status) {
        this.status = status;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public void setPaymentMethod(UUID paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    @Override
    public String toString() {
        return "Order{" +
                "id=" + id +
                ", orderNumber=" + orderNumber +
                ", dateUpdated=" + dateUpdated +
                ", datePlaced=" + datePlaced +
                ", billToFirstName='" + billToFirstName + '\'' +
                ", billToLastName='" + billToLastName + '\'' +
                ", billAddress=" + billAddress +
                ", deliverToFirstName='" + deliverToFirstName + '\'' +
                ", deliverToLastName='" + deliverToLastName + '\'' +
                ", deliveryAddress=" + deliveryAddress +
                ", totalPrice=" + totalPrice +
                ", status=" + status +
                ", customerId=" + customerId +
                ", paymentMethod=" + paymentMethod +
                ", customer=" + customer +
                "} " + super.toString();
    }
}

