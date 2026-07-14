# javalibs-datahub-spring-boot-starter

Starter Spring Boot cho module **datahub**: chỉ cần thêm một dependency là có ngay
`EventPublisher` chạy trên Kafka (kèm fallback ghi log khi không có Kafka) và
`RestClient.Builder` được cấu hình sẵn timeout + retry.

## Cài đặt

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-datahub-spring-boot-starter</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Starter kéo theo: `javalibs-datahub-core`, `javalibs-datahub-spring`,
`javalibs-datahub-spring-boot-autoconfigure` và `spring-kafka`.

## Cấu hình mẫu (`application.yml`)

```yaml
spring:
  application:
    name: order-service
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      # BAT BUOC de EventEnvelope duoc serialize thanh JSON khi gui Kafka
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

javalibs:
  datahub:
    source: order-service        # mac dinh: fallback ve spring.application.name
    kafka:
      enabled: true              # mac dinh true
      send-timeout: 30s          # timeout cho publish() dong bo
    rest:
      enabled: true              # mac dinh true
      connect-timeout: 5s
      read-timeout: 10s
      max-retries: 3             # tong so lan thu (ke ca lan dau)
      initial-backoff: 200ms     # nhan doi sau moi lan that bai: 200ms -> 400ms
```

> **Lưu ý về JSON:** envelope được gửi làm value của Kafka record, vì vậy **bắt buộc**
> phải cấu hình
> `spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer`
> (hoặc một serializer JSON tương đương) thì payload mới được serialize đúng.

## Phát một sự kiện

```java
@Service
public class OrderService {

  private final EventPublisher publisher;

  public OrderService(EventPublisher publisher) {
    this.publisher = publisher;
  }

  public void createOrder(Order order) {
    EventEnvelope<OrderCreated> event = EventEnvelope.of(
        "order.created",            // eventType
        "order-service",            // source
        new OrderCreated(order.id()));

    publisher.publish("orders", event);          // dong bo, cho broker xac nhan
    // hoac:
    publisher.publishAsync("orders", event);     // bat dong bo, tra ve CompletableFuture<Void>
  }
}
```

## Cách hoạt động

- Có `KafkaTemplate` trong context (Spring Boot tự tạo khi có `spring-kafka` +
  `spring.kafka.bootstrap-servers`) → bean `EventPublisher` là `KafkaEventPublisher`.
- Không có `KafkaTemplate`, hoặc đặt `javalibs.datahub.kafka.enabled=false` → tự động
  rơi về `LoggingEventPublisher` (chỉ ghi log INFO) — code nghiệp vụ không cần thay đổi.
- Ứng dụng tự định nghĩa bean `EventPublisher` → auto-configuration nhường hoàn toàn.
- Bean `datahubRestClientBuilder` (kiểu `RestClient.Builder`) luôn sẵn sàng để inject,
  trừ khi đặt `javalibs.datahub.rest.enabled=false`.

```java
@Bean
public RestClient inventoryClient(
    @Qualifier("datahubRestClientBuilder") RestClient.Builder builder) {
  return builder.baseUrl("http://inventory-service").build();
}
```

Xem thêm chi tiết kiến trúc và bảng thuộc tính đầy đủ tại README của module cha
`javalibs-datahub`.
