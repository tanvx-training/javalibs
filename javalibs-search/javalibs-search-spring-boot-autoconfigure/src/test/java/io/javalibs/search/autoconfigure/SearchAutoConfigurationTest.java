package io.javalibs.search.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.javalibs.search.SearchQuery.Combinator;
import io.javalibs.search.SearchQueryParser;
import io.javalibs.search.SearchQueryParser.ParserConfig;

import static org.assertj.core.api.Assertions.assertThat;

class SearchAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SearchAutoConfiguration.class));

    @Test
    void providesParserWithDefaultConfiguration() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SearchQueryParser.class);
            ParserConfig config = context.getBean(SearchQueryParser.class).config();
            assertThat(config.filterParam()).isEqualTo("filter");
            assertThat(config.sortParam()).isEqualTo("sort");
            assertThat(config.pageParam()).isEqualTo("page");
            assertThat(config.sizeParam()).isEqualTo("size");
            assertThat(config.defaultPageSize()).isEqualTo(20);
            assertThat(config.maxPageSize()).isEqualTo(100);
            assertThat(config.defaultCombinator()).isEqualTo(Combinator.AND);
        });
    }

    @Test
    void appliesPropertyOverrides() {
        contextRunner
                .withPropertyValues(
                        "javalibs.search.filter-param=q",
                        "javalibs.search.sort-param=order-by",
                        "javalibs.search.page-param=p",
                        "javalibs.search.size-param=limit",
                        "javalibs.search.default-page-size=5",
                        "javalibs.search.max-page-size=50",
                        "javalibs.search.default-combinator=or")
                .run(context -> {
                    assertThat(context).hasSingleBean(SearchQueryParser.class);
                    ParserConfig config = context.getBean(SearchQueryParser.class).config();
                    assertThat(config.filterParam()).isEqualTo("q");
                    assertThat(config.sortParam()).isEqualTo("order-by");
                    assertThat(config.pageParam()).isEqualTo("p");
                    assertThat(config.sizeParam()).isEqualTo("limit");
                    assertThat(config.defaultPageSize()).isEqualTo(5);
                    assertThat(config.maxPageSize()).isEqualTo(50);
                    assertThat(config.defaultCombinator()).isEqualTo(Combinator.OR);
                });
    }

    @Test
    void backsOffWhenUserDefinesParserBean() {
        SearchQueryParser userParser = new SearchQueryParser(
                new ParserConfig("custom", "sort", "page", "size", 10, 30, Combinator.OR));

        contextRunner
                .withBean("customSearchQueryParser", SearchQueryParser.class, () -> userParser)
                .run(context -> {
                    assertThat(context).hasSingleBean(SearchQueryParser.class);
                    assertThat(context.getBean(SearchQueryParser.class)).isSameAs(userParser);
                });
    }
}
