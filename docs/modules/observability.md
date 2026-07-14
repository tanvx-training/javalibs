# javalibs-observability

> Nền tảng observability dùng chung: correlation id xuyên suốt request (header + MDC), lan truyền MDC qua thread pool cho `@Async`, common tags cho mọi metric Micrometer, và starter gói sẵn Actuator + Micrometer Tracing (cầu nối OpenTelemetry).

## Artifacts

| Artifact (`groupId: io.javalibs`) | Nội dung | Phụ thuộc chính |
|---|---|---|
| `javalibs-observability-core` | Java thuần, không phụ thuộc framework: `CorrelationId`, `ObservabilityConstants` | không có (chỉ JDK) |
| `javalibs-observability-spring` | `CorrelationIdFilter` (servlet filter thuần `jakarta.servlet.Filter`), `MdcTaskDecorator` | core, `spring-context`, `spring-core`, `slf4j-api`, `jakarta.servlet-api` (provided) |
| `javalibs-observability-spring-boot-autoconfigure` | `ObservabilityAutoConfiguration`, `CorrelationIdWebAutoConfiguration`, `ObservabilityProperties` (`javalibs.observability.*`) | spring module, `spring-boot-autoconfigure`; optional: `micrometer-core`, `spring-boot-actuator-autoconfigure`, `spring-web` |
| `javalibs-observability-spring-boot-starter` | Không có code — gom 3 module trên + `spring-boot-starter-actuator` + `micrometer-tracing-bridge-otel` | — |

## Khi nào dùng / không dùng

**Dùng khi:**

- Muốn mọi request có một **correlation id** ổn định: nhận từ client qua header, trả lại qua response header, xuất hiện trên mọi dòng log của request đó — kể cả log ở thread `@Async`.
- Muốn mọi metric tự gắn tag `application`/`environment` (+ tag tùy chỉnh) để lọc dashboard theo service/môi trường.
- Muốn distributed tracing (traceId/spanId trong log, xuất trace ra collector OTLP) mà không phải tự lắp Micrometer Tracing.

**Không dùng khi:**

- Ứng dụng WebFlux — `CorrelationIdWebAutoConfiguration` chỉ kích hoạt cho servlet; MDC-per-thread cũng không hợp mô hình reactive.
- Đã dùng OpenTelemetry Java agent gắn ngoài JVM — tránh trùng lặp bridge tracing; khi đó chỉ nên dùng `core` + `spring` (correlation id, MDC) thay vì starter.
- Chỉ cần sinh id ngẫu nhiên chung chung — dùng `Ids` bên [javalibs-internal](internal.md).

## Các thành phần chính

### javalibs-observability-core (package `io.javalibs.observability`)

#### `CorrelationId` — utility tĩnh (final, không khởi tạo được)

| Thành viên | Giá trị / chữ ký | Mô tả |
|---|---|---|
| `DEFAULT_HEADER` | `"X-Correlation-Id"` | Header HTTP mặc định vận chuyển correlation id |
| `MDC_KEY` | `"correlationId"` | Key MDC để log pattern tham chiếu qua `%X{correlationId}` |
| `static String generate()` | — | Sinh id mới: **UUID ngẫu nhiên bỏ dấu gạch** → 32 ký tự hex thường, ví dụ `8f14e45fceea167a5a36dedd4bea2543` |
| `static boolean isValid(String value)` | — | Kiểm tra id do bên ngoài cung cấp có an toàn để dùng lại không |

Quy tắc `isValid` (chống log injection / HTTP response splitting khi echo header về client):

- `null`, blank, hoặc dài quá **128 ký tự** → không hợp lệ;
- chỉ chấp nhận **ASCII in được (0x20–0x7E)** — mọi ký tự điều khiển (kể cả `\r`, `\n`) và ký tự non-ASCII đều bị từ chối.

#### `ObservabilityConstants` — hằng số dùng chung

