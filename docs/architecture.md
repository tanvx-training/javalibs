# Kiến trúc javalibs

## Mục tiêu thiết kế

1. **Nhất quán** — hàng chục microservices trả cùng một format lỗi, log cùng một correlation id, validate JWT cùng một cách.
2. **Chọn lọc** — service chỉ "nhúng" đúng tính năng nó cần; không có module nào bắt buộc.
3. **Không khóa tay** — mọi bean đều thay thế được; thư viện luôn "lùi" khi service tự định nghĩa.
4. **Test nhanh** — logic lõi thuần Java, unit test không cần Spring context.

## Mô hình 4 tầng

Mỗi module phức tạp được phân rã thành chuỗi:

```
┌─────────────────────────────────────────────────────────────────────┐
│  javalibs-<name>  (aggregator pom — không có code)                   │
│                                                                      │
│  ┌──────────┐   ┌───────────┐   ┌──────────────────────┐   ┌──────┐ │
│  │  -core   │──►│  -spring  │──►│ -spring-boot-        │──►│ -spring-boot- │
│  │          │   │           │   │   autoconfigure      │   │   starter     │
│  └──────────┘   └───────────┘   └──────────────────────┘   └──────┘ │
│   Java thuần     Tích hợp        @AutoConfiguration +      Chỉ pom: │
│   0 Spring       framework       @ConditionalOn*           gom deps │
└─────────────────────────────────────────────────────────────────────┘
```

| Tầng | Quy tắc bất di bất dịch |
|---|---|
| `-core` | **Không phụ thuộc Spring.** Chỉ Java thuần + thư viện thuần (jjwt, resilience4j-core...). Unit test tính bằng mili-giây. |
| `-spring` | Phụ thuộc core + đúng phần Spring cần thiết (`spring-context`, `spring-web`...). Các tích hợp nặng (Kafka, Redis, JPA) khai báo `<optional>true</optional>`. |
| `-spring-boot-autoconfigure` | Chứa `@AutoConfiguration` đăng ký qua `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. **Mọi** dependency bên thứ ba là `<optional>true</optional>`. **Mọi** bean có `@ConditionalOnMissingBean`. Tính năng nặng là opt-in (`enabled` mặc định `false`). |
| `-spring-boot-starter` | **Không có code Java.** Chỉ `pom.xml` gom core + spring + autoconfigure + các dependency runtime bắt buộc, và `README.md`. |

Module đơn giản (`javalibs-internal`, `javalibs-spring`, `javalibs-test`) là một jar duy nhất. `javalibs-ddd` có 2 tầng (core + spring, kèm auto-config tối giản ngay trong `-spring`). `javalibs-openapi`/`javalibs-persistence` chỉ cần autoconfigure + starter (không có logic thuần).

## Cách auto-configuration hoạt động

1. Service khai báo starter → Maven kéo autoconfigure jar vào classpath.
2. Spring Boot đọc `META-INF/spring/...AutoConfiguration.imports` của từng jar và nạp các class `@AutoConfiguration`.
3. Từng bean được cân nhắc theo điều kiện:
   - `@ConditionalOnClass` — thư viện tương ứng có trên classpath không? (đây là lý do dep trong autoconfigure phải `optional`: class điều kiện vắng mặt thì cấu hình im lặng bỏ qua, không nổ `ClassNotFoundException`)
   - `@ConditionalOnProperty` — cờ `javalibs.<module>.enabled` (mặc định bật với tính năng nhẹ, **tắt** với tính năng cần hạ tầng: outbox, idempotency, blacklist, rest circuit breaker)
   - `@ConditionalOnBean` — hạ tầng cần thiết đã có chưa? (`DataSource`, `KafkaTemplate`, `RedisConnectionFactory`...)
   - `@ConditionalOnMissingBean` — service đã tự định nghĩa thì thư viện lùi.
4. Thứ tự giữa các auto-configuration điều khiển bằng `before`/`after`/`afterName` (ví dụ: security của javalibs chạy **trước** `SecurityAutoConfiguration` của Boot; fallback `LoggingEventPublisher` chạy **sau** Kafka publisher).

**Triết lý back-off:** nếu bạn khai báo `SecurityFilterChain`, `CacheManager`, `EventPublisher`, `GlobalExceptionHandler`... của riêng mình, bean tương ứng của javalibs không được tạo. Không bao giờ phải "đấu tranh" với thư viện.

## BOM — quản lý version

```
javalibs-root (parent POM ─ pluginManagement, dependencyManagement third-party)
└── javalibs-dependencies (BOM ─ KHÔNG có parent)
```

- `javalibs-root` chỉ dùng **nội bộ** khi build thư viện: chốt Spring Boot 3.5.3, jjwt, resilience4j (BOM import), springdoc, mapstruct; cấu hình compiler `-parameters`, surefire, jacoco, enforcer.
- `javalibs-dependencies` là thứ duy nhất client import. Nó **cố ý không có parent** — import BOM này chỉ pin version các artifact `javalibs-*`, không rò rỉ bất kỳ version pin nào của Spring Boot hay thư viện thứ ba sang project của bạn. Service tự chọn version Spring Boot của mình (khuyến nghị trùng 3.5.x).
- Mọi jar artifact mới **bắt buộc** thêm vào BOM (build sẽ không nhắc — đây là quy tắc review, xem [contributing.md](contributing.md)).

## Bản đồ phụ thuộc giữa các module

```
internal ◄─── (không module nào phụ thuộc bắt buộc; dùng nội bộ khi cần)

