# javalibs-logging

> Structured JSON logging dùng chung: một bản ghi JSON có schema cố định cho mọi sự kiện log, che dữ liệu nhạy cảm theo denylist đệ quy, HTTP access log filter thay thế `RequestLoggingFilter` của `javalibs-web`, và formatter cắm thẳng vào Structured Logging SPI của Spring Boot 3.4+ (không cần dependency ngoài).

## Artifacts

| Artifact (`groupId: io.javalibs`) | Nội dung | Phụ thuộc chính |
|---|---|---|
| `javalibs-logging-core` | Java thuần, không phụ thuộc framework: `LogFields`, `SensitiveKeys`/`SensitiveDataMasker`, `ClientIpResolver`, `LogHost`, `HttpRequestLog`/`HttpResponseLog` | `javalibs-observability-core` (chỉ để dùng lại `CorrelationId.MDC_KEY`), JDK |
| `javalibs-logging-logback` | `JavalibsJsonLogFormatter` (`extends JsonWriterStructuredLogFormatter<ILoggingEvent>`), `JavalibsLogFormatSettings`, `JavalibsLoggingEnvironmentPostProcessor` | core, `logback-classic`, `spring-boot` (compile — mọi ứng dụng Boot đã có sẵn qua `spring-boot-starter-logging`) |
| `javalibs-logging-spring` | `HttpAccessLogFilter` (`OncePerRequestFilter`), `PrincipalResolver` (SPI) + mặc định, `LogContext` | core, `spring-web`, `jakarta.servlet-api` (provided) |
| `javalibs-logging-spring-boot-autoconfigure` | `LoggingProperties` (`javalibs.logging.*`), `AccessLogAutoConfiguration` | spring module, logback module, `spring-boot-autoconfigure` |
| `javalibs-logging-spring-boot-starter` | Không có code — gom 4 module trên | — |

## Khi nào dùng / không dùng

**Dùng khi:**

- Muốn mọi dòng log — kể cả log do Spring/Hibernate/thư viện bên thứ ba phát ra, không chỉ access-log — có cùng một schema JSON để hạ tầng thu thập log parse được không cần regex.
- Muốn access log HTTP tự động kèm `request`/`response`, `user_id`, `ip`, không phải tự viết filter.
- Muốn che dữ liệu nhạy cảm (password, token, secret...) trước khi nó rời process, mà không phải tự viết bộ duyệt JSON đệ quy.

**Không dùng khi:**

- Ứng dụng WebFlux — `HttpAccessLogFilter` là `OncePerRequestFilter` (servlet), không có phiên bản reactive trong phạm vi hiện tại.
- Cần đẩy log trực tiếp sang Elasticsearch/Loki/Kafka từ trong ứng dụng — module chỉ phát JSON ra stdout; việc thu thập là việc của hạ tầng (Filebeat, Promtail, Fluent Bit...).
- Cần Log4j2 — formatter chỉ implement cho Logback, runtime mặc định của Spring Boot.

## Các thành phần chính

### javalibs-logging-core (package `io.javalibs.logging`)

#### `LogFields` — hằng số tên field

Tên field cấp cao (`timestamp`, `level`, `message`, `log_id`, `service`, `host`, `user_id`, `ip`, `request`, `response`, `tags`, `errors`, `metadata`), tên field lồng trong `request`/`response`/`errors[]`, và 3 MDC key javalibs tự ghi: `userId`, `clientIp`, `logTags`. `PROMOTED_MDC_KEYS` liệt kê MDC key nào đã lên field cấp cao, để formatter không lặp lại chúng trong `metadata`.

#### `SensitiveKeys` — chính sách "tên field nào là nhạy cảm"

So khớp trên **tên đã chuẩn hóa toàn bộ** (lowercase, bỏ `_`/`-`/khoảng trắng) — không phải substring. `withAdditional(...)` cộng dồn key cấu hình vào 26 key mặc định (xem README module); `none()` dùng khi tắt masking.

#### `SensitiveDataMasker` — che body không phải JSON

Che `application/x-www-form-urlencoded` (`maskFormEncoded`, dùng cho cả query string lẫn body dạng form) bằng cách tách từng cặp `key=value`, percent-decode tên field rồi hỏi `SensitiveKeys`. Đây là trường hợp duy nhất mà bộ duyệt cấu trúc của Spring Boot (§ dưới) không nhìn xuyên qua được, vì cả body chỉ là một chuỗi phẳng tại `request.body`.

