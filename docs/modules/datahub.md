# javalibs-datahub

> Chuẩn hóa eventing giữa các service: envelope sự kiện thống nhất (`EventEnvelope`), `EventPublisher` chạy trên Kafka (kèm fallback ghi log), **Transactional Outbox** để publish nguyên tử với ghi DB, **idempotent consumer** chống xử lý trùng, và `RestClient.Builder` cấu hình sẵn timeout + retry.

## Artifacts

| artifactId | Packaging | Mô tả |
|---|---|---|
| `javalibs-datahub` | pom | Parent gộp 4 submodule |
| `javalibs-datahub-core` | jar | Contract thuần Java, **không phụ thuộc Spring**: `EventEnvelope`, `EventPublisher`, `EventHandler`, `TransactionalOutbox`, `ProcessedEventStore`, `DatahubException`. Package `io.javalibs.datahub` |
| `javalibs-datahub-spring` | jar | Hiện thực Spring: `LoggingEventPublisher`, `kafka.KafkaEventPublisher`, `rest.RetryingClientHttpRequestInterceptor` + `rest.DatahubRestClients`, `outbox.JdbcTransactionalOutbox` + `outbox.JdbcOutboxRelay` + `outbox.OutboxRelayConfig` + `outbox.OutboxRelayScheduler`, `idempotency.JdbcProcessedEventStore` + `idempotency.IdempotentEventProcessor`. Kèm DDL tham chiếu `META-INF/datahub/outbox-schema-postgres.sql`. spring-kafka / spring-web / spring-jdbc / spring-tx / jackson-databind đều *optional* |
| `javalibs-datahub-spring-boot-autoconfigure` | jar | 5 auto-configuration + `DatahubProperties` (namespace `javalibs.datahub.*`). Package `io.javalibs.datahub.autoconfigure` |
| `javalibs-datahub-spring-boot-starter` | jar | Starter không chứa code: kéo core + spring + autoconfigure + **spring-kafka** |

## Khi nào dùng / không dùng

**Dùng khi:**

- Các service trao đổi sự kiện qua Kafka và muốn mọi sự kiện mang cùng bộ metadata (`eventId`, `eventType`, `source`, `correlationId`, `partitionKey`...).
- Cần publish sự kiện **nguyên tử với ghi DB** (chống dual-write): bật Transactional Outbox.
- Consumer cần chống xử lý trùng khi broker giao at-least-once: bật idempotency.
- Cần gọi HTTP service khác với timeout + retry mặc định mà không tự viết interceptor.

**Không dùng khi:**

- Chỉ cần event trong cùng một JVM — dùng `DomainEvent` của [javalibs-ddd](ddd.md) hoặc `ApplicationEventPublisher` của Spring.
- Cần exactly-once end-to-end tuyệt đối — outbox + idempotency cho **at-least-once + dedup phía consumer**, không phải exactly-once ở tầng transport.
- Khối lượng outbox cực lớn khiến polling không đáp ứng — vẫn dùng bảng outbox nhưng thay relay bằng Debezium/CDC (đặt `relay-enabled=false`, xem bên dưới).
- Cần circuit breaker cho HTTP call — kết hợp thêm [javalibs-resilience](resilience.md) (module này chỉ có retry).

## Các thành phần chính

### `EventEnvelope<T>` (record, `javalibs-datahub-core`)

Envelope bất biến, độc lập transport, bọc payload nghiệp vụ với metadata chuẩn:

```java
public record EventEnvelope<T>(
    String eventId,        // id duy nhất của lần phát sinh sự kiện
    String eventType,      // loại logic, vd "order.created" — BẮT BUỘC, không blank
    String source,         // tên logic của hệ thống phát; có thể null
    Instant occurredAt,    // thời điểm xảy ra
    String correlationId,  // liên kết với request/workflow gốc; có thể null
    String partitionKey,   // key routing cho transport có partition (Kafka); có thể null
    Map<String, String> headers, // header bổ sung; defensive copy, không bao giờ null
    T payload)             // sự kiện nghiệp vụ — BẮT BUỘC
```

Validation trong compact constructor:

- `eventType` `null`/blank → `IllegalArgumentException("eventType must not be null or blank")`.
- `payload` `null` → `NullPointerException`.
- `headers` `null` → thay bằng `Map.of()`; khác `null` → `Map.copyOf` (immutable).

