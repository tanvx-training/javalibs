package io.javalibs.authz;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PermissionCatalogTest {

    @Test
    void knowsRegisteredCodes() {
        PermissionCatalog catalog = new PermissionCatalog(Set.of("issue.read", "issue.update"));
        assertThat(catalog.contains("issue.read")).isTrue();
        assertThat(catalog.contains("issue.delete")).isFalse();
        assertThat(catalog.codes()).containsExactlyInAnyOrder("issue.read", "issue.update");
    }

    @Test
    void requireKnownThrowsListingUnknownCodes() {
        PermissionCatalog catalog = new PermissionCatalog(Set.of("issue.read"));
        catalog.requireKnown(List.of("issue.read"));
        assertThatExceptionOfType(UnknownPermissionException.class)
                .isThrownBy(() -> catalog.requireKnown(List.of("issue.read", "bogus.perm")))
                .withMessageContaining("bogus.perm");
    }

    @Test
    void rejectsEmptyCatalogAndBlankCodes() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PermissionCatalog(Set.of()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PermissionCatalog(Set.of(" ")));
    }
}