#### `ClientIpResolver` — phân giải `ip`

Thứ tự khi `trustProxy=true`: `X-Forwarded-For` (entry đầu tiên, validate định dạng hex/`.`/`:`/`%`) → `X-Real-IP` → `remoteAddr`. Khi `trustProxy=false` (mặc định), header proxy bị bỏ qua hoàn toàn, chỉ dùng `remoteAddr`.

#### `LogHost` — hostname

Ưu tiên biến môi trường `HOSTNAME` (container runtime thường set thành tên container/pod, và tránh reverse DNS lookup); không có thì `InetAddress.getLocalHost()`; mọi lỗi (kể cả `SecurityException` khi đọc biến môi trường) đều rơi về `"unknown"` chứ không ném — DNS hỏng không được làm ứng dụng không khởi động được. Kết quả cache một lần trong static initializer.

#### `HttpRequestLog` / `HttpResponseLog`

`record` bất biến, `toMap()` trả `LinkedHashMap` theo đúng thứ tự field của schema, **bỏ hẳn field rỗng** (`headers`/`body`/`response_body` chỉ xuất hiện khi có giá trị).

### javalibs-logging-logback (package `io.javalibs.logging.logback`)

#### `JavalibsJsonLogFormatter extends JsonWriterStructuredLogFormatter<ILoggingEvent>`

Constructor nhận `Environment` (được `Instantiator` của Spring Boot dựng **trước khi có `ApplicationContext`**, nên không lấy được bean nào). Dựng các member theo đúng thứ tự schema:

| Field | Nguồn |
|---|---|
| `timestamp` | `event.getInstant()` → định dạng `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`, UTC |
| `level` | `event.getLevel()` |
| `message` | `event.getFormattedMessage()` |
| `log_id` | `UUID.randomUUID()` — sinh mới cho từng dòng |
| `service` | `javalibs.logging.service`, fallback `spring.application.name`; bỏ field nếu cả hai đều rỗng |
| `host` | `javalibs.logging.host`, fallback `LogHost.current()` |
| `user_id` | MDC `userId` |
| `ip` | MDC `clientIp` |
| `request` / `response` | `KeyValuePair` cùng tên do `HttpAccessLogFilter` gắn qua `log.atInfo().addKeyValue(...)` |
| `tags` | hợp nhất: tag tĩnh `javalibs.logging.tags` + MDC `logTags` (tách bởi dấu phẩy) + `KeyValuePair` tên `tags` (nếu event tự thêm) |
| `errors` | `event.getThrowableProxy()`, đi hết chuỗi `cause` (tối đa 10 phần tử, có `Set` chống chu trình) |
| `metadata` | `javalibs.logging.metadata.*` + `environment` → `env` + `version` → `version` + MDC còn lại (trừ 3 key đã lên field cấp cao) + `KeyValuePair` còn lại (trừ `request`/`response`/`tags`) |

**Quy tắc field vắng mặt:** một field chỉ được ghi khi có giá trị (`.whenHasLength()` / `.whenNotNull()` / `.whenNotEmpty()`). Log phát ra ngoài phạm vi HTTP (`@Scheduled`, consumer Kafka, khởi động ứng dụng) không có `user_id`/`ip`/`request`/`response` thì **bỏ hẳn field** — dòng log gọn hơn, hệ thống index phía sau không sinh field rỗng. Ngoại lệ duy nhất là `errors`: luôn hiện diện, `[]` khi không có lỗi.

Masking cài qua `Members.applyingValueProcessor(...)` với một `JsonWriter.ValueProcessor<Object>` (chủ đích không dùng `String` — một secret dạng số như `"pin": 1234` sẽ lọt qua bộ xử lý kiểu `String`) chặn theo `MemberPath.name()` và hỏi `SensitiveKeys.isSensitive(...)`. `null` không bị đụng; chỉ áp dụng khi `javalibs.logging.masking.enabled=true`.

#### `JavalibsLogFormatSettings` — đọc `Environment` một lần

Snapshot toàn bộ cấu hình cần cho formatter, đọc và cache lúc dựng formatter để không tra property trên mỗi dòng log. Hai điểm hành vi cần biết:

- `stacktrace.max-length` âm → fallback về `4096` (`DEFAULT_STACKTRACE_MAX_LENGTH`), không phải `0` — một giá trị âm thường mang ý "không giới hạn", clamp về 0 sẽ xóa sạch stacktrace.
- `masking.value` rỗng/blank → fallback về `SensitiveDataMasker.DEFAULT_MASK` (`********`), khớp với guard tương tự trong `SensitiveDataMasker`, để cấu hình rỗng không làm hai nơi che bằng hai giá trị khác nhau.