Hai cách tạo:

- `static <T> EventEnvelope<T> of(String eventType, String source, T payload)` — eventId là UUID ngẫu nhiên, `occurredAt = Instant.now()`, không correlationId/partitionKey/headers.
- `static <T> Builder<T> builder()` — Builder với các method `eventId`, `eventType`, `source`, `occurredAt`, `correlationId`, `partitionKey`, `header(name, value)`, `headers(Map)` (null thì bỏ qua), `payload`, `build()`. Khi `build()`, field chưa đặt rơi về mặc định: `eventId` = UUID ngẫu nhiên, `occurredAt` = `Instant.now()`.

### `EventPublisher` (interface, `javalibs-datahub-core`)

Contract publish sự kiện, code nghiệp vụ chỉ phụ thuộc interface này:

- `void publish(String topic, EventEnvelope<?> event)` — đồng bộ, block đến khi transport xác nhận; lỗi ném `DatahubException`.
- `default CompletableFuture<Void> publishAsync(String topic, EventEnvelope<?> event)` — mặc định chỉ bọc `publish` vào future đã hoàn tất/đã fail; implementation transport được khuyến khích override bằng bản non-blocking thật.

### `EventHandler<T>` (interface, `javalibs-datahub-core`)

Contract cho consumer đăng ký handler theo từng loại sự kiện: `String eventType()` (loại đăng ký) và `void handle(EventEnvelope<T> event)`. Hạ tầng consumer (vd Kafka listener) dispatch envelope tới handler có `eventType()` khớp.

### `TransactionalOutbox` (interface, `javalibs-datahub-core`)

`void enqueue(String topic, EventEnvelope<?> event)` — ghi sự kiện vào bảng outbox để publish bất đồng bộ sau commit. **Hợp đồng bắt buộc: phải gọi bên trong transaction DB đang persist business state** — implementation từ chối lời gọi ngoài transaction, vì ngoài transaction thì outbox pattern không còn bảo đảm nguyên tử nào cả.

### `ProcessedEventStore` (interface, `javalibs-datahub-core`)

Store dedup cho consumer idempotent:

- `boolean markProcessed(String handlerName, String eventId)` — ghi nhận cặp `(handler, eventId)` nguyên tử; `true` khi lần đầu, `false` khi đã tồn tại (giao trùng → bỏ qua).
- `int deleteOlderThan(Instant cutoff)` — dọn marker cũ (bản trùng thường đến trong vài phút, không cần giữ marker vĩnh viễn).

Gọi trong transaction DB của listener để marker và business writes commit (hoặc rollback) cùng nhau.

### `DatahubException extends RuntimeException`

Ném bởi các component datahub khi publish/xử lý sự kiện thất bại (lỗi transport, timeout, lỗi serialize). Constructor `(String message)` và `(String message, Throwable cause)`.

### `KafkaEventPublisher` (`io.javalibs.datahub.spring.kafka`)

`EventPublisher` chạy trên `KafkaTemplate<String, Object>`:

- Constructor: `KafkaEventPublisher(KafkaTemplate<String, Object> template)` (timeout mặc định **30s**) hoặc `(template, Duration sendTimeout)`.
- Mỗi envelope gửi thành `ProducerRecord(topic, key = event.partitionKey(), value = envelope)` — **key chính là `partitionKey`** (có thể `null`), value là **toàn bộ envelope**.
- Metadata chuẩn thành Kafka record header (UTF-8, giá trị `null` bị bỏ qua), hằng public trên class:

| Hằng | Header | Nguồn |
|---|---|---|
| `HEADER_EVENT_ID` | `x-event-id` | `eventId()` |
| `HEADER_EVENT_TYPE` | `x-event-type` | `eventType()` |
| `HEADER_CORRELATION_ID` | `x-correlation-id` | `correlationId()` |
| `HEADER_SOURCE` | `x-source` | `source()` |

  cộng thêm toàn bộ `event.headers()` tùy biến.