| Hằng số | Giá trị | Mô tả |
|---|---|---|
| `TRACE_ID` | `"traceId"` | Key MDC mà Micrometer Tracing tự điền khi có tracing bridge trên classpath → `%X{traceId}` |
| `SPAN_ID` | `"spanId"` | Key MDC tương ứng cho span → `%X{spanId}` |
| `TAG_APPLICATION` | `"application"` | Tên tag metric mang tên ứng dụng (`spring.application.name`) |
| `TAG_ENVIRONMENT` | `"environment"` | Tên tag metric mang môi trường triển khai (ví dụ `prod`) |

### javalibs-observability-spring (package `io.javalibs.observability.spring`)

#### `CorrelationIdFilter` — thiết lập correlation id cho mỗi request

```java
public CorrelationIdFilter(String headerName)  // Assert.hasText — không được blank
public String getHeaderName()
```

Là `jakarta.servlet.Filter` thuần (chủ đích: không cần `spring-web` trên classpath). Hành vi mỗi request HTTP:

1. Đọc header cấu hình (mặc định `X-Correlation-Id`).
2. Header **hợp lệ** theo `CorrelationId.isValid` → **dùng lại**; thiếu hoặc **không hợp lệ** → **thay bằng id mới** từ `CorrelationId.generate()`.
3. Đặt id vào MDC key `correlationId` và **ghi vào response header trước khi** gọi tiếp filter chain — nhờ vậy client luôn nhận được header kể cả khi response bị commit sớm (streaming, lỗi giữa chừng).
4. `finally`: luôn `MDC.remove` khi request kết thúc — không rò rỉ id giữa các request trên cùng thread.

Request/response không phải HTTP → cho đi qua nguyên vẹn.

#### `MdcTaskDecorator` — lan truyền MDC sang thread pool

```java
public class MdcTaskDecorator implements org.springframework.core.task.TaskDecorator {
    public Runnable decorate(Runnable runnable);
}
```

- **Chụp** MDC context map tại thời điểm task được submit (lúc `decorate` được gọi) và **cài** lên worker thread ngay trước khi task chạy → log trong `@Async`/thread-pool mang đúng `correlationId`, `traceId`, `spanId` của request gốc.
- Trong `finally` **khôi phục** MDC cũ của worker thread (hoặc clear nếu trước đó trống) — không rò rỉ context giữa các task dùng chung pool. Map chụp được `null` cũng xử lý an toàn.

### javalibs-observability-spring-boot-autoconfigure (package `io.javalibs.observability.autoconfigure`)

Hai auto-configuration được đăng ký trong `AutoConfiguration.imports`:

#### `ObservabilityAutoConfiguration`

| Bean | Điều kiện | Hành vi |
|---|---|---|
| `mdcTaskDecorator` (`TaskDecorator`) | `@ConditionalOnMissingBean(TaskDecorator.class)` — **back off** khi app tự khai báo `TaskDecorator` | `MdcTaskDecorator`; được `TaskExecutorBuilder` của Boot tự nhặt → mọi executor Boot tạo (gồm executor cho `@Async`) đều lan truyền MDC |
| `javalibsCommonTagsMeterRegistryCustomizer` (`MeterRegistryCustomizer<MeterRegistry>`) | nested config `@ConditionalOnClass({MeterRegistry, MeterRegistryCustomizer})` (cần micrometer-core + actuator autoconfigure trên classpath) và `javalibs.observability.metrics.enabled` ≠ `false` | Gắn common tags cho **mọi** `MeterRegistry`, theo thứ tự: tag `application` (từ `spring.application.name`) → tag `environment` (từ `javalibs.observability.metrics.environment`) → từng entry của `javalibs.observability.metrics.common-tags`. Key/value blank bị bỏ qua; **key xuất hiện trước thắng** — common-tags không thể ghi đè âm thầm `application`/`environment` |

#### `CorrelationIdWebAutoConfiguration`