#### `JavalibsLoggingEnvironmentPostProcessor`

Khi `javalibs.logging.json.enabled=true` và ứng dụng **chưa** tự đặt `logging.structured.format.console`, tự thêm property đó trỏ vào `JavalibsJsonLogFormatter`. Đăng ký qua `META-INF/spring.factories` (`EnvironmentPostProcessor`), `getOrder()` = `LOWEST_PRECEDENCE` — chạy trước `LoggingApplicationListener` nên property kịp có hiệu lực khi logging system khởi tạo. Chỉ đụng đích **console**; muốn ghi JSON ra file thì tự đặt `logging.structured.format.file`.

### javalibs-logging-spring (package `io.javalibs.logging.spring`)

#### `HttpAccessLogFilter extends OncePerRequestFilter`

- Đặt MDC `userId` (qua `PrincipalResolver`) và `clientIp` (qua `ClientIpResolver`) **trước** khi vào chain, để mọi log nghiệp vụ phát ra trong request cũng mang hai field này; khôi phục giá trị MDC trước đó trong `finally` (không xóa trắng — tránh phá scope lồng nhau/rò rỉ giữa các request trên cùng thread pool).
- Kết thúc request, phát một dòng ở `INFO` (hoặc `WARN` nếu `slow-threshold-ms > 0` và latency vượt ngưỡng):
  ```java
  log.atInfo()
     .addKeyValue(LogFields.REQUEST, requestLog.toMap())
     .addKeyValue(LogFields.RESPONSE, responseLog.toMap())
     .log("{} {} {} {}ms", method, endpoint, status, latencyMs);
  ```
  Message vẫn là câu người đọc được, nên khi **chưa** bật JSON, dòng log degrade sạch thành text như trước chứ không thành dòng trống.
- Body: chỉ đọc khi `include-body=true`, chỉ với content-type văn bản (`application/json` và biến thể `+json`, `application/x-www-form-urlencoded`, `text/*`). JSON được parse bằng `JsonParserFactory` thành `Map` để nhúng thành **object** đúng schema; parse thất bại thì giữ nguyên chuỗi thô. Body vượt `max-body-length` bị thay bằng `<truncated N bytes>` cho mọi content-type trừ form-encoded (form-encoded vẫn được che value-by-value dù bị cắt, vì mỗi cặp `key=value` tự đứng độc lập).
- Headers: chỉ khi `include-headers=true`, chỉ những header trong allowlist `included-headers` (mặc định `Content-Type`, `User-Agent`, `Accept`) — cố ý dùng allowlist vì header mang credential (`Authorization`, `Cookie`, `X-Api-Key`) là đúng loại header một denylist hay bỏ sót.
- Query string bị che bằng `SensitiveDataMasker.maskFormEncoded(...)` trước khi ghép vào `endpoint`; **path** của URL (phần trước `?`) không đi qua masking.
- Yêu cầu async (`DeferredResult`, `Callable`, `StreamingResponseBody`, SSE emitter) đi qua filter hai lần; filter dùng `shouldNotFilterAsyncDispatch() = false` và chỉ log/`copyBodyToResponse()` ở dispatch thực sự kết thúc request (`!isAsyncStarted(...)`), tránh ghi một dòng log giả với status `200`/latency gần `0ms` ở lượt dispatch đầu.
- Luôn `copyBodyToResponse()` trong `finally`, kể cả khi chain ném exception hoặc việc ghi log tự nó ném lỗi (lỗi ghi log bị bắt và log ở `WARN`, không làm hỏng response).

#### `PrincipalResolver` — SPI một hàm cho `user_id`

```java
@FunctionalInterface
public interface PrincipalResolver {
    PrincipalResolver DEFAULT = request -> {
        Principal principal = request.getUserPrincipal();
        return (principal != null) ? principal.getName() : null;
    };

    String resolve(HttpServletRequest request);
}
```

Mặc định đọc `HttpServletRequest.getUserPrincipal()` — servlet container và Spring Security đều tự điền field này. Module cố tình **không** phụ thuộc `javalibs-security` hay Spring Security; muốn lấy `userId` từ nguồn khác (`UserContext`, header, JWT claim tùy biến) chỉ cần đăng ký bean `PrincipalResolver` của riêng bạn:

