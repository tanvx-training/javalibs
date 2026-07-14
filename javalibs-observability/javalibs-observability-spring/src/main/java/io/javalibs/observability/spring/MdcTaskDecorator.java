package io.javalibs.observability.spring;

import java.util.Map;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * {@link TaskDecorator} that propagates the SLF4J MDC from the submitting
 * thread to the executing thread.
 *
 * <p>The MDC context map is captured when the task is submitted (i.e. when
 * {@link #decorate(Runnable)} is invoked) and installed on the worker thread
 * just before the task runs. This makes {@code @Async} methods and other
 * thread-pool tasks carry the {@code correlationId}, {@code traceId} and
 * {@code spanId} MDC entries of the originating request, so asynchronous log
 * lines stay correlated.</p>
 *
 * <p>In a {@code finally} block the worker thread's previous MDC state is
 * restored (or cleared when there was none), preventing context leakage
 * between pooled threads. A {@code null} captured map is handled safely.</p>
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> captured = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            setContextMap(captured);
            try {
                runnable.run();
            }
            finally {
                setContextMap(previous);
            }
        };
    }

    private static void setContextMap(Map<String, String> contextMap) {
        if (contextMap != null) {
            MDC.setContextMap(contextMap);
        }
        else {
            MDC.clear();
        }
    }
}