- `publish` block bằng `future.get(sendTimeout)`; mọi thất bại dịch thành `DatahubException`: interrupt (đặt lại interrupt flag), lỗi thực thi (cause gốc được giữ), timeout (`"Timed out after <timeout> publishing event ..."`).
- `publishAsync` trả về chính future của `template.send` map sang `Void` (non-blocking thật).
- **Lưu ý bắt buộc**: envelope chỉ serialize được thành JSON khi producer dùng serializer JSON, vd `spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer`.

### `LoggingEventPublisher` (`io.javalibs.datahub.spring`)

Fallback an toàn: `publish` chỉ ghi log INFO (`topic`, `eventId`, `eventType`), không gửi đi đâu — để code phụ thuộc `EventPublisher` vẫn chạy ở local/dev không có Kafka.

### `RetryingClientHttpRequestInterceptor` (`io.javalibs.datahub.spring.rest`)

`ClientHttpRequestInterceptor` retry trong suốt các lỗi tạm thời: **`IOException` và response 5xx**.

- Constructor: `(int maxAttempts, Duration initialBackoff)` — chỉ retry method idempotent; hoặc `(maxAttempts, initialBackoff, boolean retryAllMethods)`. `maxAttempts < 1` hoặc `initialBackoff` null/âm → `IllegalArgumentException`.
- `maxAttempts` là **tổng số lần thử kể cả lần đầu**; backoff exponential: bắt đầu `initialBackoff`, **nhân đôi** sau mỗi lần thất bại (vd 3 lần thử với 200ms: chờ 200ms rồi 400ms). Mỗi lần retry log WARN.
- Mặc định chỉ retry method idempotent: **GET, HEAD, OPTIONS, PUT, DELETE**; POST (và method khác) đi thẳng qua không retry, trừ khi `retryAllMethods = true`.
- Hết lượt: nếu lần cuối là `IOException` → rethrow exception cuối; nếu lần cuối vẫn 5xx → **trả nguyên response 5xx** cho caller (không ném). Bị interrupt khi backoff → `IOException`.

### `DatahubRestClients` (`io.javalibs.datahub.spring.rest`)

Factory tĩnh: `static RestClient.Builder defaultBuilder(Duration connectTimeout, Duration readTimeout, RetryingClientHttpRequestInterceptor retry)` — builder dùng `JdkClientHttpRequestFactory` trên JDK `HttpClient` (connect timeout đặt trên client, read timeout trên request factory), gắn interceptor retry (`retry = null` thì tắt retry). Builder vẫn mở để tùy biến tiếp (`baseUrl`, header mặc định, converter...).

### `JdbcTransactionalOutbox` (`io.javalibs.datahub.spring.outbox`)

`TransactionalOutbox` trên JDBC. Constructor `(JdbcOperations jdbc, ObjectMapper objectMapper, String tableName)` — tên bảng được validate chống SQL injection (chỉ chữ, số, `_`, `.`; sai → `IllegalArgumentException`).

Hành vi `enqueue(topic, event)`:

- `topic` null/blank → `IllegalArgumentException`.
- **Ngoài transaction → `IllegalStateException`** (kiểm tra bằng `TransactionSynchronizationManager.isActualTransactionActive()`). Message nói rõ ý: *enqueue phải chạy bên trong transaction DB đang persist business state — hãy annotate service method bằng `@Transactional`; nếu không, outbox pattern không cho bảo đảm nguyên tử nào* (nguyên văn có chứa chuỗi `@Transactional`).
- INSERT một row `status = 'PENDING'`, `attempts = 0` qua chính transaction của caller (`JdbcOperations` tham gia transaction Spring trên cùng `DataSource`, kể cả transaction do `JpaTransactionManager` điều khiển): `headers` và `payload` serialize JSON bằng `ObjectMapper` (lỗi → `DatahubException`), `payload_type` = tên class đầy đủ của payload, `created_at` = `occurredAt` của envelope (fallback `Instant.now()`).

### `JdbcOutboxRelay` (`io.javalibs.datahub.spring.outbox`)

Poll các row `PENDING` và publish qua `EventPublisher` đang active. Constructor `(JdbcOperations, TransactionOperations, EventPublisher, ObjectMapper, OutboxRelayConfig)`.

`int relayBatch()` — chạy **một pass trong một transaction riêng**, trả về số sự kiện publish thành công:

