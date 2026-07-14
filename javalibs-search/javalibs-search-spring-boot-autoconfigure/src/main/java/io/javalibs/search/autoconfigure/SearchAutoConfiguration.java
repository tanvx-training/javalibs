package io.javalibs.search.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import io.javalibs.search.SearchQueryParser;
import io.javalibs.search.SearchQueryParser.ParserConfig;

/**
 * Auto-configuration providing a {@link SearchQueryParser} built from
 * {@link SearchProperties} ({@code javalibs.search.*}). Backs off when the application
 * defines its own {@link SearchQueryParser} bean.
 */
@AutoConfiguration
@EnableConfigurationProperties(SearchProperties.class)
public class SearchAutoConfiguration {

    /**
     * Creates the default {@link SearchQueryParser} from the bound properties.
     *
     * @param properties the {@code javalibs.search.*} properties
     * @return a parser configured from the properties
     */
    @Bean
    @ConditionalOnMissingBean
    public SearchQueryParser searchQueryParser(SearchProperties properties) {
        return new SearchQueryParser(new ParserConfig(
                properties.getFilterParam(),
                properties.getSortParam(),
                properties.getPageParam(),
                properties.getSizeParam(),
                properties.getDefaultPageSize(),
                properties.getMaxPageSize(),
                properties.getDefaultCombinator()));
    }
}
