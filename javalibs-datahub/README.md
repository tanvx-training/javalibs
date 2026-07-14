# javalibs-datahub

Module chuẩn hoá **eventing** (phát sự kiện qua Kafka với envelope thống nhất) và cung cấp
**REST client mặc định có khả năng chịu lỗi** (timeout + retry với exponential backoff) cho
các service xây dựng trên bộ thư viện javalibs.

## Mục đích

- Chuẩn hoá cấu trúc sự kiện trao đổi giữa các service bằng `EventEnvelope<T>`:
  mọi sự kiện đều mang `eventId`, `eventType`, `source`, `occurredAt`, `correlationId`,
  `partitionKey`, `headers` và `payload`.
- Cung cấp abstraction `EventPublisher` để code nghiệp vụ không phụ thuộc trực tiếp vào
  Kafka: production dùng `KafkaEventPublisher`, môi trường local/dev không có Kafka tự
  động rơi về `LoggingEventPublisher` (chỉ ghi log) — ứng dụng không bao giờ bị lỗi vì
  thiếu bean.
- Cung cấp `RestClient.Builder` được cấu hình sẵn timeout và interceptor retry cho các
  lỗi tạm thời (IOException, HTTP 5xx) trên các HTTP method idempotent.

## Kiến trúc submodule

| Submodule | Nội dung | Phụ thuộc chính |
|---|---|---|
| `javalibs-datahub-core` | Contract thuần Java: `EventEnvelope`, `EventPublisher`, `EventHandler`, `DatahubException`. **Không phụ thuộc Spring.** | (không) |
| `javalibs-datahub-spring` | Hiện thực Spring: `LoggingEventPublisher`, `kafka.KafkaEventPublisher`, `rest.RetryingClientHttpRequestInterceptor`, `rest.DatahubRestClients`. | core, spring-context, slf4j-api; spring-kafka / spring-web / jackson-databind (*optional*) |
| `javalibs-datahub-spring-boot-autoconfigure` | Auto-configuration cho Spring Boot: `DatahubProperties`, `DatahubKafkaAutoConfiguration`, `DatahubFallbackAutoConfiguration`, `DatahubRestAutoConfiguration`. | spring, spring-boot-autoconfigure |
| `javalibs-datahub-spring-boot-starter` | Starter "cắm là chạy": gom core + spring + autoconfigure + spring-kafka. Không chứa code. | tất cả các module trên |

## Bảng thuộc tính cấu hình (`javalibs.datahub.*`)

| Thuộc tính | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `javalibs.datahub.source` | String | *(null)* | Tên logic của service, dùng làm `source` của sự kiện. Khi không đặt, ứng dụng nên fallback về `spring.application.name`. |
| `javalibs.datahub.kafka.enabled` | boolean | `true` | Bật/tắt auto-configuration cho Kafka publisher. |
| `javalibs.datahub.kafka.send-timeout` | Duration | `30s` | Thời gian tối đa `publish()` chờ broker xác nhận. |
| `javalibs.datahub.rest.enabled` | boolean | `true` | Bật/tắt auto-configuration cho `datahubRestClientBuilder`. |
| `javalibs.datahub.rest.connect-timeout` | Duration | `5s` | Timeout thiết lập kết nối TCP. |
| `javalibs.datahub.rest.read-timeout` | Duration | `10s` | Timeout chờ dữ liệu phản hồi. |
| `javalibs.datahub.rest.max-retries` | int | `3` | Tổng số lần thử (kể cả lần đầu) cho request có thể retry. |
| `javalibs.datahub.rest.initial-backoff` | Duration | `200ms` | Độ trễ trước lần retry đầu tiên; nhân đôi sau mỗi lần thất bại (200ms → 400ms → ...). |

## Luồng sự kiện (event flow)

```
  Code nghiệp vụ
       |
       |  EventEnvelope.of("order.created", "order-service", payload)
       v
  EventPublisher.publish(topic, envelope)
       |
       +--- co KafkaTemplate? ------------------- khong ---+
       |                                                   |
      co                                                   v
       |                                          LoggingEventPublisher
       v                                          (ghi log INFO, khong gui di dau)
  KafkaEventPublisher
       |  ProducerRecord(topic, key = partitionKey, value = envelope)
       |  + headers: x-event-id, x-event-type, x-correlation-id, x-source
       |  + envelope.headers()
       v
  Kafka broker ---> Consumer ---> EventHandler<T> theo eventType()
```

## Ví dụ sử dụng

### Phát sự kiện

