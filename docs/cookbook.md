# Cookbook — Công thức cho các tình huống thực tế

Mỗi công thức là các bước tối thiểu để đạt một mục tiêu cụ thể. Chi tiết API xem tài liệu module tương ứng.

## 1. Dựng service mới từ đầu

Xem [Getting Started](getting-started.md) — quy trình đầy đủ 7 bước.

## 2. Thêm API tìm kiếm động cho một entity

**Cần:** `javalibs-search-spring-boot-starter`.

```java
public interface OrderRepository
        extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {
}

@GetMapping("/api/orders")
ApiResponse<PageResponse<OrderDto>> search(SearchQuery query) {   // resolver tự parse HTTP params
    Page<Order> page = orderRepository.findAll(
            SpecificationBuilder.toSpecification(query), PageRequests.of(query));
    return ApiResponse.ok(PageResponse.of(   // 2xx luôn bọc envelope ApiResponse (web.md)
            page.getContent().stream().map(OrderDto::from).toList(),
            page.getNumber(), page.getSize(), page.getTotalElements()));
}
```

Client gọi:

```
GET /api/orders?filter=status:eq:OPEN&filter=total:between:100,500&filter=customer.name:like:nguyen&sort=createdAt,desc&page=0&size=20
```

An toàn mặc định: tên field bị whitelist bằng regex, giá trị bind qua Criteria API (không SQL injection), `size` bị clamp về `max-page-size`. Chi tiết cú pháp: [search.md](modules/search.md).

## 3. Bắn event an toàn với Transactional Outbox

**Vấn đề:** lưu DB xong nhưng rớt mạng khi publish Kafka → dữ liệu lệch âm thầm.

**Cần:** `javalibs-datahub-spring-boot-starter` + `javalibs-persistence-spring-boot-starter`.

Bước 1 — migration tạo bảng (copy từ `META-INF/datahub/outbox-schema-postgres.sql` trong jar `javalibs-datahub-spring`):

```
src/main/resources/db/migration/V202607141100__datahub_outbox.sql
```

Bước 2 — bật outbox:

```yaml
spring:
  kafka:
    bootstrap-servers: kafka.internal:9092
    producer:
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
javalibs:
  datahub:
    outbox:
      enabled: true
```

Bước 3 — enqueue trong cùng transaction với business writes:

```java
@ApplicationService
public class PlaceOrderService {

    private final OrderRepository orders;
    private final TransactionalOutbox outbox;

    @Transactional
    public UUID placeOrder(PlaceOrderCommand cmd) {
        Order order = orders.save(Order.place(cmd));
        outbox.enqueue("orders.events",
                EventEnvelope.of("OrderPlaced", "orders-service", OrderPlacedEvent.from(order)));
        return order.getId();
    }
}
```

Relay nền tự publish sau commit (mỗi 5s, retry, park `FAILED` sau 10 lần). Giao hàng at-least-once ⇒ làm tiếp công thức 4.

## 4. Consumer idempotent (chống xử lý trùng message)

**Cần:** bảng `datahub_processed_event` (cùng file DDL trên) + `javalibs.datahub.idempotency.enabled=true`.

```java
@Component
public class OrderEventsListener {

    private final IdempotentEventProcessor idempotent;

    @KafkaListener(topics = "orders.events")
    @Transactional
    public void onEvent(EventEnvelope<JsonNode> envelope) {
        idempotent.process("OrderEventsListener", envelope, () -> {
            // business logic — chạy đúng 1 lần cho mỗi eventId.
            // Marker + business writes commit nguyên tử;
            // action ném exception → rollback cả hai → redelivery an toàn.
        });
    }
}
```

## 5. Thu hồi token khi user logout-all / đổi mật khẩu

**Cần:** `spring-boot-starter-data-redis` + cấu hình:

```yaml
spring:
  data:
    redis:
      host: redis.internal
javalibs:
  security:
    blacklist:
      mode: redis
```

Tại identity service (nơi phát token):

```java
@PostMapping("/api/auth/logout-all")
void logoutAll(@CurrentUser UserContext user, TokenBlacklist blacklist) {
    // Với mỗi token đang sống của user (lưu jti khi phát hành):
    blacklist.revoke(jti, expiresAt);   // entry tự hết hạn cùng token
}
```

Mọi resource service có blacklist `redis` cùng Redis sẽ từ chối token đó ngay lập tức (401, `RevokedTokenException`). **Điều kiện:** token phải có claim `jti` khi phát hành.

## 6. Chuyển service sang Keycloak (OAuth2/OIDC)

Không đổi một dòng code nghiệp vụ — `@RequireRole`, `@CurrentUser`, `UserContextHolder` hoạt động y hệt:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

```yaml
javalibs:
  security:
    mode: oauth2-resource-server
    jwt:
      roles-claim: realm_access.roles   # nếu cần map claim khác — hoặc giữ "roles"
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://keycloak.internal/realms/myrealm
```

Chữ ký được verify qua JWKS của Keycloak (tự xoay key). Lưu ý: claim lồng nhau kiểu `realm_access.roles` của Keycloak cần converter tùy biến — override bean `UserContextJwtAuthenticationConverter` (xem [security.md](modules/security.md)).

