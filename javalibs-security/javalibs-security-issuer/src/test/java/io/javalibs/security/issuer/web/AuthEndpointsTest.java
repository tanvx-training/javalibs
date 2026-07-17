package io.javalibs.security.issuer.web;

import io.javalibs.security.issuer.AuthenticationService;
import io.javalibs.security.issuer.InvalidCredentialsException;
import io.javalibs.security.issuer.InvalidRefreshTokenException;
import io.javalibs.security.issuer.TokenPair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthEndpointsTest {

    AuthenticationService service = Mockito.mock(AuthenticationService.class);
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AuthEndpoints(service)).build();
    }

    @Test
    void loginReturnsTokenPair() throws Exception {
        Mockito.when(service.login("alice", "s3cret")).thenReturn(new TokenPair(
                "access-jwt", Instant.parse("2026-07-17T00:15:00Z"),
                "refresh-opaque", Instant.parse("2026-08-16T00:00:00Z")));
        mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"s3cret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").value("access-jwt"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-opaque"));
    }

    @Test
    void invalidCredentialsBecome401Json() throws Exception {
        Mockito.when(service.login(any(), any()))
                .thenThrow(new InvalidCredentialsException("Invalid credentials"));
        mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"bad\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("ERR_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void invalidRefreshTokenBecomes401Json() throws Exception {
        Mockito.when(service.refresh(any()))
                .thenThrow(new InvalidRefreshTokenException("Refresh token is invalid"));
        mvc.perform(post("/auth/refresh").contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"stale\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logoutReturns204() throws Exception {
        mvc.perform(post("/auth/logout").contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"r\",\"accessTokenId\":\"jti-1\"}"))
                .andExpect(status().isNoContent());
        Mockito.verify(service).logout(eq("r"), eq("jti-1"));
    }
}
