# Tham chiếu cấu hình javalibs

Toàn bộ thuộc tính `javalibs.*` của mọi module, kèm kiểu, giá trị mặc định và ý nghĩa. Mọi thuộc tính đều bind qua `@ConfigurationProperties` nên có autocomplete trong IDE (metadata sinh bởi `spring-boot-configuration-processor`).

Quy ước chung:
- Cờ `enabled` của tính năng **nhẹ** mặc định `true` (có starter là chạy).
- Cờ `enabled` của tính năng **cần hạ tầng** (outbox, idempotency, blacklist, rest circuit breaker, CORS) mặc định `false`/`none` — opt-in chủ động.

## javalibs.cqrs — [tài liệu module](modules/cqrs.md)

| Thuộc tính | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `javalibs.cqrs.enabled` | boolean | `true` | Bật/tắt CommandBus + QueryBus |

## javalibs.web — [tài liệu module](modules/web.md)

| Thuộc tính | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `javalibs.web.exception-handler.enabled` | boolean | `true` | GlobalExceptionHandler → JSON lỗi chuẩn |
| `javalibs.web.logging.enabled` | boolean | `true` | Log 1 dòng INFO/request: method, URI, status, duration |
| `javalibs.web.logging.include-payload` | boolean | `false` | Log cả body request/response (chỉ JSON & text) |
| `javalibs.web.logging.max-payload-length` | int | `2048` | Cắt payload khi log |
| `javalibs.web.logging.excluded-paths` | List | `["/actuator/**"]` | Ant patterns bỏ qua |
| `javalibs.web.cors.enabled` | boolean | `false` | Bật CorsFilter |
| `javalibs.web.cors.allowed-origins` | List | — | Origins được phép |
| `javalibs.web.cors.allowed-methods` | List | `GET,POST,PUT,PATCH,DELETE,OPTIONS` | |
| `javalibs.web.cors.allowed-headers` | List | `*` | |
| `javalibs.web.cors.exposed-headers` | List | — | |
| `javalibs.web.cors.allow-credentials` | boolean | `false` | |
| `javalibs.web.cors.max-age` | long (giây) | `3600` | Cache preflight |
| `javalibs.web.cors.path` | String | `/**` | Pattern áp dụng CORS |

## javalibs.security — [tài liệu module](modules/security.md)

| Thuộc tính | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `javalibs.security.enabled` | boolean | `true` | Bật/tắt toàn bộ module security |
| `javalibs.security.mode` | String | `jwt` | `jwt` (tự validate) \| `oauth2-resource-server` (ủy quyền cho IAM/OIDC) |
| `javalibs.security.permit-all` | List | `/actuator/health`, `/actuator/health/**`, `/actuator/info`, `/error`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | Path không cần xác thực |
| `javalibs.security.jwt.secret` | String | — | HMAC secret (≥ 32 bytes). Bắt buộc secret **hoặc** public-key ở mode `jwt` |
| `javalibs.security.jwt.public-key` | String | — | RSA public key PEM (X.509) |
| `javalibs.security.jwt.issuer` | String | — | Kiểm tra claim `iss` khi đặt |
| `javalibs.security.jwt.audience` | String | — | Kiểm tra claim `aud` khi đặt |
| `javalibs.security.jwt.clock-skew` | Duration | `30s` | Dung sai đồng hồ khi kiểm tra hạn |
| `javalibs.security.jwt.roles-claim` | String | `roles` | Claim chứa roles (JSON array hoặc chuỗi phân tách bởi `,`/space) |
| `javalibs.security.jwt.username-claim` | String | `preferred_username` | |
| `javalibs.security.jwt.email-claim` | String | `email` | |
| `javalibs.security.jwt.tenant-claim` | String | `tenant` | |
| `javalibs.security.blacklist.mode` | String | `none` | `none` \| `in-memory` (1 instance) \| `redis` (chuẩn microservices) |
| `javalibs.security.blacklist.key-prefix` | String | `javalibs:security:revoked:` | Prefix key Redis |

Mode `oauth2-resource-server` dùng thêm cấu hình chuẩn Boot: `spring.security.oauth2.resourceserver.jwt.issuer-uri`.

## javalibs.datahub — [tài liệu module](modules/datahub.md)

