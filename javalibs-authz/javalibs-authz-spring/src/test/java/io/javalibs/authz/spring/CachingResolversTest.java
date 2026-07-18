package io.javalibs.authz.spring;

import io.javalibs.authz.ResolvedGrant;
import io.javalibs.authz.Scope;
import io.javalibs.authz.Subject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CachingResolversTest {

    @Test
    void grantResolverCachesPerSubjectAndEvicts() {
        AtomicInteger calls = new AtomicInteger();
        CachingGrantResolver resolver = new CachingGrantResolver(subject -> {
            calls.incrementAndGet();
            return List.of(new ResolvedGrant(Scope.GLOBAL, Set.of("p")));
        }, Duration.ofMinutes(1), 100);

        Subject u1 = Subject.user("u1");
        resolver.resolveGrants(u1);
        resolver.resolveGrants(u1);
        assertThat(calls.get()).isEqualTo(1);

        resolver.evictSubject(u1);
        resolver.resolveGrants(u1);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void groupResolverCachesPerUserAndEvicts() {
        AtomicInteger calls = new AtomicInteger();
        CachingGroupMembershipResolver resolver = new CachingGroupMembershipResolver(userId -> {
            calls.incrementAndGet();
            return Set.of("g1");
        }, Duration.ofMinutes(1), 100);

        resolver.groupsOf("u1");
        resolver.groupsOf("u1");
        assertThat(calls.get()).isEqualTo(1);

        resolver.evictUser("u1");
        resolver.groupsOf("u1");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void invalidatorEvictsUserFromBothCaches() {
        AtomicInteger grantCalls = new AtomicInteger();
        AtomicInteger groupCalls = new AtomicInteger();
        CachingGrantResolver grants = new CachingGrantResolver(subject -> {
            grantCalls.incrementAndGet();
            return List.of();
        }, Duration.ofMinutes(1), 100);
        CachingGroupMembershipResolver groups = new CachingGroupMembershipResolver(userId -> {
            groupCalls.incrementAndGet();
            return Set.of();
        }, Duration.ofMinutes(1), 100);
        CachingAuthzInvalidator invalidator = new CachingAuthzInvalidator(grants, groups);

        grants.resolveGrants(Subject.user("u1"));
        groups.groupsOf("u1");
        invalidator.evictUser("u1");
        grants.resolveGrants(Subject.user("u1"));
        groups.groupsOf("u1");

        assertThat(grantCalls.get()).isEqualTo(2);
        assertThat(groupCalls.get()).isEqualTo(2);
    }
}
