package io.javalibs.spring.context;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Static access to the current {@link ApplicationContext}.
 *
 * <p>Register as a bean (it implements {@link ApplicationContextAware}) and use
 * {@link #getBean(Class)} from places that cannot participate in dependency
 * injection (legacy code, static factories). Prefer constructor injection
 * everywhere else — this is an escape hatch, not a pattern.
 */
public class ApplicationContextProvider implements ApplicationContextAware {

    private static volatile ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }

    /** Returns the current context, or throws when no context has been set yet. */
    public static ApplicationContext context() {
        ApplicationContext current = context;
        if (current == null) {
            throw new IllegalStateException(
                    "ApplicationContext is not available. Register ApplicationContextProvider as a bean "
                            + "and make sure the context has been refreshed before calling this method.");
        }
        return current;
    }

    /** Shortcut for {@code context().getBean(type)}. */
    public static <T> T getBean(Class<T> type) {
        return context().getBean(type);
    }

    /** Shortcut for {@code context().getBean(name, type)}. */
    public static <T> T getBean(String name, Class<T> type) {
        return context().getBean(name, type);
    }
}