ddd-core ◄── ddd-spring
cqrs-core ◄── cqrs-spring ◄── cqrs-autoconfigure ◄── cqrs-starter
web-core ◄── web-spring ◄── web-autoconfigure ◄── web-starter
security-core ◄── security-spring ◄── security-autoconfigure ◄── security-starter
      ▲                                                    (security-test → security-core)
datahub-core ◄── datahub-spring ◄── datahub-autoconfigure ◄── datahub-starter
search-core ◄── search-spring ◄── search-autoconfigure ◄── search-starter
observability-core ◄── observability-spring ◄── observability-autoconfigure ◄── starter
logging-core ◄── logging-logback
logging-core ◄── logging-spring ◄── logging-autoconfigure ◄── logging-starter
                    (logging-autoconfigure cũng phụ thuộc logging-logback)
cache-core ◄── cache-spring ◄── cache-autoconfigure ◄── cache-starter
resilience-spring ◄── resilience-autoconfigure ◄── resilience-starter
openapi-autoconfigure ◄── openapi-starter
persistence-autoconfigure ◄── persistence-starter
```

**Quy tắc:** các nhóm module KHÔNG phụ thuộc chéo nhau ở tầng compile. Sự "hợp tác" giữa chúng diễn ra qua **quy ước runtime**, ví dụ:

| Hợp tác | Cơ chế |
|---|---|
| `web` hiển thị traceId trong ErrorResponse | Đọc MDC key `traceId`/`correlationId` mà `observability` đặt |
| `openapi` mô tả đúng format lỗi của `web` | Schema `ErrorResponse` được xây bằng tay khớp record (không import class) |
| `security` cho phép truy cập Swagger | Path `/swagger-ui/**`, `/v3/api-docs/**` nằm trong `permit-all` mặc định |
| `datahub` outbox cần bảng DB | DDL tham chiếu ship trong jar; `persistence` (Flyway) chạy migration do service copy vào |
| `resilience` bảo vệ RestClient của `datahub` | Cả hai đều customize `RestClient.Builder` qua cơ chế chuẩn của Boot |
| `logging` thay thế request-logging filter cũ của `web` | `web` dùng `@ConditionalOnMissingClass("io.javalibs.logging.spring.HttpAccessLogFilter")` (chỉ tham chiếu tên class, không sinh dependency compile) |

## Quy ước đặt tên & namespace

| Thứ | Quy ước | Ví dụ |
|---|---|---|
| groupId | `io.javalibs` | |
| artifactId | `javalibs-<name>[-core\|-spring\|-spring-boot-autoconfigure\|-spring-boot-starter\|-test]` | `javalibs-security-spring-boot-starter` |
| Package | `io.javalibs.<name>[.spring\|.autoconfigure\|.test]` | `io.javalibs.security.autoconfigure` |
| Property | `javalibs.<name>.*` | `javalibs.security.jwt.secret` |
| Bean auto-config | tiền tố `javalibs` để tránh đụng tên | `javalibsSecurityFilterChain` |
| Mã lỗi chung | `ERR_<TÊN>` | `ERR_VALIDATION` |
| Mã lỗi nghiệp vụ | `ERR-<DOMAIN>-<SEQ>` | `ERR-USER-001` |
| Kafka headers | `x-event-id`, `x-event-type`, `x-correlation-id`, `x-source` | |
| Cache key | `<prefix>::<cacheName>::<key>` | `orders-service::orders::by-customer:42` |
| Correlation header | `X-Correlation-Id` | |

## Nguyên tắc code

- **Không Lombok** — thư viện dùng chung không áp đặt annotation processor lên client; ưu tiên `record` và immutability.
- Javadoc tiếng Anh trên mọi public type; README/tài liệu tiếng Việt.
- Exception rõ ràng, fail-fast với thông điệp hành động được (ví dụ thiếu JWT secret → chỉ rõ property cần đặt).
- Bảo mật mặc định: field whitelist trong search, chống log injection trong correlation id, catch-all 500 không rò rỉ chi tiết lỗi, table name validation trong outbox, `flyway clean` bị khóa.