## 7. Định nghĩa bộ mã lỗi nghiệp vụ

```java
public final class OrderErrors {
    public static final BusinessErrorCode NOT_FOUND      = BusinessErrorCode.of("ERR-ORDER-001", 404);
    public static final BusinessErrorCode ALREADY_PAID   = BusinessErrorCode.of("ERR-ORDER-002", 409);
    public static final BusinessErrorCode LIMIT_EXCEEDED = BusinessErrorCode.of("ERR-ORDER-003", 400);

    private OrderErrors() {}
}

// Trong service:
throw new ApiException(OrderErrors.ALREADY_PAID, "Order " + id + " has already been paid");
```

Response tự động:

```json
{
  "timestamp": "2026-07-14T10:00:00Z",
  "status": 409,
  "code": "ERR-ORDER-002",
  "message": "Order 42 has already been paid",
  "path": "/api/orders/42/payments",
  "traceId": "8b6f..."
}
```

Frontend switch theo `code`, không parse message. Format lỗi này cũng tự xuất hiện trong Swagger docs của mọi endpoint ([openapi.md](modules/openapi.md)). Duy trì file `ERROR-CODES.md` trong repo service liệt kê catalog.

## 8. Cache một query nặng với TTL riêng

**Cần:** `javalibs-cache-spring-boot-starter` + `@EnableCaching` trên application class. Dùng Redis thì thêm `spring-boot-starter-data-redis`.

```yaml
javalibs:
  cache:
    caches:
      top-products: { ttl: 5m }
```

```java
@Cacheable(cacheNames = "top-products",
           key = "T(io.javalibs.cache.CacheKeys).join('by-category', #categoryId)")
public List<ProductDto> topProducts(String categoryId) { ... }
```

⚠️ Chỉ cache DTO nhỏ chuyên dụng (không cache entity JPA); đổi tên/di chuyển class DTO đã cache thì flush cache ([cache.md](modules/cache.md)).

## 9. Bảo vệ call sang service khác bằng Circuit Breaker

**Cần:** `javalibs-resilience-spring-boot-starter`.

Cách 1 — tự động cho mọi RestClient:

```yaml
javalibs:
  resilience:
    rest:
      enabled: true
```

Cách 2 — breaker riêng theo backend:

```java
CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("payment-service");
Supplier<PaymentResult> guarded =
        CircuitBreaker.decorateSupplier(breaker, () -> paymentClient.charge(req));
```

Khi backend lỗi ≥ 50% trong cửa sổ 20 call, mạch OPEN 30s: call fail ngay không chạm backend — service của bạn không bị treo thread theo, backend có thời gian hồi. Đổi ngưỡng qua `javalibs.resilience.circuit-breaker.*`.

## 10. Integration test có xác thực với Testcontainers

**Cần:** `javalibs-test` + `javalibs-security-test` (đều scope `test`), Docker chạy sẵn.

```yaml
# src/test/resources/application-test.yml
javalibs:
  security:
    jwt:
      secret: javalibs-test-secret-key-0123456789abcdef   # >= 32 bytes
```

```java
class OrderApiIT extends BaseIntegrationTest {  // PostgreSQL Testcontainers + profile "test"

    @Autowired TestRestTemplate rest;

    @Test
    void adminSeesOrders() {
        String bearer = JwtTestFactory.create()
                .subject("u-1").roles("ADMIN")
                .secret("javalibs-test-secret-key-0123456789abcdef")
                .bearer();

        var headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, bearer);
        var res = rest.exchange("/api/orders", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void expiredTokenIsRejected() {
        String bearer = JwtTestFactory.create()
                .issuedAt(Instant.now().minus(Duration.ofHours(2)))
                .expiresIn(Duration.ofHours(1))     // đã hết hạn 1 giờ
                .secret("javalibs-test-secret-key-0123456789abcdef")
                .bearer();
        // → 401
    }
}
```

## 11. Truy vết một request xuyên nhiều service

1. Mọi service dùng `javalibs-observability-spring-boot-starter` → mỗi request có `X-Correlation-Id` (tự sinh nếu thiếu) trong MDC + response header.
2. Khi gọi service khác, **forward header**: `builder.defaultHeader(CorrelationId.DEFAULT_HEADER, MDC.get(CorrelationId.MDC_KEY))` hoặc dùng interceptor chung của team.
3. Log pattern có `%X{correlationId} %X{traceId}` → grep một id ra toàn bộ hành trình trên ELK/Grafana.
4. Muốn distributed tracing đầy đủ: thêm `io.opentelemetry:opentelemetry-exporter-otlp` + `management.otlp.tracing.endpoint` ([observability.md](modules/observability.md)).

## 12. Tắt một tính năng của javalibs trong một service đặc thù

Mọi thứ có cờ — ví dụ service nội bộ không cần security:

```yaml
javalibs:
  security:
    enabled: false
  web:
    logging:
      enabled: false
```

Hoặc thay hẳn implementation: khai báo bean cùng type (`SecurityFilterChain`, `CacheManager`, `EventPublisher`, `GlobalExceptionHandler`...) — bean của javalibs tự lùi nhờ `@ConditionalOnMissingBean`.