- Điều kiện: ứng dụng web **servlet** + `javalibs.observability.correlation.enabled` ≠ `false`.
- Bean `correlationIdFilterRegistration` (`FilterRegistrationBean<CorrelationIdFilter>`):
  - filter tên `correlationIdFilter`, url pattern `/*`;
  - order **`Ordered.HIGHEST_PRECEDENCE + 10`** — chạy gần như đầu tiên, trước các filter request-logging của thư viện khác (ví dụ request logging của [javalibs-web](web.md)), để mọi log từ đó về sau đã có `correlationId` trong MDC;
  - header lấy từ `javalibs.observability.correlation.header`.

#### `ObservabilityProperties`

Class properties bind namespace `javalibs.observability.*` với hai nhóm nested `Correlation` và `Metrics` — chi tiết ở bảng Cấu hình dưới đây.

### javalibs-observability-spring-boot-starter

Không có mã nguồn; kéo theo `core` + `spring` + `autoconfigure` cùng:

| Dependency đi kèm | Mang lại |
|---|---|
| `spring-boot-starter-actuator` | Health, info, metrics endpoint + toàn bộ hạ tầng Micrometer |
| `io.micrometer:micrometer-tracing-bridge-otel` | Micrometer Tracing với backend OpenTelemetry: Boot tự tạo trace/span cho mỗi request HTTP và điền `traceId`/`spanId` vào MDC |

**`traceId`/`spanId` vào log thế nào:** khi tracing bridge có mặt trên classpath, Spring Boot tự thêm phần correlation (`application name`, `traceId`, `spanId`) vào **pattern log mặc định** — thấy trace id trong log mà không cần cấu hình gì thêm. `correlationId` là khái niệm riêng của javalibs nên **phải tự thêm `%X{correlationId}` vào pattern** (xem mục Hướng dẫn).

## Cấu hình

Toàn bộ thuộc tính namespace `javalibs.observability.*` (bind vào `ObservabilityProperties`):

| Thuộc tính | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `javalibs.observability.correlation.enabled` | boolean | `true` | Bật/tắt đăng ký `CorrelationIdFilter` (chỉ áp dụng cho ứng dụng web servlet) |
| `javalibs.observability.correlation.header` | String | `X-Correlation-Id` | Tên header HTTP đọc và echo correlation id |
| `javalibs.observability.metrics.enabled` | boolean | `true` | Bật/tắt việc gắn common tags cho mọi `MeterRegistry` |
| `javalibs.observability.metrics.environment` | String | — (không đặt) | Khi đặt, thêm tag `environment` với giá trị này vào mọi meter (ví dụ `dev`, `staging`, `prod`) |
| `javalibs.observability.metrics.common-tags.*` | Map\<String, String\> | rỗng | Tag tùy chỉnh gắn vào mọi meter, ví dụ `common-tags.team: platform` |

Ghi chú: tag `application` không có property riêng — lấy tự động từ `spring.application.name` (không đặt tên app thì tag này bị bỏ qua). Bean `TaskDecorator` không có cờ tắt riêng: muốn thay thì khai báo `TaskDecorator` của bạn (bean mặc định back off).

## Hướng dẫn sử dụng

### 1. Thêm starter

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-observability-spring-boot-starter</artifactId>
</dependency>
```

Chỉ cần từng phần: `javalibs-observability-spring` nếu tự đăng ký filter/decorator (không auto-config), `javalibs-observability-core` nếu chỉ cần tiện ích correlation id thuần Java.

### 2. application.yml

```yaml
spring:
  application:
    name: orders-api          # → tag "application" trên mọi metric

javalibs:
  observability:
    correlation:
      enabled: true           # tắt filter bằng false
      header: X-Correlation-Id
    metrics:
      enabled: true
      environment: prod       # → tag "environment"
      common-tags:
        team: platform        # tag tùy chỉnh

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

### 3. Pattern log khuyến nghị (thêm `%X{correlationId}`)

Cách gọn qua `application.yml` (mở rộng phần level của pattern mặc định):

```yaml
logging:
  pattern:
    level: "%5p [%X{correlationId}] [%X{traceId}/%X{spanId}]"
```