| Thuộc tính | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `javalibs.datahub.source` | String | — (fallback `spring.application.name`) | Tên logical của service trong `EventEnvelope.source` |
| `javalibs.datahub.kafka.enabled` | boolean | `true` | KafkaEventPublisher (khi có bean `KafkaTemplate`) |
| `javalibs.datahub.kafka.send-timeout` | Duration | `30s` | Timeout của publish đồng bộ |
| `javalibs.datahub.rest.enabled` | boolean | `true` | Bean `datahubRestClientBuilder` (timeout + retry) |
| `javalibs.datahub.rest.connect-timeout` | Duration | `5s` | |
| `javalibs.datahub.rest.read-timeout` | Duration | `10s` | |
| `javalibs.datahub.rest.max-retries` | int | `3` | Retry IOException/5xx (chỉ method idempotent) |
| `javalibs.datahub.rest.initial-backoff` | Duration | `200ms` | Backoff nhân đôi mỗi lần |
| `javalibs.datahub.outbox.enabled` | boolean | **`false`** | Transactional Outbox (cần bảng + DataSource) |
| `javalibs.datahub.outbox.table` | String | `datahub_outbox_event` | |
| `javalibs.datahub.outbox.batch-size` | int | `100` | Số row claim mỗi lượt relay |
| `javalibs.datahub.outbox.max-attempts` | int | `10` | Quá số lần → status `FAILED` |
| `javalibs.datahub.outbox.poll-interval` | Duration | `5s` | Chu kỳ relay |
| `javalibs.datahub.outbox.use-skip-locked` | boolean | `true` | `FOR UPDATE SKIP LOCKED` (PostgreSQL/MySQL 8+); tắt với H2 |
| `javalibs.datahub.outbox.relay-enabled` | boolean | `true` | Tắt khi dùng Debezium/CDC thay relay polling |
| `javalibs.datahub.idempotency.enabled` | boolean | **`false`** | Dedup consumer (cần bảng) |
| `javalibs.datahub.idempotency.table` | String | `datahub_processed_event` | |

## javalibs.search — [tài liệu module](modules/search.md)

| Thuộc tính | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `javalibs.search.filter-param` | String | `filter` | Tên HTTP param chứa điều kiện lọc |
| `javalibs.search.sort-param` | String | `sort` | |
| `javalibs.search.page-param` | String | `page` | |
| `javalibs.search.size-param` | String | `size` | |
| `javalibs.search.default-page-size` | int | `20` | |
| `javalibs.search.max-page-size` | int | `100` | `size` bị clamp về ngưỡng này |
| `javalibs.search.default-combinator` | AND\|OR | `AND` | Cách nối các điều kiện lọc |

## javalibs.observability — [tài liệu module](modules/observability.md)

| Thuộc tính | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `javalibs.observability.correlation.enabled` | boolean | `true` | CorrelationIdFilter (đọc/sinh + gắn MDC + response header) |
| `javalibs.observability.correlation.header` | String | `X-Correlation-Id` | |
| `javalibs.observability.metrics.enabled` | boolean | `true` | MeterRegistryCustomizer gắn common tags |
| `javalibs.observability.metrics.common-tags` | Map | `{}` | Tags gắn vào mọi metric (vd `team: payments`) |
| `javalibs.observability.metrics.environment` | String | — | Thêm tag `environment` khi đặt |

## javalibs.cache — [tài liệu module](modules/cache.md)

| Thuộc tính | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `javalibs.cache.enabled` | boolean | `true` | (chỉ kích hoạt khi app có `@EnableCaching`) |
| `javalibs.cache.provider` | AUTO\|REDIS\|CAFFEINE | `AUTO` | AUTO ưu tiên Redis khi có `RedisConnectionFactory` |
| `javalibs.cache.key-prefix` | String | `spring.application.name` | Key: `<prefix>::<cacheName>::<key>` |
| `javalibs.cache.default-ttl` | Duration | `10m` | TTL cho cache không khai báo riêng |
| `javalibs.cache.cache-null-values` | boolean | `false` | |
| `javalibs.cache.caches.<tên>.ttl` | Duration | — | TTL riêng theo tên cache |
| `javalibs.cache.caches.<tên>.max-size` | Long | — | Giới hạn entry (chỉ Caffeine) |

## javalibs.resilience — [tài liệu module](modules/resilience.md)

