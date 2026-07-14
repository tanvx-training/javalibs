# javalibs-observability

Bộ thư viện observability dùng chung cho các dịch vụ Java/Spring Boot: **correlation id**, **lan truyền MDC qua thread**, **common metric tags** và starter tích hợp sẵn Actuator + Micrometer Tracing (OpenTelemetry bridge).

## Mục đích

Khi một request đi qua nhiều tầng (filter → controller → service → tác vụ `@Async` → HTTP call sang dịch vụ khác), ta cần một cách thống nhất để:

1. Gắn cho mỗi request một **correlation id** ổn định, trả lại cho client qua response header và ghi vào mọi dòng log.
2. Đảm bảo **MDC (correlationId, traceId, spanId) không bị mất** khi công việc được chuyển sang thread pool.
3. Gắn **tag chung** (`application`, `environment`, tag tùy chỉnh) cho toàn bộ metric, giúp lọc dashboard theo dịch vụ/môi trường.

## Kiến trúc module

```
javalibs-observability (pom)
├── javalibs-observability-core                       # thuần Java, không phụ thuộc framework
├── javalibs-observability-spring                     # tích hợp Spring (filter, TaskDecorator)
├── javalibs-observability-spring-boot-autoconfigure  # auto-configuration + properties
└── javalibs-observability-spring-boot-starter        # gộp tất cả + actuator + tracing bridge
```

| Module | Nội dung chính |
|---|---|
| `javalibs-observability-core` | `CorrelationId` (hằng số header/MDC key, `generate()`, `isValid()` chống log injection), `ObservabilityConstants` (`traceId`, `spanId`, tag `application`, `environment`). |
| `javalibs-observability-spring` | `CorrelationIdFilter` (filter servlet thuần `jakarta.servlet.Filter`, không cần spring-web), `MdcTaskDecorator` (lan truyền MDC cho `@Async`/thread pool). |
| `javalibs-observability-spring-boot-autoconfigure` | `ObservabilityAutoConfiguration`, `CorrelationIdWebAutoConfiguration`, `ObservabilityProperties` (namespace `javalibs.observability.*`). |
| `javalibs-observability-spring-boot-starter` | Không có mã nguồn; kéo theo 3 module trên + `spring-boot-starter-actuator` + `micrometer-tracing-bridge-otel`. |

## Bảng thuộc tính cấu hình

| Thuộc tính | Kiểu | Mặc định | Ý nghĩa |
|---|---|---|---|
| `javalibs.observability.correlation.enabled` | boolean | `true` | Bật/tắt đăng ký `CorrelationIdFilter` (chỉ áp dụng cho ứng dụng web servlet). |
| `javalibs.observability.correlation.header` | String | `X-Correlation-Id` | Tên header đọc/ghi correlation id. |
| `javalibs.observability.metrics.enabled` | boolean | `true` | Bật/tắt việc gắn common tags cho mọi `MeterRegistry`. |
| `javalibs.observability.metrics.common-tags.*` | Map<String,String> | rỗng | Các tag tùy chỉnh gắn vào mọi meter, ví dụ `common-tags.team=platform`. |
| `javalibs.observability.metrics.environment` | String | (không đặt) | Khi được đặt sẽ thêm tag `environment` vào mọi meter. |

## Correlation id và trace id chảy qua log như thế nào

1. Request đến, `CorrelationIdFilter` (order `HIGHEST_PRECEDENCE + 10`) đọc header `X-Correlation-Id`.
   - Header hợp lệ (không rỗng, ≤ 128 ký tự, chỉ gồm ASCII in được — chặn `\r`/`\n` để chống log injection) → dùng lại.
   - Thiếu hoặc không hợp lệ → sinh id mới bằng `CorrelationId.generate()` (UUID bỏ dấu gạch, 32 ký tự hex).
2. Filter đặt id vào MDC key `correlationId` và ghi vào response header **trước khi** chuyển tiếp chain, nên client luôn nhận được id kể cả khi response bị commit sớm; khi request kết thúc, MDC được dọn sạch trong `finally`.
3. Nếu dùng starter, Micrometer Tracing (bridge OTel) tự tạo span cho mỗi request và đặt `traceId`/`spanId` vào MDC — Spring Boot cũng tự thêm chúng vào pattern log mặc định.
4. Khi công việc được đẩy sang thread pool (`@Async`, executor do Boot tạo), `MdcTaskDecorator` chụp lại MDC tại thời điểm submit và cài vào worker thread lúc chạy, rồi khôi phục trạng thái cũ trong `finally` — log ở thread khác vẫn mang đúng `correlationId`/`traceId`.

## Pattern log ví dụ

```xml
<pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%X{correlationId}] [%X{traceId}/%X{spanId}] %logger{36} - %msg%n</pattern>
```

hoặc chỉ mở rộng phần level qua `application.yml`:

```yaml
logging:
  pattern:
    level: "%5p [%X{correlationId}] [%X{traceId}/%X{spanId}]"
```

Dòng log thu được sẽ có dạng:

```
2026-07-13 10:15:42.123 INFO  [8f14e45fceea167a5a36dedd4bea2543] [64a1b2.../f00d...] c.e.OrderService - Order created
```

## Sử dụng nhanh

Thêm starter (khuyến nghị cho ứng dụng Spring Boot):

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-observability-spring-boot-starter</artifactId>
</dependency>
```

Hoặc chỉ lấy từng phần: dùng `javalibs-observability-spring` nếu muốn tự đăng ký filter/decorator, hoặc `javalibs-observability-core` nếu chỉ cần tiện ích correlation id thuần Java.

Chi tiết cấu hình exporter trace và endpoint Actuator: xem README của [`javalibs-observability-spring-boot-starter`](javalibs-observability-spring-boot-starter/README.md).
