# javalibs-security-spring-boot-starter

Starter Spring Boot cho bộ thư viện bảo mật **javalibs-security**: chỉ cần thêm **một** dependency là service có ngay xác thực JWT stateless, lỗi 401/403 dạng JSON và các tiện ích `@RequireRole` / `@CurrentUser`. Module này không chứa code — nó chỉ gom `javalibs-security-core`, `javalibs-security-spring`, `javalibs-security-spring-boot-autoconfigure` và `spring-boot-starter-security`.

## Cài đặt

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-security-spring-boot-starter</artifactId>
</dependency>
```

## Cấu hình `application.yml`

```yaml
javalibs:
  security:
    enabled: true                       # mặc định true
    permit-all:                         # các đường dẫn không cần xác thực
      - /actuator/health
      - /actuator/info
      - /public/**
    jwt:
      secret: ${JWT_SECRET}             # secret HMAC, TỐI THIỂU 32 byte
      # public-key: |                   # hoặc dùng RSA public key (PEM) thay cho secret
      #   -----BEGIN PUBLIC KEY-----
      #   ...
      #   -----END PUBLIC KEY-----
      issuer: https://sso.example.com   # kiểm tra claim iss (tuỳ chọn)
      audience: orders-api              # kiểm tra claim aud (tuỳ chọn)
      clock-skew: 30s                   # độ lệch đồng hồ cho phép
      roles-claim: roles                # đổi nếu IdP dùng tên claim khác
      username-claim: preferred_username
      email-claim: email
      tenant-claim: tenant
```

> Bắt buộc cấu hình `jwt.secret` **hoặc** `jwt.public-key` (hoặc tự cung cấp bean `TokenValidator`). Nếu không, ứng dụng sẽ dừng ngay khi khởi động kèm thông báo hướng dẫn.

## Sử dụng trong controller

```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    // Tiêm người dùng hiện tại; null nếu endpoint permit-all và không có token
    @GetMapping("/me")
    public ProfileDto me(@CurrentUser UserContext user) {
        return ProfileDto.of(user.userId(), user.username(), user.email());
    }

    // Cần MỘT trong các role liệt kê (anyOf = true là mặc định)
    @RequireRole({"ADMIN", "SUPPORT"})
    @GetMapping("/{id}")
    public OrderDto get(@PathVariable String id) { ... }

    // Cần TẤT CẢ role liệt kê
    @RequireRole(value = {"ADMIN", "AUDITOR"}, anyOf = false)
    @GetMapping("/audit")
    public AuditReport audit() { ... }
}
```

`@RequireRole` cũng đặt được ở mức class (áp dụng cho mọi method; annotation ở method sẽ được ưu tiên). Ở tầng service, dùng `UserContextHolder.current()` (trả về `Optional`) hoặc `UserContextHolder.require()`.

## Viết test với `javalibs-security-test`

Thêm dependency (scope `test`):

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-security-test</artifactId>
  <scope>test</scope>
</dependency>
```

Trỏ secret của test về secret mặc định của factory, ví dụ `src/test/resources/application-test.yml`:

```yaml
javalibs:
  security:
    jwt:
      secret: javalibs-test-secret-key-0123456789abcdef   # JwtTestFactory.DEFAULT_TEST_SECRET
```

Sau đó mint token ngay trong test:

```java
@Test
void adminCanDeleteOrder() throws Exception {
    String bearer = JwtTestFactory.create()
            .subject("user-1")
            .username("jdoe")
            .roles("ADMIN")
            .tenant("acme")
            .bearer();               // "Bearer eyJhbGciOi..."

    mockMvc.perform(delete("/api/orders/42").header("Authorization", bearer))
           .andExpect(status().isNoContent());
}

@Test
void expiredTokenIsRejectedWith401() throws Exception {
    String expired = JwtTestFactory.create()
            .issuedAt(Instant.now().minus(Duration.ofHours(2)))
            .expiresIn(Duration.ofHours(1))   // exp = issuedAt + 1h => đã hết hạn
            .bearer();

    mockMvc.perform(get("/api/orders/me").header("Authorization", expired))
           .andExpect(status().isUnauthorized());
}
```

Cho unit test thuần (không đi qua HTTP), dùng fixture dựng sẵn: `TestUserContexts.admin()`, `TestUserContexts.user()`, `TestUserContexts.withRoles("MANAGER")`.

## Tuỳ biến

- Tự định nghĩa bean `TokenValidator`, `JwtAuthenticationFilter`, `RestAuthenticationEntryPoint`, `RestAccessDeniedHandler` hoặc `SecurityFilterChain` → bean mặc định tương ứng tự động lùi bước (`@ConditionalOnMissingBean`).
- `javalibs.security.enabled=false` → tắt toàn bộ module.

Xem thêm tài liệu chi tiết tại [`javalibs-security/README.md`](../README.md).