1. **Claim** row bằng query khóa hàng:

   ```sql
   SELECT id, topic, event_type, source, correlation_id, partition_key,
          headers_json, payload_json, attempts, created_at
   FROM <table>
   WHERE status = 'PENDING'
   ORDER BY created_at
   FETCH FIRST <batchSize> ROWS ONLY
   FOR UPDATE SKIP LOCKED   -- SKIP LOCKED chỉ khi useSkipLocked = true
   ```

   Nhờ `FOR UPDATE SKIP LOCKED`, nhiều instance service relay song song không tranh nhau row (không double-publish trong cửa sổ khóa) — tổng thể vẫn là **at-least-once**.
2. **Từng row được cô lập lỗi**: dựng lại `EventEnvelope<JsonNode>` (payload đọc bằng `objectMapper.readTree`; `eventId` = cột `id`, `occurredAt` = `created_at`) rồi `publisher.publish(topic, envelope)`.
   - Thành công → `UPDATE ... SET status = 'PUBLISHED', published_at = now`.
   - Thất bại (mọi `Exception`) → `attempts + 1`, `last_error` = `e.toString()` cắt còn tối đa 1000 ký tự; nếu `attempts >= maxAttempts` → `status = 'FAILED'` (đỗ lại chờ xử lý tay, log ERROR), ngược lại giữ `PENDING` (log WARN) chờ pass sau. **Một row hỏng (poison) không chặn các row còn lại trong batch.**

### `OutboxRelayConfig` (record, `io.javalibs.datahub.spring.outbox`)

`record OutboxRelayConfig(String tableName, int batchSize, int maxAttempts, boolean useSkipLocked)` — validate: tên bảng hợp lệ, `batchSize > 0`, `maxAttempts > 0`. `useSkipLocked` thêm `SKIP LOCKED` vào query claim (PostgreSQL, MySQL 8+, Oracle; **tắt cho H2**).

### `OutboxRelayScheduler` (`io.javalibs.datahub.spring.outbox`)

Chạy `relayBatch()` theo fixed-delay trên **một daemon thread riêng tên `datahub-outbox-relay`**. Implement `SmartLifecycle` nên tự start sau khi context sẵn sàng và stop êm khi shutdown (chờ tối đa 5s rồi `shutdownNow`) — **không cần `@EnableScheduling`** trong ứng dụng. Constructor `(JdbcOutboxRelay relay, Duration pollInterval)`; `pollInterval` null/0/âm → `IllegalArgumentException`. Exception trong một pass chỉ log WARN, tick sau chạy tiếp.

### `JdbcProcessedEventStore` (`io.javalibs.datahub.spring.idempotency`)

`ProcessedEventStore` trên JDBC, dựa vào **primary key `(handler, event_id)`** của bảng: `markProcessed` là INSERT thẳng — **insert đầu tiên thắng**; insert trùng (kể cả đồng thời) ném `DuplicateKeyException` được bắt và trả `false`. `deleteOlderThan(cutoff)` xóa theo `processed_at < cutoff`. Constructor `(JdbcOperations jdbc, String tableName)` validate tên bảng như outbox.

### `IdempotentEventProcessor` (`io.javalibs.datahub.spring.idempotency`)

Wrapper biến listener bất kỳ thành idempotent: `boolean process(String handlerName, EventEnvelope<?> envelope, Runnable action)` — gọi `markProcessed` trước; trùng → log DEBUG, trả `false`, **không chạy action**; lần đầu → chạy `action`, trả `true`. Gọi trong transaction DB của listener: action ném exception thì cả marker lẫn business writes rollback, lần redeliver sau xử lý lại sạch sẽ.

### Schema tham chiếu (`META-INF/datahub/outbox-schema-postgres.sql`, trong jar `javalibs-datahub-spring`)

Thư viện **không bao giờ tự chạy DDL** — copy script này vào một migration Flyway của service (vd `src/main/resources/db/migration/V202607141030__datahub_outbox.sql`, xem [javalibs-persistence](persistence.md)). Nội dung (dialect PostgreSQL):

