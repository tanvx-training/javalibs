# javalibs-observability-spring-boot-starter

Starter "tất cả trong một" cho observability trong ứng dụng Spring Boot: chỉ cần thêm một dependency là có ngay correlation id, MDC propagation, common metric tags, Actuator và Micrometer Tracing (cầu nối OpenTelemetry).

## Cài đặt

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-observability-spring-boot-starter</artifactId>
</dependency>
```

## Bạn nhận được gì ngay khi thêm starter

| Tính năng | Mô tả |
|---|---|
| Correlation id filter | Filter servlet tự động đăng ký ở `Ordered.HIGHEST_PRECEDENCE + 10`, đọc header `X-Correlation-Id` (hoặc header tùy chỉnh); nếu thiếu/không hợp lệ sẽ sinh id mới (UUID 32 ký tự hex), đưa vào MDC key `correlationId` và ghi vào response header **trước khi** xử lý request. |
| MDC propagation | `MdcTaskDecorator` được đăng ký làm `TaskDecorator` mặc định, giúp các tác vụ `@Async` / thread-pool mang theo `correlationId`, `traceId`, `spanId` của thread gửi tác vụ. Tự động lùi bước (back off) nếu bạn khai báo `TaskDecorator` riêng. |
| Common metric tags | Mọi `MeterRegistry` được gắn tag `application` (từ `spring.application.name`), tag `environment` (từ `javalibs.observability.metrics.environment`) và các tag tùy chỉnh trong `javalibs.observability.metrics.common-tags.*`. |
| Actuator | `spring-boot-starter-actuator` đi kèm: health, metrics, cùng toàn bộ hạ tầng Micrometer. |
| Micrometer Tracing (OTel bridge) | `micrometer-tracing-bridge-otel` đi kèm, nên Spring Boot tự động tạo trace/span cho mỗi request và **tự đưa `traceId`/`spanId` vào pattern log mặc định** — bạn thấy trace id trong log mà không cần cấu hình gì thêm. |

## Cấu hình (`javalibs.observability.*`)

```yaml
javalibs:
  observability:
    correlation:
      enabled: true               # tắt filter bằng false
      header: X-Correlation-Id    # đổi tên header nếu cần
    metrics:
      enabled: true               # tắt common tags bằng false
      environment: prod           # thêm tag "environment" khi được đặt
      common-tags:
        team: platform            # tag tùy chỉnh gắn vào mọi meter
```

## Xuất trace ra hệ thống bên ngoài (OTLP)

Starter chỉ tạo trace trong ứng dụng; để **xuất** trace sang collector (Grafana Tempo, Jaeger, v.v.) hãy thêm exporter:

```xml
<dependency>
  <groupId>io.opentelemetry</groupId>
  <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

và trỏ endpoint OTLP:

```yaml
management:
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
  tracing:
    sampling:
      probability: 1.0   # mặc định 0.1 — tăng lên khi cần thấy đủ trace ở môi trường dev
```

## Pattern log khuyến nghị

Đưa cả correlation id lẫn trace/span id vào log để tra cứu chéo giữa log và trace:

```yaml
logging:
  pattern:
    level: "%5p [%X{correlationId}] [%X{traceId}/%X{spanId}]"
```

hoặc pattern đầy đủ trong `logback-spring.xml`:

```xml
<pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%X{correlationId}] [%X{traceId}/%X{spanId}] %logger{36} - %msg%n</pattern>
```

## Mở endpoint Actuator

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
```

## Các module bên dưới

- `javalibs-observability-core` — tiện ích thuần Java: `CorrelationId`, `ObservabilityConstants`.
- `javalibs-observability-spring` — `CorrelationIdFilter`, `MdcTaskDecorator`.
- `javalibs-observability-spring-boot-autoconfigure` — auto-configuration và configuration properties.
