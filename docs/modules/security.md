# javalibs-security

> Xác thực và phân quyền **JWT stateless** dùng chung cho mọi service javalibs: validate token (HMAC/RSA hoặc ủy quyền cho OIDC provider), ánh xạ claims thành `UserContext`, lỗi 401/403 JSON thống nhất, `@RequireRole`/`@CurrentUser`, thu hồi token qua blacklist và bộ công cụ sinh token cho test.

## Artifacts

| Artifact (`groupId: io.javalibs`) | Nội dung | Phụ thuộc chính |
|---|---|---|
| `javalibs-security-core` | Java thuần, **không phụ thuộc Spring**: `UserContext`, `TokenValidator`, `JwtValidationConfig`, `JwtTokenValidator`, `TokenBlacklist`, `InMemoryTokenBlacklist`, hệ thống exception | `jjwt-api` (compile), `jjwt-impl`/`jjwt-jackson` (runtime), `slf4j-api` |
| `javalibs-security-spring` | Tích hợp Spring Security / Spring MVC: `JwtAuthenticationFilter`, `UserContextAuthenticationToken`, `RestAuthenticationEntryPoint`, `RestAccessDeniedHandler`, `UserContextHolder`, `@RequireRole` + interceptor, `@CurrentUser` + resolver, `RedisTokenBlacklist`, `UserContextJwtAuthenticationConverter` | core, `spring-security-web/config`, `spring-webmvc`, `jackson-databind`; optional: `spring-data-redis`, `spring-security-oauth2-resource-server`, `spring-security-oauth2-jose` |
| `javalibs-security-spring-boot-autoconfigure` | `SecurityProperties` (`javalibs.security.*`) + 4 auto-configuration | spring module, `spring-boot-autoconfigure`; optional: redis/oauth2 như trên |
| `javalibs-security-spring-boot-starter` | Không có code — gom core + spring + autoconfigure + `spring-boot-starter-security` | — |
| `javalibs-security-test` | `JwtTestFactory` (sinh JWT HS256), `TestUserContexts` (fixture) — dùng với `scope=test` | core, jjwt **compile scope** (để tự sinh token standalone) |
| `javalibs-security-issuer` | Phát hành token: `PasswordHasher`, `JwtIssuerConfig`, `TokenIssuer`, `AuthenticationService` (rotation + reuse detection), SPI `CredentialsStore`/`RefreshTokenStore`, endpoint `AuthEndpoints` (`/auth/login`, `/auth/refresh`, `/auth/logout`) — xem [Phát hành token (issuer)](#phát-hành-token-issuer) | core, `spring-security-crypto`, `spring-webmvc`, jjwt; optional trong `javalibs-security-spring-boot-autoconfigure` — thêm module này vào classpath để kích hoạt `SecurityIssuerAutoConfiguration` |

## Khi nào dùng / không dùng

**Dùng khi:**

- Xây REST API stateless nhận `Authorization: Bearer <jwt>` — thêm starter là có ngay `SecurityFilterChain` chuẩn.
- Cần API thống nhất để đọc thông tin người gọi (`UserContext`) ở controller lẫn service, độc lập với cách token được validate.
- Cần thu hồi token trước hạn (logout-all, đổi mật khẩu, khóa tài khoản) qua blacklist theo `jti`.
- Tích hợp Keycloak / OIDC provider (`mode=oauth2-resource-server`) mà không đổi code nghiệp vụ.
- Cần validate JWT **ngoài Spring** (worker, CLI): chỉ dùng `javalibs-security-core`.

**Không dùng khi:**

- Ứng dụng dùng session + form login truyền thống (module này ép stateless, tắt session/CSRF/form login).
- Cần OAuth2 **client** (đăng nhập lấy token) — module chỉ làm resource server / xác thực request đến.
- Ứng dụng WebFlux — auto-configuration chỉ kích hoạt cho servlet (`@ConditionalOnWebApplication(type = SERVLET)`).

## Mô hình bảo mật: luồng xử lý request

```
Request ──► JwtAuthenticationFilter (trước UsernamePasswordAuthenticationFilter)
             │  1. Tách token từ header "Authorization: Bearer <token>"
             │     (so khớp prefix "Bearer " không phân biệt hoa thường, trim; rỗng → coi như không có token)
             │  2. TokenValidator.validate(token) → UserContext
             │  3. Nếu có TokenBlacklist: kiểm tra attributes["jti"] — bị thu hồi → RevokedTokenException
             │
             ├─ Token hợp lệ ──► UserContextAuthenticationToken (kèm WebAuthenticationDetails)
             │                   đặt vào SecurityContextHolder → chain tiếp tục ĐÃ xác thực
             │
             ├─ Token không hợp lệ (InvalidTokenException, gồm cả Expired/Revoked)
             │   ──► SecurityContextHolder.clearContext()
             │       + stash exception vào request attribute JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE
             │       → chain tiếp tục KHÔNG xác thực
             │       → RestAuthenticationEntryPoint trả 401 JSON với message chính xác của exception
             │
             └─ Không có token ──► chain tiếp tục không xác thực
                                   (endpoint permit-all vẫn chạy; endpoint bảo vệ → 401)
```

Đặc điểm chain mặc định: `SessionCreationPolicy.STATELESS`, CSRF/httpBasic/formLogin/logout/requestCache đều tắt, mọi request `OPTIONS` (CORS preflight) được permit, CORS **không** cấu hình ở đây (để tầng web đảm nhiệm — xem [javalibs-web](web.md)).

Định dạng lỗi JSON thống nhất (các field theo đúng thứ tự):

```json
{"timestamp":"2026-07-13T10:00:00Z","status":401,"code":"ERR_UNAUTHORIZED","message":"Token has expired: ..."}
{"timestamp":"2026-07-13T10:00:00Z","status":403,"code":"ERR_FORBIDDEN","message":"Access denied: ..."}
```

## Các thành phần chính

### javalibs-security-core (package `io.javalibs.security`)

#### `UserContext` — record bất biến mô tả người gọi đã xác thực

```java
public record UserContext(
        String userId,                    // định danh user (thường là claim "sub")
        String username,                  // có thể null
        String email,                     // có thể null
        Set<String> roles,                // không bao giờ null, unmodifiable
        String tenantId,                  // có thể null
        Map<String, Object> attributes)   // các claim còn lại, không bao giờ null, unmodifiable
```

- Constructor chuẩn hóa `null` collection thành rỗng và copy phòng vệ (`Set.copyOf`/`Map.copyOf`) — không bao giờ phải null-check `roles()`/`attributes()`.
- `boolean hasRole(String role)` — so khớp **chính xác, phân biệt hoa thường**; `null` → `false`.
- `boolean hasAnyRole(String... candidates)` — `true` nếu có ít nhất 1 role; phần tử `null` bị bỏ qua.
- `static Builder builder()` — builder với các method: `userId`, `username`, `email`, `roles(Collection<String>)`, `roles(String...)` (thay thế toàn bộ), `role(String)` (thêm 1), `tenantId`, `attributes(Map)` (thay thế), `attribute(key, value)` (thêm 1), `build()`. Builder không thread-safe.

#### `TokenValidator` — SPI validate token

```java
@FunctionalInterface
public interface TokenValidator {
    UserContext validate(String token) throws InvalidTokenException;
}
```

Implementation phải thread-safe. Ném `ExpiredTokenException` khi token đúng chữ ký nhưng hết hạn, `InvalidTokenException` cho mọi lỗi khác.

#### `JwtValidationConfig` — record cấu hình cho `JwtTokenValidator`

| Field | Mặc định (constant) | Ý nghĩa |
|---|---|---|
| `hmacSecret` | — | Secret HMAC (HS256), tối thiểu 32 byte khi dùng |
| `rsaPublicKeyPem` | — | Public key RSA dạng PEM (X.509 SubjectPublicKeyInfo) |
| `issuer` | `null` (không kiểm tra) | Giá trị `iss` bắt buộc khi đặt |
| `audience` | `null` (không kiểm tra) | Giá trị `aud` bắt buộc khi đặt |
| `clockSkew` | `DEFAULT_CLOCK_SKEW` = `Duration.ofSeconds(30)` | Độ lệch đồng hồ cho phép với các claim thời gian |
| `rolesClaim` | `DEFAULT_ROLES_CLAIM` = `"roles"` | Tên claim chứa roles |
| `usernameClaim` | `DEFAULT_USERNAME_CLAIM` = `"preferred_username"` | Tên claim chứa username (chuẩn OIDC) |
| `emailClaim` | `DEFAULT_EMAIL_CLAIM` = `"email"` | Tên claim chứa email |
| `tenantClaim` | `DEFAULT_TENANT_CLAIM` = `"tenant"` | Tên claim chứa tenant id |

- **Bắt buộc** có ít nhất một trong `hmacSecret` / `rsaPublicKeyPem`, nếu không constructor ném `IllegalArgumentException`.
- Constructor chuẩn hóa: `clockSkew` null → 30s; các tên claim blank → giá trị mặc định.
- `JwtValidationConfig.builder()` đã điền sẵn mặc định; `clockSkew(null)` khôi phục mặc định 30s.

#### `JwtTokenValidator` — implementation dựa trên JJWT

```java
public JwtTokenValidator(JwtValidationConfig config)   // fail-fast tại constructor
public UserContext validate(String token) throws InvalidTokenException
```

- **HMAC**: secret được encode UTF-8 và **phải ≥ 32 byte** (yêu cầu của HS256), ngắn hơn → `IllegalArgumentException` ngay lúc khởi tạo.
- **RSA**: parse PEM X.509 SubjectPublicKeyInfo (khối `-----BEGIN PUBLIC KEY-----`/`-----END PUBLIC KEY-----`); PEM hỏng → `IllegalArgumentException`. Nếu **cả hai** được cấu hình, HMAC được ưu tiên (RSA bị bỏ qua).
- `issuer`/`audience` được enforce qua `requireIssuer`/`requireAudience` khi đặt; `clockSkew` áp cho mọi kiểm tra thời gian (`exp`, `nbf`, `iat`).
- `validate`: token `null`/blank → `InvalidTokenException`; hết hạn → `ExpiredTokenException`; mọi `JwtException` khác → `InvalidTokenException`.
- Ánh xạ claims → `UserContext`:
  - `sub` → `userId`; các claim theo tên cấu hình → `username`/`email`/`tenantId`;
  - claim roles chấp nhận **JSON array hoặc chuỗi phân tách bằng dấu phẩy/khoảng trắng** (split theo regex `[,\s]+`), phần tử được trim, bỏ phần tử blank;
  - mọi claim còn lại (trừ registered claims `sub/iss/aud/exp/nbf/iat/jti` và 4 claim đã map) → `attributes`;
  - nếu token có `jti`, giá trị được đưa vào `attributes` dưới key `TokenBlacklist.TOKEN_ID_ATTRIBUTE` (= `"jti"`) để phục vụ thu hồi.

#### Exceptions

| Exception | Kế thừa | Khi nào |
|---|---|---|
| `InvalidTokenException` | `RuntimeException` | Token malformed, sai chữ ký, sai issuer/audience... |
| `ExpiredTokenException` | `InvalidTokenException` | Token đúng chữ ký nhưng `exp` (± clock skew) đã qua |
| `RevokedTokenException` | `InvalidTokenException` | Token hợp lệ nhưng `jti` nằm trong `TokenBlacklist` |

Chỉ cần catch `InvalidTokenException` là bắt được cả ba.

#### `TokenBlacklist` + `InMemoryTokenBlacklist`

```java
public interface TokenBlacklist {
    String TOKEN_ID_ATTRIBUTE = "jti";               // key trong UserContext.attributes()
    void revoke(String tokenId, Instant expiresAt);  // thu hồi jti đến khi token tự hết hạn
    boolean isRevoked(String tokenId);
}
```

`InMemoryTokenBlacklist` (constructor mặc định hoặc `InMemoryTokenBlacklist(Clock)` cho test):

- Lưu trong `ConcurrentHashMap`, entry đã hết hạn được **dọn lazily mỗi lần ghi** và khi đọc trúng entry hết hạn.
- `revoke` với `expiresAt` đã qua là no-op. `isRevoked(null)` → `false`.
- **Chỉ dùng cho deployment 1 instance** — mỗi instance giữ list riêng; scale ngang phải dùng `RedisTokenBlacklist`.

### javalibs-security-spring (package `io.javalibs.security.spring`)

#### `JwtAuthenticationFilter` (extends `OncePerRequestFilter`)

```java
public JwtAuthenticationFilter(TokenValidator tokenValidator)                      // không kiểm tra thu hồi
public JwtAuthenticationFilter(TokenValidator tokenValidator, TokenBlacklist tb)   // có kiểm tra thu hồi
public static final String AUTH_ERROR_ATTRIBUTE
        = "io.javalibs.security.spring.JwtAuthenticationFilter.AUTH_ERROR";
```

Hành vi như sơ đồ ở trên. Token không có `jti` **không thể** bị thu hồi riêng lẻ (đi qua bước blacklist).

#### `UserContextAuthenticationToken` (extends `AbstractAuthenticationToken`)

- Principal là `UserContext`; luôn ở trạng thái authenticated (chỉ được tạo sau khi validate thành công).
- Mỗi role thành authority `ROLE_<role>`; **role đã có sẵn prefix `ROLE_` sẽ không bị prefix hai lần** → cả `hasRole("ADMIN")` lẫn `hasAuthority("ROLE_ADMIN")` đều dùng được.
- `getCredentials()` trả `""` (không bao giờ giữ lại token gốc); `getName()` trả `username`, fallback `userId`.

#### `RestAuthenticationEntryPoint` / `RestAccessDeniedHandler`

- Constructor: mặc định (tự tạo `ObjectMapper`) hoặc nhận `ObjectMapper` của ứng dụng.
- Body JSON gồm đúng 4 field: `timestamp` (ISO-8601 instant), `status` (401/403), `code`, `message`; `Content-Type: application/json`, charset UTF-8.
- Entry point (401, hằng số `ERROR_CODE = "ERR_UNAUTHORIZED"`): message lấy theo thứ tự ưu tiên — exception trong `AUTH_ERROR_ATTRIBUTE` → message của `AuthenticationException` → `"Authentication required"`.
- Denied handler (403, `ERROR_CODE = "ERR_FORBIDDEN"`): message của `AccessDeniedException`, fallback `"Access denied"`.

#### `UserContextHolder`

```java
public static Optional<UserContext> current();  // Optional.empty() nếu chưa xác thực
public static UserContext require();            // ném IllegalStateException nếu chưa xác thực
```

Dùng ở tầng service (không có tham số controller). Ưu tiên `@CurrentUser` trong controller.

#### `@RequireRole` + `RequireRoleInterceptor`

```java
@RequireRole("ADMIN")                                       // any-of, 1 role
@RequireRole({"ADMIN", "SUPPORT"})                          // any-of (mặc định anyOf = true)
@RequireRole(value = {"ADMIN", "AUDITOR"}, anyOf = false)   // all-of: cần đủ TẤT CẢ role
```

- Role ghi **không có** prefix `ROLE_`.
- Interceptor giải quyết annotation theo thứ tự: **method trước, class sau** (annotation ở method thắng annotation ở class); không có annotation → cho qua.
- Người gọi chưa xác thực bằng `UserContext` hoặc thiếu role → ném `AccessDeniedException`, Spring Security dịch thành **403** (hoặc **401** cho anonymous qua entry point).
- Đặt được ở `METHOD` và `TYPE` (áp cho mọi handler của controller).

#### `@CurrentUser` + `CurrentUserArgumentResolver`

- Resolver hỗ trợ **mọi** tham số controller kiểu `UserContext` — annotation `@CurrentUser` là tùy chọn, chỉ để làm rõ ý định.
- Request chưa xác thực → tham số nhận `null` (endpoint permit-all vẫn khai báo được).

#### `RedisTokenBlacklist`

```java
public static final String DEFAULT_KEY_PREFIX = "javalibs:security:revoked:";
public RedisTokenBlacklist(StringRedisTemplate redis)                    // dùng prefix mặc định
public RedisTokenBlacklist(StringRedisTemplate redis, String keyPrefix)
```

- Mỗi revocation là 1 key `<keyPrefix><jti>` (value `"1"`) với **TTL = thời gian sống còn lại của token** — Redis tự xóa entry đúng lúc token tự hết hạn, store không bao giờ phình.
- `revoke` với TTL ≤ 0 (token đã hết hạn) là no-op; `isRevoked` = `hasKey`.
- Chia sẻ giữa mọi instance → lựa chọn đúng cho microservices.

#### `UserContextJwtAuthenticationConverter`

```java
public UserContextJwtAuthenticationConverter(String rolesClaim, String usernameClaim,
        String emailClaim, String tenantClaim)
// implements Converter<Jwt, AbstractAuthenticationToken>
```

Cầu nối chế độ OAuth2 Resource Server: nhận `Jwt` đã được Spring verify qua JWKS của IAM, map claims (cùng quy tắc roles array/chuỗi phân tách) thành `UserContext` rồi bọc trong `UserContextAuthenticationToken` — nhờ đó `UserContextHolder`, `@CurrentUser`, `@RequireRole` hoạt động **y hệt** chế độ `jwt`.

### javalibs-security-spring-boot-autoconfigure (package `io.javalibs.security.autoconfigure`)

Bốn auto-configuration được đăng ký (theo thứ tự trong `AutoConfiguration.imports`):

| Auto-configuration | Điều kiện kích hoạt | Đăng ký |
|---|---|---|
| `SecurityBlacklistAutoConfiguration` | `enabled=true` (mặc định); chạy **sau** `RedisAutoConfiguration`, **trước** `JavalibsSecurityAutoConfiguration` | Bean `TokenBlacklist` theo `blacklist.mode`: `none` (mặc định — không bean, token không thể thu hồi), `in-memory` (`InMemoryTokenBlacklist`, chỉ 1 instance), `redis` (`RedisTokenBlacklist` — cần `spring-data-redis` trên classpath **và** bean `StringRedisTemplate`, Boot tự tạo khi cấu hình `spring.data.redis.*`) |
| `JavalibsSecurityAutoConfiguration` | servlet web app, có Spring Security, `enabled=true` **và** `mode=jwt` (cả hai mặc định); chạy trước `SecurityAutoConfiguration` của Boot | `javalibsTokenValidator` (**fail-fast**: `IllegalStateException` lúc khởi động nếu không có cả `jwt.secret` lẫn `jwt.public-key` và app không tự cung cấp `TokenValidator`), `javalibsJwtAuthenticationFilter` (tự nhận `TokenBlacklist` nếu có), `javalibsRestAuthenticationEntryPoint`, `javalibsRestAccessDeniedHandler` (dùng `ObjectMapper` của app nếu có), `javalibsSecurityFilterChain` (stateless như mô tả ở trên). Tất cả đều `@ConditionalOnMissingBean` |
| `JavalibsOAuth2ResourceServerAutoConfiguration` | như trên nhưng `mode=oauth2-resource-server` (không matchIfMissing); cần class `JwtDecoder`/`Jwt` trên classpath → app phải thêm `spring-boot-starter-oauth2-resource-server` | `javalibsUserContextJwtAuthenticationConverter` (dùng tên claim từ `javalibs.security.jwt.*`), entry point + denied handler (dùng chung với jwt mode), `javalibsOAuth2SecurityFilterChain` — chỉ tạo khi tồn tại bean `JwtDecoder` (Boot build từ `spring.security.oauth2.resourceserver.jwt.issuer-uri` hoặc `jwk-set-uri`) |
| `SecurityWebMvcAutoConfiguration` | servlet web app + Spring MVC, `enabled=true` — **hoạt động ở cả hai mode** | `WebMvcConfigurer` đăng ký `RequireRoleInterceptor` và `CurrentUserArgumentResolver` |

### javalibs-security-test (package `io.javalibs.security.test`)

#### `JwtTestFactory` — sinh JWT HS256 cho test

```java
public static final String DEFAULT_TEST_SECRET = "javalibs-test-secret-key-0123456789abcdef";
public static JwtTestFactory create();
```

Mặc định khi `create()`: secret = `DEFAULT_TEST_SECRET` (≥ 32 byte), `sub` = `"test-user"`, `jti` = **UUID ngẫu nhiên** (để token thu hồi được qua blacklist), không role, `iat` = "now" tại thời điểm build, hạn 1 giờ. Tên claim khớp mặc định của core (`roles`, `preferred_username`, `email`, `tenant`) nên token validate được ngay với `JwtTokenValidator` cấu hình cùng secret.

| Method | Ý nghĩa |
|---|---|
| `subject(String)` | Đặt claim `sub` |
| `username(String)` | Đặt claim `preferred_username` |
| `email(String)` | Đặt claim `email` |
| `roles(String...)` | **Thêm** role vào claim `roles` (JSON array) |
| `tenant(String)` | Đặt claim `tenant` |
| `issuer(String)` / `audience(String)` | Đặt claim `iss` / `aud` |
| `claim(String, Object)` | Thêm claim tùy ý |
| `issuedAt(Instant)` | Đặt claim `iat` (mặc định: now lúc build) |
| `expiresIn(Duration)` | Thời gian sống; luôn tính `exp = issuedAt + expiresIn` (mặc định 1h) |
| `secret(String)` | Đổi secret ký (phải ≥ 32 byte) |
| `tokenId(String)` | Ghi đè `jti`; **truyền `null` để mint token không có `jti`** |
| `token()` | Trả compact JWT đã ký HS256 |
| `bearer()` | Trả `"Bearer " + token()` — dùng thẳng làm giá trị header `Authorization` |

Sinh token **hết hạn**: đẩy `iat` về quá khứ — `issuedAt(Instant.now().minus(Duration.ofHours(2))).expiresIn(Duration.ofHours(1))`.

Instance là builder mutable, không thread-safe — tạo mới cho mỗi token.

#### `TestUserContexts` — fixture `UserContext` sẵn dùng

| Method | Trả về |
|---|---|
| `admin()` | userId `test-admin`, username `admin`, email `admin@test.local`, role `ADMIN` |
| `user()` | userId `test-user`, username `user`, email `user@test.local`, role `USER` |
| `withRoles(String... roles)` | userId `test-user`, username `user`, đúng các role truyền vào |

## Cấu hình

Toàn bộ thuộc tính namespace `javalibs.security.*` (bind vào `SecurityProperties`):

| Thuộc tính | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `javalibs.security.enabled` | boolean | `true` | Bật/tắt toàn bộ auto-configuration security (cả 4 auto-config) |
| `javalibs.security.mode` | String | `jwt` | `jwt` — thư viện tự validate token bằng `javalibs.security.jwt.*`; `oauth2-resource-server` — ủy quyền cho Spring Security OAuth2 Resource Server (cấu hình qua `spring.security.oauth2.resourceserver.jwt.*`). Cả hai mode expose cùng API `UserContext` |
| `javalibs.security.permit-all` | List\<String\> | `/actuator/health`, `/actuator/health/**`, `/actuator/info`, `/error`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | Các pattern (ant-style) truy cập không cần xác thực. **Đặt property này sẽ THAY THẾ toàn bộ list mặc định** |
| `javalibs.security.jwt.secret` | String | — | Secret HMAC verify token HS256, **tối thiểu 32 byte**. Bắt buộc một trong `secret`/`public-key` (mode `jwt`) |
| `javalibs.security.jwt.public-key` | String (PEM) | — | Public key RSA (X.509 SubjectPublicKeyInfo PEM) verify token RS256. Bắt buộc một trong `secret`/`public-key` (mode `jwt`) |
| `javalibs.security.jwt.issuer` | String | — | Giá trị `iss` kỳ vọng; bỏ trống thì không kiểm tra |
| `javalibs.security.jwt.audience` | String | — | Giá trị `aud` kỳ vọng; bỏ trống thì không kiểm tra |
| `javalibs.security.jwt.clock-skew` | Duration | `30s` | Độ lệch đồng hồ cho phép khi kiểm tra `exp`/`nbf`/`iat` |
| `javalibs.security.jwt.roles-claim` | String | `roles` | Tên claim chứa roles (JSON array hoặc chuỗi phân tách phẩy/khoảng trắng) |
| `javalibs.security.jwt.username-claim` | String | `preferred_username` | Tên claim chứa username |
| `javalibs.security.jwt.email-claim` | String | `email` | Tên claim chứa email |
| `javalibs.security.jwt.tenant-claim` | String | `tenant` | Tên claim chứa tenant id |
| `javalibs.security.blacklist.mode` | String | `none` | Backend thu hồi token: `none` (không thu hồi được), `in-memory` (chỉ deployment 1 instance), `redis` (chia sẻ mọi instance; cần spring-data-redis + bean `StringRedisTemplate`) |
| `javalibs.security.blacklist.key-prefix` | String | `javalibs:security:revoked:` | Prefix key Redis cho entry thu hồi (chỉ dùng ở mode `redis`) |

Lưu ý: các tên claim trong `javalibs.security.jwt.*` được dùng **ở cả hai mode** (mode oauth2 dùng chúng cho `UserContextJwtAuthenticationConverter`).

## Phát hành token (issuer)

Module `javalibs-security` chỉ **validate** token; phát hành token (login/refresh/logout) là việc của `javalibs-security-issuer` — dùng cho service đóng vai trò identity/auth-service (ví dụ: gateway xác thực, hoặc chính service nghiệp vụ nếu chưa tách riêng). `SecurityIssuerAutoConfiguration` được đăng ký sẵn trong `javalibs-security-spring-boot-autoconfigure` nhưng có `@ConditionalOnClass(AuthenticationService.class)` — **chỉ kích hoạt khi thêm `javalibs-security-issuer` vào classpath** (dependency `<optional>true</optional>` từ phía autoconfigure).

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-security-spring-boot-starter</artifactId>
</dependency>
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-security-issuer</artifactId>
</dependency>
```

`AuthenticationService` (bean `javalibsAuthenticationService`) chỉ được tạo khi **cả hai** bean SPI `CredentialsStore` và `RefreshTokenStore` đều tồn tại — mặc định lấy từ `javalibs-authz-jpa` (`JpaCredentialsStore`/`JpaRefreshTokenStore`, đọc/ghi bảng `authz_user`/`authz_refresh_token`), hoặc tự cung cấp implementation riêng nếu không dùng `javalibs-authz-jpa`.

### Roles & custom claims trong token phát hành

`TokenIssuer` phát access token mang **roles** và **claim tùy ý** lấy từ `StoredCredentials`:

- `StoredCredentials(userId, username, email, passwordHash, enabled, roles, extraClaims)` — `roles` (`Set<String>`) ghi vào claim theo tên `javalibs.security.jwt.roles-claim` (mặc định `roles`, JSON array) khi không rỗng; `extraClaims` (`Map<String,Object>`) ghi các claim còn lại. Constructor 5 tham số cũ vẫn dùng được (roles/claims rỗng).
- `TokenIssuer.issue(userId, username, email, roles, extraClaims)` là overload mới; overload 3 tham số cũ giữ nguyên (không roles).
- Claim **reserved** (`sub`, `iss`, `aud`, `exp`, `nbf`, `iat`, `jti`) và các tên claim đã map (username/email/roles) **không thể bị `extraClaims` ghi đè** — chống giả mạo.
- `AuthenticationService.login`/`refresh` tự truyền `roles`/`extraClaims` của user xuống — service validate token đọc `roles` qua `javalibs.security.jwt.roles-claim` như thường, `@RequireRole` hoạt động ngay.

### Cấu hình `javalibs.security.issuer.*`

Bind vào `IssuerProperties`:

| Thuộc tính | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `javalibs.security.issuer.enabled` | boolean | `true` | Bật/tắt `SecurityIssuerAutoConfiguration` |
| `javalibs.security.issuer.private-key` | String (PEM PKCS#8) | — | RSA private key ký token RS256. Bỏ trống → ký HS256 bằng `javalibs.security.jwt.secret` |
| `javalibs.security.issuer.access-token-ttl` | Duration | `15m` | Thời gian sống access token phát hành |
| `javalibs.security.issuer.refresh-token-ttl` | Duration | `30d` | Thời gian sống refresh token phát hành |
| `javalibs.security.issuer.endpoints.enabled` | boolean | `true` | Đăng ký sẵn `AuthEndpoints` (`/auth/login`, `/auth/refresh`, `/auth/logout`) |
| `javalibs.security.issuer.endpoints.base-path` | String | `/auth` | Base path của 3 endpoint trên |

**HS256 vs RS256**: để trống `private-key` → issuer ký HS256 bằng chung `javalibs.security.jwt.secret` mà mọi service validate token đã cấu hình (đơn giản, phù hợp khi chỉ 1 service phát hành + validate). Đặt `private-key` (PEM PKCS#8) → issuer ký RS256; các service validate token khác đó cấu hình `javalibs.security.jwt.public-key` (PEM X.509 SubjectPublicKeyInfo) tương ứng — chỉ service phát hành giữ private key, phù hợp kiến trúc nhiều service validate, một service phát hành. Không cấu hình cả `jwt.secret` lẫn `issuer.private-key` → khởi động thất bại (`IllegalStateException`, bean `javalibsJwtIssuerConfig`).

### 3 endpoint dựng sẵn

`AuthEndpoints`, base path mặc định `/auth` (đổi qua `endpoints.base-path`):

**`POST {base-path}/login`** — xác thực username/password, phát hành cặp token mới (family mới):

```json
// Request
{"username": "alice", "password": "s3cret"}

// Response 200
{
  "tokenType": "Bearer",
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "accessTokenExpiresAt": "2026-07-18T10:15:00Z",
  "refreshToken": "9f1c3e7b...",
  "refreshTokenExpiresAt": "2026-08-17T10:00:00Z"
}
```

Sai username/password hoặc tài khoản bị `enabled=false` → **401** với message chung "Invalid credentials" (không phân biệt được lý do, chống dò username):

```json
{"timestamp":"2026-07-18T10:00:00Z","status":401,"code":"ERR_INVALID_CREDENTIALS","message":"Invalid credentials"}
```

**`POST {base-path}/refresh`** — xoay refresh token, phát hành cặp token mới cùng family:

```json
// Request
{"refreshToken": "9f1c3e7b..."}

// Response 200 — cấu trúc giống /login, accessToken và refreshToken đều MỚI
```

Token không tồn tại/hết hạn/đã bị thu hồi → **401**:

```json
{"timestamp":"2026-07-18T10:15:00Z","status":401,"code":"ERR_INVALID_REFRESH_TOKEN","message":"Refresh token is invalid"}
```

**`POST {base-path}/logout`** — thu hồi refresh token (và blacklist access token nếu có `accessTokenId`):

```json
// Request
{"refreshToken": "a2d8f61c...", "accessTokenId": "3f9e2b7a-...-jti"}
```

Response **204 No Content**. `accessTokenId` là claim `jti` của access token hiện tại (client tự giải mã JWT để lấy, hoặc backend của bạn expose sẵn); có thể bỏ trống nếu chỉ muốn thu hồi refresh token.

### Rotation + reuse detection

Mỗi lần `refresh` thành công: refresh token cũ bị đánh dấu `rotated` (revoke + ghi hash token thay thế), token mới được phát hành **trong cùng family** (`familyId` không đổi qua các lần xoay). Nếu một refresh token **đã bị rotate** lại được trình lên lần nữa (dấu hiệu bị đánh cắp và dùng sai thứ tự với bản gốc) → `AuthenticationService.refresh` gọi `RefreshTokenStore.revokeFamily(familyId, now)`, thu hồi **toàn bộ** refresh token còn sống trong family đó, buộc người dùng hợp lệ phải đăng nhập lại. Refresh token bị revoke qua `logout` (không phải do rotate) không kích hoạt reuse detection — chỉ chính token đó bị từ chối.

### Logout + blacklist

`logout` luôn revoke refresh token (idempotent — refresh token không tồn tại/đã revoke không phải lỗi). Muốn access token JWT (vốn stateless) cũng mất hiệu lực **trước khi tự hết hạn**, phải cấu hình blacklist của javalibs-security và truyền `accessTokenId` (`jti`):

```yaml
javalibs:
  security:
    blacklist:
      mode: redis   # hoặc in-memory cho single-instance; none (mặc định) = access token KHÔNG thể thu hồi qua logout
```

Không có bean `TokenBlacklist` (mode `none`, mặc định) → `logout` vẫn revoke refresh token nhưng access token đã phát hành tiếp tục hợp lệ tới khi tự hết hạn theo `access-token-ttl`. Xem thêm cơ chế blacklist tại [Thu hồi token — luồng logout-all](#4-thu-hồi-token--luồng-logout-all) và [cách app tự đọc `jti`](#cách-đọc-jti-từ-access-token-để-logout) ngay dưới đây.

#### Cách đọc `jti` từ access token để logout

Access token không tự lộ `accessTokenId` qua response `/login`/`/refresh` (chỉ có `accessToken` dạng JWT nguyên bản) — client backend tự giải mã claim `jti` từ JWT (không cần verify chữ ký để đọc claim, hoặc dùng `UserContextHolder`/`@CurrentUser` phía server rồi lấy `user.attributes().get(TokenBlacklist.TOKEN_ID_ATTRIBUTE)` như mô tả ở mục 4 bên dưới) trước khi gọi `/auth/logout`.

**Nhớ thêm base path vào permit-all**: `/auth/login` và `/auth/refresh` phải gọi được khi **chưa** có token — thêm `endpoints.base-path` (mặc định `/auth`) dạng ant-pattern vào `javalibs.security.permit-all`:

```yaml
javalibs:
  security:
    permit-all:
      - /actuator/health
      - /error
      - /auth/**   # /auth/login, /auth/refresh, /auth/logout
```

Quên bước này → `/auth/login` bị chặn bởi `JwtAuthenticationFilter` như mọi endpoint khác, trả 401 ngay cả khi credentials đúng.

## Hướng dẫn sử dụng

### 1. Thêm dependency

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-security-spring-boot-starter</artifactId>
</dependency>

<!-- Test support -->
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-security-test</artifactId>
  <scope>test</scope>
</dependency>
```

(Version quản lý bởi BOM `javalibs-dependencies` — xem [Kiến trúc](../architecture.md).)

### 2a. application.yml — HMAC (HS256)

```yaml
javalibs:
  security:
    permit-all:              # nhớ: đặt property này THAY THẾ list mặc định
      - /actuator/health
      - /actuator/health/**
      - /error
      - /public/**
    jwt:
      secret: ${JWT_SECRET}  # >= 32 byte, sinh ngẫu nhiên, KHÔNG commit vào git
      issuer: https://sso.example.com
      audience: orders-api
      clock-skew: 30s
```

### 2b. application.yml — RSA (RS256)

```yaml
javalibs:
  security:
    jwt:
      issuer: https://sso.example.com
      audience: orders-api
      public-key: |
        -----BEGIN PUBLIC KEY-----
        MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...
        -----END PUBLIC KEY-----
```

Chỉ service phát hành token giữ private key; các resource service chỉ cần public key.

### 3. Controller với `@RequireRole` + `@CurrentUser`

```java
import io.javalibs.security.UserContext;
import io.javalibs.security.spring.CurrentUser;
import io.javalibs.security.spring.RequireRole;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @GetMapping("/me")
    public ProfileDto me(@CurrentUser UserContext user) {   // null nếu endpoint permit-all và chưa đăng nhập
        return ProfileDto.of(user.userId(), user.username(), user.tenantId());
    }

    @RequireRole("ADMIN")                                   // cần 1 trong các role liệt kê (any-of)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) { ... }

    @RequireRole(value = {"ADMIN", "AUDITOR"}, anyOf = false) // cần đủ TẤT CẢ role
    @GetMapping("/audit")
    public AuditReport audit() { ... }
}
```

Ở tầng service (không có tham số controller):

```java
UserContext user = UserContextHolder.require(); // ném IllegalStateException nếu chưa xác thực
```

### 4. Thu hồi token — luồng logout-all

Bật blacklist Redis:

```yaml
javalibs:
  security:
    blacklist:
      mode: redis            # none | in-memory | redis

spring:
  data:
    redis:
      host: redis.internal
```

Endpoint logout thu hồi token hiện tại theo `jti`:

```java
import io.javalibs.security.TokenBlacklist;
import io.javalibs.security.UserContext;

@RestController
public class LogoutController {

    private final TokenBlacklist blacklist;
    private final Duration tokenTtl = Duration.ofHours(1); // = thời gian sống token mà IdP phát hành

    public LogoutController(TokenBlacklist blacklist) {
        this.blacklist = blacklist;
    }

    @PostMapping("/api/auth/logout")
    public void logout(@CurrentUser UserContext user) {
        Object jti = user.attributes().get(TokenBlacklist.TOKEN_ID_ATTRIBUTE); // key "jti"
        if (jti != null) {
            // exp không nằm trong attributes (là registered claim) — nếu service không tự phát hành
            // token, dùng cận trên an toàn = now + TTL tối đa của token.
            blacklist.revoke(jti.toString(), Instant.now().plus(tokenTtl));
        }
    }
}
```

Từ request kế tiếp, `JwtAuthenticationFilter` phát hiện `jti` bị thu hồi → `RevokedTokenException` → 401 JSON. Service phát hành token (biết chính xác `exp`) nên truyền `expiresAt` thật để entry Redis hết hạn đúng lúc. **Token phải có claim `jti`** — token không có `jti` không thể thu hồi riêng lẻ.

### 5. Test MockMvc với `JwtTestFactory`

```yaml
# src/test/resources/application-test.yml
javalibs:
  security:
    jwt:
      secret: javalibs-test-secret-key-0123456789abcdef   # = JwtTestFactory.DEFAULT_TEST_SECRET
```

```java
import io.javalibs.security.test.JwtTestFactory;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderControllerTest {

    @Autowired MockMvc mockMvc;

    @Test
    void adminCanDelete() throws Exception {
        mockMvc.perform(delete("/api/orders/42")
                        .header("Authorization", JwtTestFactory.create()
                                .subject("user-1")
                                .username("jdoe")
                                .roles("ADMIN")
                                .bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void expiredTokenIsRejectedWith401() throws Exception {
        String bearer = JwtTestFactory.create()
                .issuedAt(Instant.now().minus(Duration.ofHours(2)))
                .expiresIn(Duration.ofHours(1))            // exp = iat + 1h => đã hết hạn 1h
                .bearer();
        mockMvc.perform(get("/api/orders/me").header("Authorization", bearer))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_UNAUTHORIZED"));
    }
}
```

Unit test không cần token — dựng thẳng `SecurityContext` bằng fixture:

```java
SecurityContextHolder.getContext().setAuthentication(
        new UserContextAuthenticationToken(TestUserContexts.admin()));
```

### 6. Chuyển sang Keycloak (OAuth2 Resource Server)

Thêm dependency vào service:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

```yaml
javalibs:
  security:
    mode: oauth2-resource-server   # thay cho mode jwt mặc định
    jwt:
      roles-claim: roles           # đổi nếu Keycloak map role vào claim khác

spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://keycloak.internal/realms/myrealm
```

Chữ ký được verify qua JWKS của Keycloak (Boot tự build `JwtDecoder` từ `issuer-uri`); `UserContextJwtAuthenticationConverter` map token về `UserContext` → `UserContextHolder`, `@CurrentUser`, `@RequireRole` hoạt động **y hệt** — chuyển đổi giữa hai mode là thay đổi thuần cấu hình, không đổi một dòng code nghiệp vụ.

## Ghi đè & mở rộng

Mọi bean auto-configure đều có `@ConditionalOnMissingBean` — chỉ cần khai báo bean cùng loại là bean mặc định lùi bước:

- **`TokenValidator` riêng** — thay cách validate (ví dụ gọi introspection endpoint của IdP, token opaque). Chỉ cần trả về `UserContext`; toàn bộ phần còn lại (filter, error handler, `@RequireRole`...) dùng lại nguyên vẹn. Đây cũng là cách né fail-fast khi không dùng secret/public-key.
- **`SecurityFilterChain` riêng** — chain mặc định back off hoàn toàn (`@ConditionalOnMissingBean(SecurityFilterChain.class)`), nhưng `TokenValidator`, `JwtAuthenticationFilter`, entry point, denied handler vẫn được tạo để bạn tiêm vào chain của mình.
- **`TokenBlacklist` riêng** — implement interface (ví dụ backend JDBC) và khai báo bean; cả hai bean blacklist mặc định đều `@ConditionalOnMissingBean(TokenBlacklist.class)`.
- **`RestAuthenticationEntryPoint` / `RestAccessDeniedHandler` riêng** — đổi format lỗi (nên đồng bộ với error catalog của [javalibs-web](web.md)).
- **`UserContextJwtAuthenticationConverter` riêng** (mode oauth2) — ví dụ đọc role từ `realm_access.roles` lồng nhau của Keycloak thay vì claim phẳng.
- **Tắt hẳn module**: `javalibs.security.enabled=false` — cả 4 auto-configuration back off, Spring Boot quay về hành vi security mặc định.
- Dùng lẻ `javalibs-security-spring` (không qua starter) khi muốn tự dựng toàn bộ `SecurityFilterChain` nhưng tái dùng các thành phần chuẩn; dùng lẻ `javalibs-security-core` ngoài Spring.

## Testing

- **Integration test (MockMvc/WebTestClient)**: thêm `javalibs-security-test` scope test, đặt `javalibs.security.jwt.secret` = `JwtTestFactory.DEFAULT_TEST_SECRET` trong profile test, mint token bằng `JwtTestFactory.create()...bearer()` (xem ví dụ ở trên). Không cần Identity Provider thật.
- **Unit test controller/service**: dùng `TestUserContexts.admin()/user()/withRoles(...)` + `UserContextAuthenticationToken` đặt thẳng vào `SecurityContextHolder`; nhớ `SecurityContextHolder.clearContext()` trong `@AfterEach`.
- **Test blacklist**: `InMemoryTokenBlacklist(Clock)` nhận `Clock` cố định để kiểm soát thời gian; mode test dùng `javalibs.security.blacklist.mode=in-memory` để không cần Redis.
- **Test auto-configuration**: dùng `WebApplicationContextRunner` + `AutoConfigurations.of(JavalibsSecurityAutoConfiguration.class, ...)` như các test trong `javalibs-security-spring-boot-autoconfigure`.
- Testcontainers cho Redis thật: xem [javalibs-test](test.md).

## Lưu ý & bẫy thường gặp

1. **Fail-fast lúc khởi động**: mode `jwt` mà không cấu hình `jwt.secret` lẫn `jwt.public-key` (và không tự cung cấp `TokenValidator`) → ứng dụng **không khởi động được** (`IllegalStateException` với hướng dẫn cụ thể). Đây là chủ đích: tránh service chạy "mở toang".
2. **Secret ngắn hơn 32 byte** → `IllegalArgumentException` ngay khi tạo `JwtTokenValidator`. Độ dài tính theo **byte UTF-8**, không phải số ký tự.
3. **Đặt `permit-all` là THAY THẾ, không phải bổ sung** — list mặc định (`/actuator/health`, `/error`, swagger...) biến mất; nhớ liệt kê lại những pattern còn cần.
4. **Cấu hình cả `secret` lẫn `public-key`**: HMAC được ưu tiên, public key bị bỏ qua âm thầm — đừng cấu hình cả hai.
5. **Blacklist chỉ được enforce ở mode `jwt`**: `JwtAuthenticationFilter` mới kiểm tra blacklist; chain của mode `oauth2-resource-server` không consult `TokenBlacklist` (khi dùng OIDC, thu hồi token là việc của IdP).
6. **Token không có `jti` không thu hồi được** — luôn phát hành token kèm `jti` nếu cần revocation. `JwtTestFactory` tự sinh `jti` mặc định; `tokenId(null)` để test trường hợp thiếu.
7. **`in-memory` blacklist không dùng được khi scale ngang** — token bị thu hồi ở instance này vẫn hợp lệ ở instance khác. Microservices phải dùng `redis`.
8. **`hasRole`/`hasAnyRole` phân biệt hoa thường** và so khớp role **không** prefix `ROLE_`; ở tầng Spring Security expression thì `hasRole("ADMIN")` ⇔ `hasAuthority("ROLE_ADMIN")`.
9. **Tự khai báo `SecurityFilterChain`** → chain mặc định biến mất hoàn toàn, gồm cả permit-all và error handler; phải tự `addFilterBefore(jwtAuthenticationFilter, ...)` và tự cấu hình `exceptionHandling` nếu vẫn muốn hành vi cũ.
10. **`@RequireRole` chỉ chặn ở tầng Spring MVC handler** (HandlerInterceptor) — gọi nội bộ giữa các service method không bị chặn; logic phân quyền tầng service dùng `UserContextHolder.require()` + `hasRole/hasAnyRole` hoặc `@PreAuthorize`.
11. **`UserContextHolder` gắn với thread hiện tại** — sang thread khác (`@Async`, thread pool) sẽ mất context; truyền `UserContext` qua tham số, hoặc xem cơ chế propagation MDC bên [javalibs-observability](observability.md) làm hình mẫu (SecurityContext không được tự lan truyền).
12. **Mode `oauth2-resource-server` không có `JwtDecoder`** (quên `issuer-uri`/`jwk-set-uri` hoặc quên dependency `spring-boot-starter-oauth2-resource-server`) → chain của javalibs không được tạo; nếu Spring Security còn trên classpath, Boot áp chain mặc định của nó — kiểm tra log khởi động khi 401 trả về không đúng format JSON kỳ vọng.
13. **Clock skew mặc định 30s** — token "hết hạn" vẫn được chấp nhận thêm tối đa 30 giây; đặt `clock-skew: 0s` nếu cần nghiêm ngặt tuyệt đối.
14. **Quên thêm `/auth/**` vào `permit-all`** (issuer) → `JwtAuthenticationFilter` chặn ngay cả `/auth/login`, trả 401 dù credentials đúng — xem [Phát hành token (issuer)](#phát-hành-token-issuer). `javalibs-security-issuer` phải có mặt tường minh trên classpath (không tự kéo theo bởi starter) để `SecurityIssuerAutoConfiguration` kích hoạt; thiếu một trong hai bean `CredentialsStore`/`RefreshTokenStore` (mặc định do `javalibs-authz-jpa` cung cấp) → bean `AuthenticationService`/`AuthEndpoints` không được tạo, không có lỗi khởi động rõ ràng — kiểm tra log `@ConditionalOnBean` khi `/auth/*` trả 404.