```java
@Bean
PrincipalResolver principalResolver(UserContext userContext) {
    return request -> {
        var user = userContext.currentUserOrNull();
        return (user != null) ? user.id() : null;
    };
}
```

`AccessLogAutoConfiguration.javalibsPrincipalResolver()` là `@ConditionalOnMissingBean`, nên bean tự khai báo tự động thay thế mặc định. Nếu `resolve(...)` ném exception, filter bắt lỗi và coi như không có user id — không làm hỏng request.

#### `LogContext` — scope MDC tĩnh

```java
try (LogContext.Scope scope = LogContext.tags("checkout")) {
    log.info("Cart validated"); // mang tag "checkout"
}
```

`LogContext.tags(...)` hợp nhất tag mới với tag đã có trong scope hiện tại. `LogContext.put(key, value)` đặt một entry MDC tùy ý cho metadata phạm vi request. `close()` khôi phục giá trị MDC **trước đó** (không xóa trắng), nên các scope lồng nhau không phá nhau và không rò rỉ khi thread được tái sử dụng trong pool.

### javalibs-logging-spring-boot-autoconfigure (package `io.javalibs.logging.autoconfigure`)

#### `LoggingProperties` — bind `javalibs.logging.*`

Record `@ConfigurationProperties("javalibs.logging")`, gồm các nested record `Json`, `Masking`, `Stacktrace`, `Access`. Chỉ phục vụ tầng filter/autoconfigure — formatter (`JavalibsLogFormatSettings`) đọc thẳng `Environment` như đã nói ở trên, vì nó được dựng trước khi có `ApplicationContext`. Hai bộ đọc cấu hình cùng namespace và cùng giá trị mặc định; đây là một tính năng mô tả bằng hai đường đọc, không phải hai tính năng.

#### `AccessLogAutoConfiguration`

`@ConditionalOnWebApplication(type = SERVLET)`, `@ConditionalOnProperty(prefix = "javalibs.logging.access", name = "enabled", havingValue = "true", matchIfMissing = true)`. Đăng ký `PrincipalResolver` mặc định (`@ConditionalOnMissingBean`) và `FilterRegistrationBean<HttpAccessLogFilter>` (`@ConditionalOnMissingBean`, order `LOWEST_PRECEDENCE - 10` — giống filter cũ của `javalibs-web`, để status ghi được là status cuối cùng).

### javalibs-logging-spring-boot-starter

Không có mã nguồn; kéo theo `core` + `logback` + `spring` + `autoconfigure`. `logback-classic` và `spring-boot` là dependency **compile** của module `logback` nên starter không khai báo lại — mọi ứng dụng Spring Boot đã có sẵn cả hai qua `spring-boot-starter-logging`.

## Luồng dữ liệu

```
HTTP request
   │
   ├─► CorrelationIdFilter (observability, order HIGHEST+10)
   │      MDC: correlationId
   │
   ├─► [Micrometer Tracing]  MDC: traceId, spanId
   │
   ├─► HttpAccessLogFilter (order LOWEST-10)
   │      MDC: userId, clientIp        ← trước chain
   │      bọc ContentCachingRequest/ResponseWrapper (khi include-body)
   │      │
   │      └─► controller / service
   │             log.atInfo().addKeyValue("orderId", id).log("…")
   │             LogContext.tags("checkout")
   │             │
   │             └─► JavalibsJsonLogFormatter
   │                    envelope + metadata (MDC & KeyValuePair) + masking
   │                    → stdout: 1 dòng JSON
   │
   │      cuối request: phát dòng access-log kèm request/response
   │      finally: copyBodyToResponse() + dọn MDC
   ▼
HTTP response
```

Mọi dòng log phát ra *bên trong* request đều đã mang `user_id`, `ip`, `correlationId` nhờ MDC được đặt ở filter — không cần developer làm gì thêm.

## Cấu hình

Toàn bộ thuộc tính nằm dưới `javalibs.logging.*` (bind vào `LoggingProperties`):