| Thuộc tính | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `javalibs.resilience.circuit-breaker.failure-rate-threshold` | float (%) | `50` | Tỉ lệ lỗi để ngắt mạch |
| `javalibs.resilience.circuit-breaker.slow-call-rate-threshold` | float (%) | `100` | |
| `javalibs.resilience.circuit-breaker.slow-call-duration-threshold` | Duration | `2s` | Call chậm hơn ngưỡng = slow |
| `javalibs.resilience.circuit-breaker.sliding-window-size` | int | `20` | |
| `javalibs.resilience.circuit-breaker.minimum-number-of-calls` | int | `10` | Tối thiểu trước khi tính tỉ lệ |
| `javalibs.resilience.circuit-breaker.wait-duration-in-open-state` | Duration | `30s` | Thời gian OPEN trước khi thăm dò lại |
| `javalibs.resilience.circuit-breaker.permitted-number-of-calls-in-half-open-state` | int | `5` | |
| `javalibs.resilience.retry.max-attempts` | int | `3` | Tổng số lần (gồm lần đầu) |
| `javalibs.resilience.retry.wait-duration` | Duration | `500ms` | |
| `javalibs.resilience.retry.exponential-backoff-multiplier` | double | `2.0` | |
| `javalibs.resilience.rate-limiter.limit-for-period` | int | `50` | |
| `javalibs.resilience.rate-limiter.limit-refresh-period` | Duration | `1s` | |
| `javalibs.resilience.rate-limiter.timeout-duration` | Duration | `0` | 0 = fail fast khi hết permit |
| `javalibs.resilience.rest.enabled` | boolean | **`false`** | Gắn circuit breaker vào mọi `RestClient.Builder` |
| `javalibs.resilience.rest.circuit-breaker-name` | String | `rest-client` | |

## javalibs.openapi — [tài liệu module](modules/openapi.md)

| Thuộc tính | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `javalibs.openapi.enabled` | boolean | `true` | |
| `javalibs.openapi.title` | String | `<spring.application.name> API` | |
| `javalibs.openapi.description` | String | — | |
| `javalibs.openapi.version` | String | `v1` | |
| `javalibs.openapi.servers` | List | `[]` | Base URLs (vd gateway) |
| `javalibs.openapi.contact.name` / `.email` / `.url` | String | — | Team sở hữu API |
| `javalibs.openapi.security.enabled` | boolean | `true` | Scheme bearer JWT + nút Authorize |
| `javalibs.openapi.security.scheme-name` | String | `bearerAuth` | |
| `javalibs.openapi.global-responses.enabled` | boolean | `true` | Inject 400/401/403/404/409/500 vào mọi operation |

## javalibs.persistence — [tài liệu module](modules/persistence.md)

| Thuộc tính | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `javalibs.persistence.flyway.enabled` | boolean | `true` | Áp default an toàn của platform |
| `javalibs.persistence.flyway.clean-disabled` | boolean | `true` | Chặn `flyway clean` (bảo vệ production) |
| `javalibs.persistence.flyway.validate-migration-naming` | boolean | `true` | Bắt buộc `V<version>__<desc>.sql` |
| `javalibs.persistence.flyway.out-of-order` | boolean | `false` | Lịch sử migration tuyến tính |
| `javalibs.persistence.flyway.baseline-on-migrate` | boolean | `false` | Baseline schema cũ là quyết định chủ động |

Mọi cấu hình Flyway khác (locations, placeholders...) vẫn theo chuẩn `spring.flyway.*`.

## Ví dụ `application.yml` tổng hợp

```yaml
spring:
  application:
    name: orders-service

javalibs:
  security:
    jwt:
      secret: ${JWT_SECRET}
    blacklist:
      mode: redis
  web:
    cors:
      enabled: true
      allowed-origins: ["https://app.example.com"]
    logging:
      include-payload: false
  datahub:
    outbox:
      enabled: true
    idempotency:
      enabled: true
  cache:
    caches:
      orders: { ttl: 30s }
      customers: { ttl: 1h, max-size: 5000 }
  resilience:
    rest:
      enabled: true
  observability:
    metrics:
      environment: prod
      common-tags:
        team: orders
  openapi:
    title: Orders API
    contact: { name: Orders Team, email: orders@example.com }
```
