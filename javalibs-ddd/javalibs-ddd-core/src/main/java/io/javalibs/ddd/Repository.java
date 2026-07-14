package io.javalibs.ddd;

import java.util.Optional;

/**
 * Base repository contract for aggregate roots, technology-agnostic.
 *
 * <p>Infrastructure modules implement this with JPA, JDBC, MongoDB, etc. Domain
 * and application code depend only on this interface, keeping the domain model
 * persistence-ignorant.
 *
 * @param <T>  aggregate type
 * @param <ID> aggregate identifier type
 */
public interface Repository<T extends AggregateRoot<ID>, ID> {

    Optional<T> findById(ID id);

    T save(T aggregate);

    void delete(T aggregate);
}
