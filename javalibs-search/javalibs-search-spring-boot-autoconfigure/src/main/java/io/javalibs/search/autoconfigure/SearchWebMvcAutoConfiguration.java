package io.javalibs.search.autoconfigure;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import io.javalibs.search.SearchQueryParser;
import io.javalibs.search.spring.SearchQueryArgumentResolver;

/**
 * Auto-configuration that registers a {@link SearchQueryArgumentResolver} with Spring MVC
 * so controllers can declare {@link io.javalibs.search.SearchQuery} method parameters.
 * Only active in servlet web applications with Spring MVC on the classpath.
 */
@AutoConfiguration(after = SearchAutoConfiguration.class)
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass(WebMvcConfigurer.class)
public class SearchWebMvcAutoConfiguration {

    /**
     * Registers the {@link SearchQueryArgumentResolver} backed by the application's
     * {@link SearchQueryParser} bean.
     *
     * @param parser the configured search query parser
     * @return a {@link WebMvcConfigurer} adding the argument resolver
     */
    @Bean
    public WebMvcConfigurer searchWebMvcConfigurer(SearchQueryParser parser) {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new SearchQueryArgumentResolver(parser));
            }
        };
    }
}
