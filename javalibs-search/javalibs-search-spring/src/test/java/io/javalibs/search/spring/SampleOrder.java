package io.javalibs.search.spring;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/** Test-only entity used to exercise {@link SpecificationBuilder} against a real database. */
@Entity
class SampleOrder {

    /** Test-only order status enum. */
    enum Status {
        NEW, OPEN, CLOSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private Status status;

    private String customerName;

    @Column(precision = 19, scale = 2)
    private BigDecimal total;

    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    private SampleCustomer customer;

    protected SampleOrder() {
    }

    SampleOrder(Status status, String customerName, BigDecimal total, Instant createdAt,
                SampleCustomer customer) {
        this.status = status;
        this.customerName = customerName;
        this.total = total;
        this.createdAt = createdAt;
        this.customer = customer;
    }

    Long getId() {
        return id;
    }

    Status getStatus() {
        return status;
    }

    String getCustomerName() {
        return customerName;
    }

    BigDecimal getTotal() {
        return total;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    SampleCustomer getCustomer() {
        return customer;
    }
}
