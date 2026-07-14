package io.javalibs.observability.spring;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.javalibs.observability.CorrelationId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class MdcTaskDecoratorTest {

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    @Test
    void propagatesMdcToAnotherThread() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            MDC.put(CorrelationId.MDC_KEY, "abc123");
            MDC.put("traceId", "trace-1");
            AtomicReference<Map<String, String>> seenInWorker = new AtomicReference<>();

            Runnable decorated = decorator.decorate(() ->
                    seenInWorker.set(MDC.getCopyOfContextMap()));
            executor.submit(decorated).get(5, TimeUnit.SECONDS);

            assertThat(seenInWorker.get())
                    .containsEntry(CorrelationId.MDC_KEY, "abc123")
                    .containsEntry("traceId", "trace-1");
        }
        finally {
            executor.shutdownNow();
        }
    }

    @Test
    void cleansWorkerThreadMdcAfterExecution() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            MDC.put(CorrelationId.MDC_KEY, "abc123");
            Runnable decorated = decorator.decorate(() -> { });
            executor.submit(decorated).get(5, TimeUnit.SECONDS);

            AtomicReference<String> leaked = new AtomicReference<>();
            executor.submit(() -> leaked.set(MDC.get(CorrelationId.MDC_KEY)))
                    .get(5, TimeUnit.SECONDS);

            assertThat(leaked.get()).isNull();
        }
        finally {
            executor.shutdownNow();
        }
    }

    @Test
    void cleansWorkerThreadMdcEvenWhenTaskThrows() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            MDC.put(CorrelationId.MDC_KEY, "abc123");
            Runnable decorated = decorator.decorate(() -> {
                throw new IllegalStateException("boom");
            });
            assertThatCode(() -> executor.submit(decorated).get(5, TimeUnit.SECONDS))
                    .hasRootCauseInstanceOf(IllegalStateException.class);

            AtomicReference<String> leaked = new AtomicReference<>();
            executor.submit(() -> leaked.set(MDC.get(CorrelationId.MDC_KEY)))
                    .get(5, TimeUnit.SECONDS);

            assertThat(leaked.get()).isNull();
        }
        finally {
            executor.shutdownNow();
        }
    }

    @Test
    void restoresPreviousMdcOfExecutingThread() {
        MDC.put(CorrelationId.MDC_KEY, "submitting-value");
        Runnable decorated = decorator.decorate(() -> { });

        // Simulate a worker thread that already has its own MDC state.
        MDC.clear();
        MDC.put("workerKey", "workerValue");
        decorated.run();

        assertThat(MDC.get("workerKey")).isEqualTo("workerValue");
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }

    @Test
    void handlesNullContextMapSafely() {
        MDC.clear();
        Runnable decorated = decorator.decorate(() ->
                assertThat(MDC.getCopyOfContextMap()).satisfiesAnyOf(
                        map -> assertThat(map).isNull(),
                        map -> assertThat(map).isEmpty()));

        assertThatCode(decorated::run).doesNotThrowAnyException();
    }
}