| Thuộc tính | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `javalibs.logging.json.enabled` | boolean | `false` | Bật JSON formatter cho console |
| `javalibs.logging.service` | String | `${spring.application.name}` | Field `service` |
| `javalibs.logging.host` | String | hostname máy | Field `host` |
| `javalibs.logging.environment` | String | (không đặt) | → `metadata.env` |
| `javalibs.logging.version` | String | (không đặt) | → `metadata.version` |
| `javalibs.logging.metadata.*` | Map | rỗng | Metadata tĩnh thêm vào mọi dòng |
| `javalibs.logging.tags` | List | rỗng | Tag tĩnh gắn vào mọi dòng |
| `javalibs.logging.masking.enabled` | boolean | `true` | Bật che dữ liệu nhạy cảm |
| `javalibs.logging.masking.keys` | List | rỗng | **Cộng dồn** vào denylist mặc định (26 key) |
| `javalibs.logging.masking.value` | String | `********` | Giá trị thay thế |
| `javalibs.logging.stacktrace.enabled` | boolean | `true` | Ghi stacktrace vào `errors[]` |
| `javalibs.logging.stacktrace.max-length` | int | `4096` | Trần ký tự mỗi stacktrace; giá trị âm rơi về mặc định |
| `javalibs.logging.access.enabled` | boolean | `true` | Đăng ký `HttpAccessLogFilter` |
| `javalibs.logging.access.include-headers` | boolean | `false` | Ghi header request |
| `javalibs.logging.access.included-headers` | List | `Content-Type, User-Agent, Accept` | Allowlist header |
| `javalibs.logging.access.include-body` | boolean | `false` | Ghi body request/response |
| `javalibs.logging.access.max-body-length` | int | `2048` | Trần ký tự mỗi body (không áp cho việc đệm response trong bộ nhớ) |
| `javalibs.logging.access.excluded-paths` | List | `/actuator/**` | Ant pattern bỏ qua |
| `javalibs.logging.access.trust-proxy` | boolean | `false` | Tin `X-Forwarded-For`/`X-Real-IP` khi phân giải `ip` |
| `javalibs.logging.access.slow-threshold-ms` | long | `0` (tắt) | Vượt ngưỡng → log `WARN` |

## Hướng dẫn sử dụng

### 1. Thêm starter

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-logging-spring-boot-starter</artifactId>
</dependency>
```

Chỉ cần từng phần: `javalibs-logging-spring` nếu tự đăng ký filter (không auto-config), `javalibs-logging-core` nếu chỉ cần tiện ích masking/IP/host thuần Java.

### 2. Bật JSON và cấu hình cơ bản

```yaml
javalibs:
  logging:
    json:
      enabled: true
    service: orders-api
    environment: prod
    version: 1.2.3
    tags: [orders]
    access:
      slow-threshold-ms: 1000
