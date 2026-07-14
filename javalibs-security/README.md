# javalibs-security

Thư viện xác thực (authentication) và phân quyền (authorization) dùng chung cho các service Java/Spring Boot của hệ sinh thái **javalibs**, theo mô hình **JWT stateless**.

## Mục đích

- Chuẩn hoá cách các service xác thực request bằng JWT (`Authorization: Bearer <token>`).
- Cung cấp sẵn `SecurityFilterChain` stateless, trả lỗi 401/403 dạng JSON thống nhất.
- Cung cấp các tiện ích phân quyền ở tầng controller: `@RequireRole`, `@CurrentUser`, `UserContextHolder`.
- Cung cấp thư viện hỗ trợ test (`JwtTestFactory`) để tự sinh token trong test mà không cần Identity Provider thật.

## Kiến trúc 5 module con

```
javalibs-security (pom tổng hợp)
├── javalibs-security-core                      # Java thuần, KHÔNG phụ thuộc Spring
├── javalibs-security-spring                    # Tích hợp Spring Security / Spring MVC
├── javalibs-security-spring-boot-autoconfigure # Tự động cấu hình cho Spring Boot
├── javalibs-security-spring-boot-starter       # Starter: chỉ cần thêm 1 dependency
└── javalibs-security-test                      # Hỗ trợ test: sinh token HS256, fixture UserContext
```

| Module | Nội dung chính | Khi nào dùng trực tiếp |
|---|---|---|
| `javalibs-security-core` | `UserContext`, `TokenValidator`, `JwtTokenValidator`, `JwtValidationConfig`, `InvalidTokenException` / `ExpiredTokenException` | Khi cần validate JWT ngoài môi trường Spring (worker, CLI, thư viện khác) |
| `javalibs-security-spring` | `JwtAuthenticationFilter`, `UserContextAuthenticationToken`, `RestAuthenticationEntryPoint` (401 JSON), `RestAccessDeniedHandler` (403 JSON), `UserContextHolder`, `@RequireRole` + interceptor, `@CurrentUser` + resolver | Khi tự dựng `SecurityFilterChain` riêng nhưng vẫn muốn tái dùng các thành phần chuẩn |
| `javalibs-security-spring-boot-autoconfigure` | `SecurityProperties` (`javalibs.security.*`), `JavalibsSecurityAutoConfiguration`, `SecurityWebMvcAutoConfiguration` | Hiếm khi dùng trực tiếp — thường đi qua starter |
| `javalibs-security-spring-boot-starter` | Chỉ gồm pom: gom core + spring + autoconfigure + `spring-boot-starter-security` | **Mặc định cho mọi service Spring Boot** |
| `javalibs-security-test` | `JwtTestFactory`, `TestUserContexts` | Thêm vào dependency với `scope=test` |

## Mô hình bảo mật: JWT stateless

1. Client gửi request kèm header `Authorization: Bearer <jwt>`.
2. `JwtAuthenticationFilter` tách token và giao cho `TokenValidator` (mặc định là `JwtTokenValidator`, hỗ trợ HS256 qua `secret` hoặc RS256 qua `public-key` PEM).
3. Token hợp lệ → claims được ánh xạ thành `UserContext` (userId = `sub`, roles, username, email, tenant, các claim còn lại vào `attributes`) và đặt vào `SecurityContextHolder` dưới dạng `UserContextAuthenticationToken` (mỗi role thành authority `ROLE_<role>`).
4. Token không hợp lệ → filter xoá security context, ghi lại lỗi vào request attribute và **cho request đi tiếp không xác thực**; `RestAuthenticationEntryPoint` sẽ trả về 401 JSON với message chính xác.
5. Không có session, không có CSRF token — server hoàn toàn stateless (`SessionCreationPolicy.STATELESS`, CSRF disabled). CORS không được cấu hình ở đây mà để tầng web đảm nhiệm; các request `OPTIONS` (preflight) luôn được permit.

Định dạng lỗi thống nhất:

```json
{"timestamp":"2026-07-13T10:00:00Z","status":401,"code":"ERR_UNAUTHORIZED","message":"Token has expired: ..."}
{"timestamp":"2026-07-13T10:00:00Z","status":403,"code":"ERR_FORBIDDEN","message":"Access denied: ..."}
```

## Bảng cấu hình (`javalibs.security.*`)

| Property | Kiểu | Mặc định | Ý nghĩa |
|---|---|---|---|
| `javalibs.security.enabled` | boolean | `true` | Bật/tắt toàn bộ auto-configuration |
| `javalibs.security.permit-all` | List\<String\> | `/actuator/health`, `/actuator/health/**`, `/actuator/info`, `/error`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | Các pattern không cần xác thực |
| `javalibs.security.jwt.secret` | String | — | Secret HMAC (HS256), **tối thiểu 32 byte**. Bắt buộc nếu không có `public-key` |
| `javalibs.security.jwt.public-key` | String (PEM) | — | Public key RSA (RS256) dạng PEM. Bắt buộc nếu không có `secret` |
| `javalibs.security.jwt.issuer` | String | — | Giá trị `iss` bắt buộc; bỏ trống thì không kiểm tra |
| `javalibs.security.jwt.audience` | String | — | Giá trị `aud` bắt buộc; bỏ trống thì không kiểm tra |
| `javalibs.security.jwt.clock-skew` | Duration | `30s` | Độ lệch đồng hồ cho phép khi kiểm tra `exp`/`nbf`/`iat` |
| `javalibs.security.jwt.roles-claim` | String | `roles` | Tên claim chứa danh sách role (chấp nhận JSON array hoặc chuỗi phân tách bằng dấu phẩy/khoảng trắng) |
| `javalibs.security.jwt.username-claim` | String | `preferred_username` | Tên claim chứa username |
| `javalibs.security.jwt.email-claim` | String | `email` | Tên claim chứa email |
| `javalibs.security.jwt.tenant-claim` | String | `tenant` | Tên claim chứa tenant id |