Hoặc pattern đầy đủ trong `logback-spring.xml`:

```xml
<pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%X{correlationId}] [%X{traceId}/%X{spanId}] %logger{36} - %msg%n</pattern>
```

Dòng log thu được:

```
2026-07-13 10:15:42.123 INFO  [8f14e45fceea167a5a36dedd4bea2543] [64a1b2.../f00d...] c.e.OrderService - Order created
```

### 4. `@Async` tự mang MDC

```java
@EnableAsync
@SpringBootApplication
public class OrdersApplication { ... }

@Service
public class NotificationService {
    @Async   // executor do Boot tạo đã được gắn MdcTaskDecorator
    public void sendEmail(String orderId) {
        log.info("Sending email for {}", orderId); // log vẫn có [correlationId] của request gốc
    }
}
```

Nếu tự dựng executor, gắn decorator thủ công:

```java
@Bean
ThreadPoolTaskExecutor reportExecutor(TaskDecorator mdcTaskDecorator) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setTaskDecorator(mdcTaskDecorator);
    return executor;
}
```

### 5. Xuất trace ra collector (OTLP)

Starter chỉ **tạo** trace trong ứng dụng; để **xuất** sang collector (Grafana Tempo, Jaeger, OTel Collector...) thêm exporter:

```xml
<dependency>
  <groupId>io.opentelemetry</groupId>
  <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

```yaml
management:
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
  tracing:
    sampling:
      probability: 1.0   # mặc định của Boot là 0.1 — tăng lên ở dev để thấy đủ trace
```

### 6. Correlation id chảy giữa các service

Filter chỉ xử lý chiều **vào**; khi service A gọi service B, **client phải tự forward header `X-Correlation-Id`** — lấy giá trị hiện hành từ MDC:

```java
import io.javalibs.observability.CorrelationId;
import org.slf4j.MDC;