```

### 3. Viết `PrincipalResolver` riêng (không phụ thuộc Spring Security)

```java
@Bean
PrincipalResolver principalResolver(UserContext userContext) {
    return request -> {
        var user = userContext.currentUserOrNull();
        return (user != null) ? user.id() : null;
    };
}
```

### 4. Gắn tag/metadata theo phạm vi công việc

```java
try (LogContext.Scope scope = LogContext.tags("checkout", "payment")) {
    log.info("Charging card");   // mang cả 2 tag
    paymentGateway.charge(order);
}
```

## Ghi đè & mở rộng

- **Tắt JSON, giữ access log**: không đặt `json.enabled` (mặc định `false`) — `HttpAccessLogFilter` vẫn chạy và phát dòng text người đọc được.
- **Tắt access log, giữ JSON formatter**: `javalibs.logging.access.enabled=false` — mọi log vẫn thành JSON, chỉ không có filter tự phát dòng access-log.
- **Tắt masking**: `javalibs.logging.masking.enabled=false` — không khuyến nghị cho production; chỉ hữu ích khi debug cục bộ cần thấy giá trị thật.
- **Tự đăng ký `FilterRegistrationBean<HttpAccessLogFilter>`**: khai báo bean cùng kiểu generic sẽ khiến bean mặc định back off (xem javadoc `AccessLogAutoConfiguration.javalibsHttpAccessLogFilter`).
- **Ghi JSON ra file**: tự đặt `logging.structured.format.file=io.javalibs.logging.logback.JavalibsJsonLogFormatter` — javalibs không tự bật để tránh áp đặt chính sách rotation/dung lượng đĩa.

## Testing

- `-core`: test `SensitiveDataMasker`/`SensitiveKeys` bằng chuỗi form-encoded và các biến thể tên key; `ClientIpResolver` với `trustProxy` bật/tắt; `LogHost` fallback không ném.
- `-logback`: format một `ILoggingEvent` rồi parse ngược JSON để assert từng field và thứ tự; `errors` rỗng/có throwable; MDC không rò giữa hai event liên tiếp; formatter không ném khi gặp event dị dạng (message `null`, MDC `null`).
- `-spring`: `MockHttpServletRequest`/`MockHttpServletResponse` cho status/latency/endpoint/method; body bị che, content-type nhị phân bị bỏ qua, body vượt trần bị cắt thành placeholder; `excluded-paths` không phát log; response body vẫn tới được client sau khi filter đọc (`copyBodyToResponse`); MDC sạch sau request kể cả khi chain ném exception.
- `-autoconfigure`: `ApplicationContextRunner`/`WebApplicationContextRunner` cho bật/tắt qua `access.enabled`, `@ConditionalOnMissingBean` cho phép ghi đè, không kích hoạt khi không phải web servlet; `JavalibsLoggingEnvironmentPostProcessor` thực sự đặt property và không ghi đè khi app đã tự cấu hình; với `javalibs-web-spring-boot-autoconfigure` ở test scope: `RequestLoggingFilter` cũ không được đăng ký khi `HttpAccessLogFilter` có trên classpath (xem `WebRequestLoggingBackOffTest`).

## Lưu ý & bẫy thường gặp

1. **POJO/record tự định nghĩa truyền vào `addKeyValue` không được che.** Bộ duyệt cấu trúc của `JsonWriter.Members` chỉ đi vào `Map`/`List`/mảng; một object tùy ý bị chuyển bằng `toString()` trước khi tới masking. Chỉ truyền `Map`/`List`/giá trị vô hướng vào `addKeyValue` khi dữ liệu có thể mang bí mật — ví dụ `log.atInfo().addKeyValue("user", Map.of("id", id))` thay vì truyền thẳng entity/record chứa mật khẩu.
2. **Khớp denylist là khớp toàn bộ tên đã chuẩn hóa, không phải substring.** `tokens` (số nhiều) không khớp `token`; `passenger` không bị che nhầm vì chứa `pass`. Service có tên field riêng cần che thì tự thêm qua `masking.keys` — property này cộng dồn, không thay thế.
3. **Body dạng `text/*` không che được** — chỉ JSON (parse thành `Map`) và form-urlencoded mới có cấu trúc để masking bám vào. Một endpoint trả `text/plain` chứa dữ liệu nhạy cảm sẽ đi thẳng vào log nếu bật `include-body`.
4. **Body bị cắt thành placeholder `<truncated N bytes>`, không phải nội dung đã cắt.** Đây là đánh đổi bảo mật có chủ đích: một body JSON bị cắt ngang không còn parse được thành `Map`, nên nằm ở `request.body` dưới dạng chuỗi thô và **né hoàn toàn** masking — ghi phần đầu bị cắt ra log rất dễ để lộ mật khẩu nằm ở đầu payload.
5. **Secret nằm trong path của URL không được che.** Ví dụ `/reset-password/{token}` — masking chỉ chạm tới query string và body. Đừng đặt secret trong path; dùng query string hoặc body nếu bắt buộc phải truyền, hoặc loại hẳn endpoint đó khỏi `access.excluded-paths`.
6. **Bật `access.include-body` sẽ đệm trọn response trong bộ nhớ (`ContentCachingResponseWrapper`), bất kể `max-body-length`** — giới hạn đó chỉ áp lúc ghi log, không áp lúc đệm. Không bật trên service có endpoint tải file/export/streaming, hoặc loại chúng qua `access.excluded-paths`; đây là rủi ro OOM thật, không chỉ lý thuyết.
7. **`masking.keys` thêm một key trùng tên field cấp cao của schema sẽ che cả field đó.** Thêm `message` vào denylist sẽ khiến `"message"` của **mọi** dòng log (không chỉ log nghiệp vụ) thành `********`. Tránh đặt key riêng trùng với `LogFields` (`timestamp`, `level`, `message`, `log_id`, `service`, `host`, `user_id`, `ip`, `request`, `response`, `tags`, `errors`, `metadata`).
8. **`javalibs-web` tự lùi, không cần đặt property.** `RequestLoggingAutoConfiguration` của `javalibs-web` có `@ConditionalOnMissingClass("io.javalibs.logging.spring.HttpAccessLogFilter")` — chỉ tham chiếu theo tên class dạng chuỗi nên `javalibs-web` không sinh dependency ngược sang module này. Thêm `javalibs-logging-spring` (trực tiếp hoặc qua starter) là đủ để filter cũ tự tắt.
9. **`json.enabled=false` không tắt masking hay access log** — chỉ tắt định dạng JSON của console. `HttpAccessLogFilter` và masking vẫn hoạt động bình thường, chỉ khác là dòng log ra text thay vì JSON.
