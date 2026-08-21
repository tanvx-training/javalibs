package io.javalibs.logging;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    void ignoresForwardedHeadersWhenTheProxyIsNotTrusted() {
        ClientIpResolver resolver = new ClientIpResolver(false);
        Map<String, String> headers = Map.of(ClientIpResolver.HEADER_FORWARDED_FOR, "1.2.3.4");

        assertThat(resolver.resolve(headers::get, "10.0.0.1")).isEqualTo("10.0.0.1");
    }

    @Test
    void usesTheFirstForwardedForEntryWhenTheProxyIsTrusted() {
        ClientIpResolver resolver = new ClientIpResolver(true);
        Map<String, String> headers =
                Map.of(ClientIpResolver.HEADER_FORWARDED_FOR, "203.0.113.7, 10.0.0.5, 10.0.0.6");

        assertThat(resolver.resolve(headers::get, "10.0.0.1")).isEqualTo("203.0.113.7");
    }

    @Test
    void fallsBackToRemoteAddressWhenTheForwardedValueIsNotAnAddress() {
        ClientIpResolver resolver = new ClientIpResolver(true);
        Map<String, String> headers =
                Map.of(ClientIpResolver.HEADER_FORWARDED_FOR, "not an ip\r\nInjected: header");

        assertThat(resolver.resolve(headers::get, "10.0.0.1")).isEqualTo("10.0.0.1");
    }

    @Test
    void fallsBackToRealIpHeaderWhenForwardedForIsAbsent() {
        ClientIpResolver resolver = new ClientIpResolver(true);
        Map<String, String> headers = Map.of(ClientIpResolver.HEADER_REAL_IP, "198.51.100.9");

        assertThat(resolver.resolve(headers::get, "10.0.0.1")).isEqualTo("198.51.100.9");
    }

    @Test
    void acceptsIpv6Addresses() {
        ClientIpResolver resolver = new ClientIpResolver(true);
        Map<String, String> headers = Map.of(ClientIpResolver.HEADER_FORWARDED_FOR, "2001:db8::1");

        assertThat(resolver.resolve(headers::get, "10.0.0.1")).isEqualTo("2001:db8::1");
    }

    @Test
    void returnsNullWhenNothingIsAvailable() {
        assertThat(new ClientIpResolver(false).resolve(name -> null, null)).isNull();
    }
}
