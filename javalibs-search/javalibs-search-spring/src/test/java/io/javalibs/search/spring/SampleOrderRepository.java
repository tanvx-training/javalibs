package io.javalibs.search.spring;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Test-only repository executing Specifications against {@link SampleOrder}. */
interface SampleOrderRepository
        extends JpaRepository<SampleOrder, Long>, JpaSpecificationExecutor<SampleOrder> {
}