@Bean
RestClient ordersRestClient(RestClient.Builder builder) {
    return builder
            .baseUrl("http://inventory-api")
            .requestInterceptor((request, body, execution) -> {
                String correlationId = MDC.get(CorrelationId.MDC_KEY);
                if (correlationId != null) {
                    request.getHeaders().set(CorrelationId.DEFAULT_HEADER, correlationId);
                }
                return execution.execute(request, body);
            })
            .build();
}
```

Service B nhận header, `CorrelationIdFilter` của B thấy giá trị hợp lệ → dùng lại → log của cả chuỗi service chung một correlation id. (Riêng `traceId` được Micrometer Tracing tự lan truyền qua header W3C `traceparent` khi dùng client instrumented — không cần code tay.)

## Ghi đè & mở rộng

- **`TaskDecorator` riêng**: khai báo bean `TaskDecorator` → `mdcTaskDecorator` mặc định back off. Muốn vừa MDC vừa logic riêng thì compose: gọi `new MdcTaskDecorator().decorate(...)` bên trong decorator của bạn.
- **Đổi header**: `javalibs.observability.correlation.header: X-Request-Id` — filter đọc/ghi header mới; hằng số `CorrelationId.DEFAULT_HEADER` chỉ là mặc định.
- **Tắt từng phần**: `correlation.enabled=false` (bỏ filter), `metrics.enabled=false` (bỏ common tags). Không có cờ tắt tổng cho cả module.
- **Đăng ký filter thủ công** (không dùng auto-config, ví dụ ngoài Boot): `new CorrelationIdFilter("X-Correlation-Id")` là servlet filter thuần, đăng ký bằng bất kỳ cơ chế nào; nếu muốn đổi order trong Boot, tự khai báo `FilterRegistrationBean<CorrelationIdFilter>` của bạn (lưu ý: bean đăng ký mặc định **không** có `@ConditionalOnMissingBean` — tắt bằng `correlation.enabled=false` rồi tự đăng ký).
- **Thêm common tags động**: khai báo thêm `MeterRegistryCustomizer<MeterRegistry>` bean của riêng bạn — customizer của javalibs không độc quyền.

## Testing

- `CorrelationIdFilter` test được với `MockHttpServletRequest`/`MockHttpServletResponse` + `MockFilterChain` (spring-test) — xem `CorrelationIdFilterTest` trong `javalibs-observability-spring`: assert response header được set, MDC có giá trị trong chain và được dọn sau đó.
- `MdcTaskDecorator` test thuần JUnit: `MDC.put(...)` → `decorate(runnable)` → chạy runnable trên thread khác → assert MDC trong runnable và trạng thái MDC của worker được khôi phục (xem `MdcTaskDecoratorTest`).
- Auto-configuration test bằng `ApplicationContextRunner`/`WebApplicationContextRunner` + `AutoConfigurations.of(ObservabilityAutoConfiguration.class, CorrelationIdWebAutoConfiguration.class)` — xem 2 test class trong module autoconfigure (kiểm tra back-off của `TaskDecorator`, cờ `enabled`, order/tên filter registration).
- Trong integration test của service, muốn assert correlation id: gửi request kèm header `X-Correlation-Id: test-123` và expect header cùng giá trị trong response.

## Lưu ý & bẫy thường gặp

1. **Correlation id không thay thế trace id**: `correlationId` là token tùy ý client truyền vào (dễ tra cứu, echo về client), `traceId` do hệ thống tracing sinh và lan truyền chuẩn W3C. Nên log cả hai.
2. **Header không hợp lệ bị thay im lặng**: giá trị quá 128 ký tự, chứa ký tự điều khiển hoặc non-ASCII → filter sinh id mới thay vì dùng lại (chống log injection). Client thấy response header khác giá trị mình gửi tức là giá trị gửi lên không đạt `CorrelationId.isValid`.
3. **MDC không tự qua thread**: `MdcTaskDecorator` chỉ áp cho executor mà Boot build (hoặc executor bạn gắn decorator). `CompletableFuture.supplyAsync(...)` với common `ForkJoinPool`, `new Thread(...)`, hay executor tự tạo không gắn decorator sẽ **mất** correlationId.
4. **Starter không tự xuất trace**: `micrometer-tracing-bridge-otel` chỉ tạo trace; không có `opentelemetry-exporter-otlp` + `management.otlp.tracing.endpoint` thì trace không đi đâu cả. Sampling mặc định của Boot là **0.1** (10%) — dev/staging nên nâng `management.tracing.sampling.probability`.
5. **Tag `application` trống nếu quên `spring.application.name`** — đặt tên app trong mọi service.
6. **Common tags trùng key bị bỏ qua**: key xuất hiện trước thắng (`application` → `environment` → `common-tags`), nên `common-tags.application: xyz` sẽ không có tác dụng.
7. **Metrics customizer cần actuator autoconfigure**: `MeterRegistryCustomizer` nằm trong `spring-boot-actuator-autoconfigure` (optional trong module autoconfigure) — dùng lẻ module autoconfigure mà không có actuator thì common tags không kích hoạt; starter đã kéo sẵn actuator.
8. **Forward header là việc của client**: service gọi đi mà quên forward `X-Correlation-Id` thì service phía sau sinh id mới — chuỗi log bị đứt. Chuẩn hóa `RestClient`/`WebClient` builder có interceptor như ví dụ ở trên (hoặc dùng client chuẩn của [javalibs-web](web.md) nếu có).
9. **Response header được set trước chain** — nếu ứng dụng tự đổi correlation id giữa chừng (ghi đè MDC), response header vẫn mang id do filter thiết lập ban đầu.
10. **Log pattern tự viết phải nhớ `%X{correlationId}`**: Boot chỉ tự thêm `traceId`/`spanId` vào pattern mặc định; và khi bạn khai báo `logging.pattern.console` hoàn toàn mới, cả phần correlation mặc định của Boot cũng bị thay — phải tự thêm cả `%X{traceId}`/`%X{spanId}` lẫn `%X{correlationId}`.