Bảng `datahub_outbox_event`:

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| `id` | VARCHAR(64) | PRIMARY KEY (= `eventId` của envelope) |
| `topic` | VARCHAR(255) | NOT NULL |
| `event_type` | VARCHAR(255) | NOT NULL |
| `source` | VARCHAR(255) | |
| `correlation_id` | VARCHAR(128) | |
| `partition_key` | VARCHAR(255) | |
| `headers_json` | TEXT | |
| `payload_json` | TEXT | NOT NULL |
| `payload_type` | VARCHAR(512) | |
| `status` | VARCHAR(16) | NOT NULL DEFAULT `'PENDING'` (PENDING / PUBLISHED / FAILED) |
| `attempts` | INT | NOT NULL DEFAULT 0 |
| `last_error` | TEXT | |
| `created_at` | TIMESTAMPTZ | NOT NULL |
| `published_at` | TIMESTAMPTZ | |

kèm index `idx_datahub_outbox_pending ON datahub_outbox_event (status, created_at)`.

Bảng `datahub_processed_event`:

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| `handler` | VARCHAR(255) | NOT NULL, PRIMARY KEY (handler, event_id) |
| `event_id` | VARCHAR(64) | NOT NULL, PRIMARY KEY (handler, event_id) |
| `processed_at` | TIMESTAMPTZ | NOT NULL |

kèm index `idx_datahub_processed_cleanup ON datahub_processed_event (processed_at)`.

### Auto-configuration (5 class, theo thứ tự trong `AutoConfiguration.imports`)

| Class | Bean | Điều kiện |
|---|---|---|
| `DatahubKafkaAutoConfiguration` | `kafkaEventPublisher` (`KafkaEventPublisher`) | `after = KafkaAutoConfiguration`; `@ConditionalOnClass(KafkaTemplate)`; `javalibs.datahub.kafka.enabled` (mặc định `true`); bean cần có `KafkaTemplate` trong context và `@ConditionalOnMissingBean(EventPublisher)` |
| `DatahubFallbackAutoConfiguration` | `loggingEventPublisher` (`LoggingEventPublisher`) | `after = DatahubKafkaAutoConfiguration`; chỉ `@ConditionalOnMissingBean(EventPublisher)` — **bảo đảm luôn tồn tại một bean `EventPublisher`** (Kafka trước, không có thì logging) |
| `DatahubRestAutoConfiguration` | `datahubRestClientBuilder` (`RestClient.Builder`) | `@ConditionalOnClass(RestClient)`; `javalibs.datahub.rest.enabled` (mặc định `true`); `@ConditionalOnMissingBean(name = "datahubRestClientBuilder")` |
| `DatahubOutboxAutoConfiguration` | `datahubTransactionalOutbox` (`JdbcTransactionalOutbox`), `datahubOutboxRelay` (`JdbcOutboxRelay`), `datahubOutboxRelayScheduler` (`OutboxRelayScheduler`) | **Opt-in** `javalibs.datahub.outbox.enabled=true`; `after` Kafka + Fallback; `@ConditionalOnClass(JdbcOperations, ObjectMapper)`. Outbox cần bean `DataSource`; relay cần thêm `PlatformTransactionManager` + `EventPublisher`; scheduler cần relay + `relay-enabled` (mặc định `true`). `ObjectMapper` lấy từ context nếu có, không có thì `new ObjectMapper()` |
| `DatahubIdempotencyAutoConfiguration` | `datahubProcessedEventStore` (`JdbcProcessedEventStore`), `datahubIdempotentEventProcessor` (`IdempotentEventProcessor`) | **Opt-in** `javalibs.datahub.idempotency.enabled=true`; `@ConditionalOnClass(JdbcOperations)`; store cần bean `DataSource` |

Thứ tự Kafka → Fallback là điểm mấu chốt: khi có `KafkaTemplate` (Boot tự tạo khi có spring-kafka + `spring.kafka.bootstrap-servers`) thì `EventPublisher` là Kafka; không có, hoặc `kafka.enabled=false`, hoặc thiếu class → fallback logging. Ứng dụng tự định nghĩa `EventPublisher` thì cả hai nhường.

## Luồng outbox (sequence)

