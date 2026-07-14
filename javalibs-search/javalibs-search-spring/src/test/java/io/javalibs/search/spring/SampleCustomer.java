package io.javalibs.search.spring;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** Test-only entity: a customer referenced by {@link SampleOrder} via a to-one association. */
@Entity
class SampleCustomer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    protected SampleCustomer() {
    }

    SampleCustomer(String name) {
        this.name = name;
    }

    Long getId() {
        return id;
    }

    String getName() {
        return name;
    }
}
