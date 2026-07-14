package io.javalibs.spring.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link BeanPostProcessor} that measures bean initialization time and logs a
 * WARN line for every bean slower than the configured threshold.
 *
 * <p>Useful to diagnose slow application startup: register this processor (or
 * enable it via a javalibs starter) and grep the log for "Slow bean".
 */
public class SlowBeanInitializationLogger implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(SlowBeanInitializationLogger.class);

    private final long thresholdMillis;
    private final Map<String, Long> startTimes = new ConcurrentHashMap<>();

    /** Creates a logger with the default threshold of 500 ms. */
    public SlowBeanInitializationLogger() {
        this(Duration.ofMillis(500));
    }

    public SlowBeanInitializationLogger(Duration threshold) {
        this.thresholdMillis = threshold.toMillis();
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        startTimes.put(beanName, System.nanoTime());
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Long start = startTimes.remove(beanName);
        if (start != null) {
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
            if (elapsedMillis >= thresholdMillis) {
                log.warn("Slow bean initialization: '{}' ({}) took {} ms",
                        beanName, bean.getClass().getName(), elapsedMillis);
            }
        }
        return bean;
    }
}