```
 App thread (@Transactional)                    PostgreSQL                Relay thread (daemon, mỗi poll-interval)         Kafka
 ───────────────────────────                    ──────────                ────────────────────────────────────────         ─────
  BEGIN TX
  │ orderRepository.save(order)  ──────────────► INSERT orders
  │ outbox.enqueue(topic, envelope) ───────────► INSERT datahub_outbox_event
  │                                              (status=PENDING, attempts=0)
  COMMIT  ◄── nguyên tử: cả hai cùng commit hoặc cùng rollback
                                                                          BEGIN TX
                                                 SELECT ... WHERE status='PENDING'
                                                 ORDER BY created_at
                                                 FETCH FIRST <batch> ROWS ONLY
                                                 FOR UPDATE SKIP LOCKED  ◄────────┘
                                                                          │ với từng row:
                                                                          │  publisher.publish(topic, envelope) ─────────► broker ack
                                                                          │  ok   → UPDATE status='PUBLISHED', published_at
                                                                          │  lỗi  → attempts+1, last_error
                                                                          │         attempts >= max-attempts → status='FAILED'
                                                                          COMMIT
```

**Ngữ nghĩa at-least-once**: relay có thể publish xong nhưng crash trước khi UPDATE/commit → row vẫn `PENDING` → pass sau publish lại. Vì vậy **consumer bắt buộc idempotent** (dùng `IdempotentEventProcessor`).

**Thay thế bằng Debezium/CDC**: khối lượng rất lớn thì đặt `javalibs.datahub.outbox.relay-enabled=false` — bean outbox (`enqueue`) và bảng vẫn giữ nguyên, chỉ scheduler polling không chạy; Debezium đọc bảng `datahub_outbox_event` (layout tương thích) và tự đẩy vào Kafka.

## Cấu hình

Toàn bộ property bind vào `DatahubProperties` (`javalibs.datahub.*`):

| Thuộc tính | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `javalibs.datahub.source` | String | *(null)* | Tên logic của service dùng làm `source` của sự kiện; không đặt thì ứng dụng nên fallback về `spring.application.name` |
| `javalibs.datahub.kafka.enabled` | boolean | `true` | Bật/tắt auto-configuration Kafka publisher |
| `javalibs.datahub.kafka.send-timeout` | Duration | `30s` | Thời gian tối đa `publish()` (blocking) chờ broker xác nhận |
| `javalibs.datahub.rest.enabled` | boolean | `true` | Bật/tắt bean `datahubRestClientBuilder` |
| `javalibs.datahub.rest.connect-timeout` | Duration | `5s` | Timeout thiết lập kết nối TCP |
| `javalibs.datahub.rest.read-timeout` | Duration | `10s` | Timeout chờ dữ liệu response |
| `javalibs.datahub.rest.max-retries` | int | `3` | Tổng số lần thử (kể cả lần đầu) cho request retry được |
| `javalibs.datahub.rest.initial-backoff` | Duration | `200ms` | Backoff trước retry đầu tiên; nhân đôi sau mỗi lần thất bại |
| `javalibs.datahub.outbox.enabled` | boolean | `false` | **Opt-in** bật Transactional Outbox (cần bảng + `DataSource`) |
| `javalibs.datahub.outbox.table` | String | `datahub_outbox_event` | Tên bảng outbox (chỉ chữ/số/`_`/`.`) |
| `javalibs.datahub.outbox.batch-size` | int | `100` | Số row tối đa claim mỗi pass relay |
| `javalibs.datahub.outbox.max-attempts` | int | `10` | Số lần publish trước khi row chuyển `FAILED` |
| `javalibs.datahub.outbox.poll-interval` | Duration | `5s` | Chu kỳ giữa các pass relay (fixed delay) |
| `javalibs.datahub.outbox.use-skip-locked` | boolean | `true` | Thêm `SKIP LOCKED` vào query claim (PostgreSQL/MySQL 8+/Oracle; tắt cho H2) |
| `javalibs.datahub.outbox.relay-enabled` | boolean | `true` | Chạy relay polling in-process; đặt `false` khi dùng Debezium/CDC |
| `javalibs.datahub.idempotency.enabled` | boolean | `false` | **Opt-in** bật dedup consumer (cần bảng + `DataSource`) |
| `javalibs.datahub.idempotency.table` | String | `datahub_processed_event` | Tên bảng dedup |

## Hướng dẫn sử dụng

### Dependency

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-datahub-spring-boot-starter</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Starter kéo theo `javalibs-datahub-core`, `javalibs-datahub-spring`, `javalibs-datahub-spring-boot-autoconfigure` và `spring-kafka`. Muốn dùng outbox/idempotency thì service cần thêm `DataSource` (vd `spring-boot-starter-data-jpa` hoặc `spring-boot-starter-jdbc`) và migration tạo bảng.