> Lưu ý fail-fast: nếu không cấu hình `secret` lẫn `public-key` (và không tự cung cấp bean `TokenValidator`), ứng dụng sẽ **không khởi động được** với thông báo lỗi hướng dẫn cụ thể.

## Bắt đầu nhanh

**1. Thêm starter:**

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-security-spring-boot-starter</artifactId>
</dependency>
```

**2. Cấu hình `application.yml`:**

```yaml
javalibs:
  security:
    permit-all:
      - /actuator/health
      - /public/**
    jwt:
      secret: ${JWT_SECRET}          # >= 32 byte
      issuer: https://sso.example.com
      audience: orders-api
```

**3. Dùng trong controller:**

```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @GetMapping("/me")
    public ProfileDto me(@CurrentUser UserContext user) {
        return ProfileDto.of(user.userId(), user.username(), user.tenantId());
    }

    @RequireRole("ADMIN")                                      // cần 1 trong các role liệt kê
    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) { ... }

    @RequireRole(value = {"ADMIN", "AUDITOR"}, anyOf = false)  // cần đủ TẤT CẢ role
    @GetMapping("/audit")
    public AuditReport audit() { ... }
}
```

Ở tầng service (không có tham số controller):

```java
UserContext user = UserContextHolder.require(); // ném IllegalStateException nếu chưa xác thực
```

**4. Viết test với `javalibs-security-test`:**

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-security-test</artifactId>
  <scope>test</scope>
</dependency>
```

```java
// application-test.yml: javalibs.security.jwt.secret = JwtTestFactory.DEFAULT_TEST_SECRET
mockMvc.perform(get("/api/orders/me")
        .header("Authorization", JwtTestFactory.create()
                .subject("user-1")
                .username("jdoe")
                .roles("ADMIN")
                .bearer()))
    .andExpect(status().isOk());
```

## Ghi đè (override) hành vi mặc định

Mọi bean đều khai báo `@ConditionalOnMissingBean`, vì vậy chỉ cần tự định nghĩa bean cùng loại:

- Tự cung cấp `TokenValidator` → thay cách validate token (ví dụ gọi introspection endpoint).
- Tự cung cấp `SecurityFilterChain` → toàn bộ filter chain mặc định lùi bước (backs off), nhưng vẫn có thể tiêm `JwtAuthenticationFilter`, `RestAuthenticationEntryPoint`... để tái dùng.
- Đặt `javalibs.security.enabled=false` → tắt hẳn module, Spring Boot quay về hành vi mặc định.

## Thu hồi token — Blacklist (mới)

JWT stateless không thể thu hồi trước khi hết hạn — lỗ hổng khi user đổi mật khẩu/bị khóa. Giải pháp: blacklist theo `jti` (token id), entry tự hết hạn cùng token nên store luôn nhỏ.

```yaml
javalibs:
  security:
    blacklist:
      mode: redis   # none (mặc định) | in-memory (chỉ 1 instance) | redis (chuẩn microservices)
```

- `redis` cần `spring-boot-starter-data-redis` + cấu hình `spring.data.redis.*`; key dạng `javalibs:security:revoked:<jti>` với TTL = thời gian còn lại của token.
- Thu hồi khi user logout-all/đổi mật khẩu: inject `TokenBlacklist` và gọi `blacklist.revoke(jti, expiresAt)`.
- `JwtAuthenticationFilter` tự kiểm tra sau khi validate chữ ký; token bị thu hồi → 401 (`RevokedTokenException`).
- **Token phải có claim `jti`** — `JwtTestFactory` giờ tự sinh `jti` mặc định (ghi đè bằng `.tokenId(...)`).

## Chế độ OAuth2 Resource Server / OIDC (mới)

Tích hợp IAM tập trung (Keycloak, Google...) mà không đổi một dòng code nghiệp vụ:

```yaml
javalibs:
  security:
    mode: oauth2-resource-server   # mặc định: jwt (tự validate)

spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://keycloak.internal/realms/myrealm
```

Thêm dependency `spring-boot-starter-oauth2-resource-server` vào service. Chữ ký được verify qua JWKS của IAM; `UserContextJwtAuthenticationConverter` map token về `UserContext` (dùng chung tên claim `javalibs.security.jwt.roles-claim`...), nên `UserContextHolder`, `@CurrentUser`, `@RequireRole` hoạt động **y hệt** chế độ `jwt` — chuyển đổi giữa hai chế độ là thay đổi thuần cấu hình.