```java
@Service
public class OrderService {

  private final EventPublisher publisher;

  public OrderService(EventPublisher publisher) {
    this.publisher = publisher;
  }

  public void createOrder(Order order) {
    // ... luu don hang ...
    EventEnvelope<OrderCreated> event = EventEnvelope.of(
        "order.created", "order-service", new OrderCreated(order.id()));
    publisher.publish("orders", event);
  }
}
```

### Điều khiển đầy đủ metadata bằng Builder

```java
EventEnvelope<OrderCreated> event = EventEnvelope.<OrderCreated>builder()
    .eventType("order.created")
    .source("order-service")
    .correlationId(MDC.get("traceId"))
    .partitionKey(order.id())          // dam bao cac su kien cung don hang vao cung partition
    .header("tenant", tenantId)
    .payload(new OrderCreated(order.id()))
    .build();

publisher.publishAsync("orders", event)
    .whenComplete((v, ex) -> { /* xu ly ket qua */ });
```

### Đăng ký handler phía consumer

```java
@Component
public class OrderCreatedHandler implements EventHandler<OrderCreated> {

  @Override
  public String eventType() {
    return "order.created";
  }

  @Override
  public void handle(EventEnvelope<OrderCreated> event) {
    // xu ly su kien
  }
}
```

### Dùng REST client có retry

```java
@Configuration
public class InventoryClientConfig {

  @Bean
  public RestClient inventoryClient(
      @Qualifier("datahubRestClientBuilder") RestClient.Builder builder) {
    return builder.baseUrl("http://inventory-service").build();
  }
}
```

Interceptor chỉ retry các method idempotent (GET/HEAD/OPTIONS/PUT/DELETE); POST **không**
được retry trừ khi tự tạo `RetryingClientHttpRequestInterceptor` với cờ
`retryAllMethods = true`.

## Ghi chú

- Để envelope được serialize thành JSON khi gửi Kafka, cần cấu hình
  `spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer`
  (xem README của starter).
- Tự định nghĩa bean `EventPublisher` hoặc bean tên `datahubRestClientBuilder` trong
  ứng dụng sẽ khiến auto-configuration tương ứng tự động nhường (back off).

## Transactional Outbox (mới)

Ghi DB và bắn Kafka là **hai hệ thống không thể commit nguyên tử** (dual-write): lưu DB thành công nhưng rớt mạng khi publish ⇒ dữ liệu lệch âm thầm. Outbox pattern giải quyết:

```
@Transactional ──► business writes ──► INSERT datahub_outbox_event (cùng transaction)
                                              │ commit nguyên tử
   OutboxRelay (nền, mỗi 5s) ──► SELECT PENDING FOR UPDATE SKIP LOCKED ──► Kafka ──► PUBLISHED
```

Kích hoạt (opt-in):

```yaml
javalibs:
  datahub:
    outbox:
      enabled: true
      poll-interval: 5s      # chu kỳ relay
      batch-size: 100
      max-attempts: 10       # quá số lần → status FAILED, chờ xử lý tay
      use-skip-locked: true  # PostgreSQL/MySQL 8+; nhiều instance relay song song an toàn
      relay-enabled: true    # tắt nếu dùng Debezium/CDC đọc bảng outbox
```

Tạo bảng bằng Flyway — copy DDL từ `META-INF/datahub/outbox-schema-postgres.sql` (thư viện không bao giờ tự chạy DDL). Sử dụng:

```java
@Transactional
public void placeOrder(PlaceOrderCommand cmd) {
  orderRepository.save(order);
  outbox.enqueue("orders.events", EventEnvelope.of("OrderPlaced", "orders-service", event));
  // enqueue ngoài transaction sẽ ném IllegalStateException — đúng thiết kế
}
```

Giao hàng là **at-least-once** ⇒ consumer phải idempotent (bên dưới). Khối lượng rất lớn: thay relay polling bằng Debezium đọc bảng outbox (schema tương thích).

## Idempotent Consumer (mới)

Kafka có thể giao cùng một message 2 lần (rebalance, retry). Bật deduplication:

```yaml
javalibs:
  datahub:
    idempotency:
      enabled: true   # cần bảng datahub_processed_event (cùng file schema trên)
```

```java
@KafkaListener(topics = "orders.events")
@Transactional
public void onOrderEvent(EventEnvelope<JsonNode> envelope) {
  idempotentProcessor.process("OrderEventsListener", envelope, () -> {
    // business logic — chạy đúng 1 lần cho mỗi eventId;
    // marker + business writes commit nguyên tử, action ném exception thì rollback cả hai
  });
}
```

Thuộc tính mới: `javalibs.datahub.outbox.*` (enabled, table, batch-size, max-attempts, poll-interval, use-skip-locked, relay-enabled) và `javalibs.datahub.idempotency.*` (enabled, table).