### `application.yml`

```yaml
spring:
  application:
    name: order-service
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      # BẮT BUỘC để EventEnvelope được serialize thành JSON khi gửi Kafka
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

javalibs:
  datahub:
    source: order-service
    kafka:
      send-timeout: 30s
    outbox:
      enabled: true          # opt-in
      poll-interval: 5s
      batch-size: 100
      max-attempts: 10
      use-skip-locked: true  # PostgreSQL/MySQL 8+
      relay-enabled: true    # false nếu dùng Debezium
    idempotency:
      enabled: true          # opt-in, cần bảng datahub_processed_event
```

### Ví dụ 1 — publish qua outbox trong `@Transactional`

```java
@Service
public class OrderService {

  private final OrderRepository orderRepository;
  private final TransactionalOutbox outbox;

  public OrderService(OrderRepository orderRepository, TransactionalOutbox outbox) {
    this.orderRepository = orderRepository;
    this.outbox = outbox;
  }

  @Transactional  // BẮT BUỘC — enqueue ngoài transaction ném IllegalStateException
  public void placeOrder(PlaceOrderCommand cmd) {
    Order order = orderRepository.save(Order.place(cmd));
    outbox.enqueue("orders.events",
        EventEnvelope.of("OrderPlaced", "order-service", OrderPlacedEvent.from(order)));
    // Cả hai INSERT commit nguyên tử; relay nền publish sau commit.
  }
}
```

### Ví dụ 2 — consumer idempotent với `@KafkaListener`

```java
@Component
public class OrderEventsListener {

  private final IdempotentEventProcessor idempotentProcessor;

  public OrderEventsListener(IdempotentEventProcessor idempotentProcessor) {
    this.idempotentProcessor = idempotentProcessor;
  }

  @KafkaListener(topics = "orders.events")
  @Transactional  // marker + business writes commit nguyên tử
  public void onOrderEvent(EventEnvelope<JsonNode> envelope) {
    idempotentProcessor.process("OrderEventsListener", envelope, () -> {
      // business logic — chạy tối đa 1 lần cho mỗi eventId;
      // action ném exception → rollback cả marker lẫn business writes,
      // lần redeliver sau xử lý lại từ đầu
    });
  }
}
```

### Ví dụ 3 — publish trực tiếp (không outbox) và REST client có retry

```java
// publish trực tiếp — chỉ dùng khi không kèm ghi DB cần nguyên tử
publisher.publish("orders", EventEnvelope.of("order.created", "order-service", payload));
publisher.publishAsync("orders", event).whenComplete((v, ex) -> { /* ... */ });

// REST client: inject builder theo tên
@Bean
public RestClient inventoryClient(
    @Qualifier("datahubRestClientBuilder") RestClient.Builder builder) {
  return builder.baseUrl("http://inventory-service").build();
}
```

## Ghi đè & mở rộng

- **`EventPublisher` riêng** (RabbitMQ, SNS...): khai báo bean `EventPublisher` — cả Kafka lẫn fallback auto-config nhường (`@ConditionalOnMissingBean(EventPublisher)`); outbox relay tự dùng publisher đó.
- **Tắt Kafka nhưng giữ contract**: `javalibs.datahub.kafka.enabled=false` → rơi về `LoggingEventPublisher`, code nghiệp vụ không đổi.
- **`TransactionalOutbox` / `JdbcOutboxRelay` / `OutboxRelayScheduler` / `ProcessedEventStore` / `IdempotentEventProcessor` riêng**: tất cả bean đều `@ConditionalOnMissingBean` — khai báo bean cùng kiểu là thư viện lùi.
- **Chỉ tắt scheduler, giữ relay bean**: `relay-enabled=false` rồi tự điều khiển `JdbcOutboxRelay.relayBatch()` (vd theo lịch riêng, hoặc trigger sau mỗi request).
- **Builder REST riêng**: khai báo bean tên `datahubRestClientBuilder`, hoặc tự lắp `DatahubRestClients.defaultBuilder(...)` / `new RetryingClientHttpRequestInterceptor(maxAttempts, initialBackoff, true)` khi cần retry cả POST.
- **Circuit breaker cho REST**: bật `javalibs.resilience.rest.enabled=true` của [javalibs-resilience](resilience.md) — lưu ý `RestClientCustomizer` của resilience chỉ áp vào builder do Boot auto-configure, còn `datahubRestClientBuilder` được tạo thủ công nên muốn kết hợp thì tự gắn `CircuitBreakingClientHttpRequestInterceptor` vào builder.

## Testing

- **Outbox trên H2** (cách module tự test trong `OutboxJdbcTest`): `EmbeddedDatabaseBuilder` H2 + tự chạy DDL (đổi `TIMESTAMPTZ` → `TIMESTAMP`), `JdbcTransactionalOutbox` + `TransactionTemplate` + mock `EventPublisher`, và `OutboxRelayConfig` với `useSkipLocked = false` (H2 không hỗ trợ `SKIP LOCKED`). Gọi `relayBatch()` thủ công, assert status/attempts trong bảng.
- **Idempotency trên H2** (`IdempotencyJdbcTest`): tạo bảng `datahub_processed_event`, assert `markProcessed` lần 2 trả `false`, action chỉ chạy 1 lần, `deleteOlderThan` xóa marker cũ.
- **Auto-configuration** (`DatahubAutoConfigurationTest`, `DatahubOutboxAutoConfigurationTest`): `ApplicationContextRunner` + `AutoConfigurations.of(...)`; test outbox đặt `poll-interval=10m` để scheduler `SmartLifecycle` không tick trong lúc test.
- **KafkaEventPublisher** (`KafkaEventPublisherTest`): mock `KafkaTemplate`, capture `ProducerRecord` để assert key/value/header; future không hoàn tất trong `sendTimeout` để test `DatahubException` timeout.
- **Retry interceptor** (`RetryingClientHttpRequestInterceptorTest`): mock `ClientHttpRequestExecution` trả 5xx/IOException, `initialBackoff` 1ms cho test nhanh.

## Lưu ý & bẫy thường gặp

- **Quên `value-serializer` JSON**: envelope là value của Kafka record — thiếu `spring.kafka.producer.value-serializer=...JsonSerializer` thì producer mặc định (StringSerializer) sẽ fail/serialize sai.
- **`enqueue` ngoài transaction ném `IllegalStateException`** — đây là thiết kế, không phải bug: ngoài transaction thì outbox không còn bảo đảm nguyên tử. Annotate service method bằng `@Transactional`.
- **Outbox/idempotency là opt-in**: `enabled` mặc định `false`; bật flag mà quên tạo bảng thì lỗi chỉ lộ lúc runtime (INSERT/SELECT fail) — thư viện không bao giờ tự chạy DDL, hãy copy `META-INF/datahub/outbox-schema-postgres.sql` vào migration Flyway ([javalibs-persistence](persistence.md)).
- **At-least-once, không exactly-once**: relay crash giữa publish và UPDATE → publish lại. Mọi consumer của topic từ outbox phải idempotent.
- **Row `FAILED` không tự hồi**: quá `max-attempts` row đỗ lại vĩnh viễn chờ xử lý tay (sửa nguyên nhân rồi `UPDATE ... SET status='PENDING', attempts=0`). Giám sát số row `FAILED` và `PENDING` tồn đọng.
- **`use-skip-locked` với H2**: H2 không hỗ trợ `SKIP LOCKED` — tắt trong test/embedded, giữ `true` trên PostgreSQL/MySQL 8+ để nhiều instance relay song song.
- **Payload phía relay là `JsonNode`**, không phải class gốc (`payload_type` chỉ lưu để tham khảo) — consumer nhận JSON và tự bind theo `x-event-type`.
- **POST không được retry mặc định** ở REST client — đúng chủ đích (không idempotent); cần lắm thì tự tạo interceptor với `retryAllMethods = true` và tự chịu rủi ro double-submit.
- **`publish` là blocking** (tối đa `send-timeout`): trên hot path hãy dùng `publishAsync` hoặc đi qua outbox.
- **Dedup marker cần dọn định kỳ**: lên lịch `processedEventStore.deleteOlderThan(...)` (vd giữ 7 ngày) — thư viện không tự dọn.
