# javalibs-logging

Bộ thư viện dùng chung để mọi microservice phát ra **một bản ghi JSON cho mỗi sự kiện log**, theo đúng một schema cố định: che sẵn dữ liệu nhạy cảm, gắn `user_id`/`ip`/`request`/`response` cho log trong phạm vi HTTP, và không cần mỗi service tự khai báo pattern hay encoder riêng.

## Mục đích

Log dạng text tự do (`2026-08-13 10:25:30 INFO Order created for user 12345`) dễ đọc trên máy dev nhưng khó khai thác ở quy mô nhiều service: mỗi đội tự đặt format riêng, hệ thống thu thập log (ELK, Loki...) phải parse bằng regex mong manh, và không ai đảm bảo trường `user_id` hay `status` nằm ở đúng vị trí trong mọi dòng log.

`javalibs-logging` chuẩn hóa điều đó bằng cách phát ra **một dòng JSON có schema cố định cho mọi sự kiện log** — không chỉ access-log mà cả log do code nghiệp vụ, Spring, Hibernate hay bất kỳ thư viện bên thứ ba nào phát ra — nhờ cắm thẳng vào Structured Logging SPI của Spring Boot 3.4+ thay vì tự viết logger hay thêm dependency ngoài.

## Ví dụ đầu ra

```json
{
  "timestamp": "2026-08-13T10:25:30.123Z",
  "level": "INFO",
  "message": "User login successful",
  "log_id": "f3a2b1c4-d5e6-7f89-0abc-1234567890ab",
  "service": "auth-service",
  "host": "server-01",
  "user_id": "user_12345",
  "ip": "192.168.1.10",
  "request":  { "method": "POST", "endpoint": "/api/login",
                "headers": { ... }, "body": { "password": "********" } },
  "response": { "status": 200, "latency_ms": 152, "response_body": { ... } },
  "tags": ["auth", "login", "user"],
  "errors": [],
  "metadata": { "env": "production", "version": "1.2.3" }
}
```

`request`/`response` chỉ xuất hiện trên dòng access-log kết thúc một HTTP request. Log phát ra ngoài phạm vi HTTP (`@Scheduled`, consumer Kafka, log lúc khởi động) không có `user_id`/`ip`/`request`/`response` — field đó **bị bỏ hẳn** chứ không ghi `null`. Ngoại lệ là `errors`, luôn có mặt, `[]` khi không có lỗi.

## Kiến trúc module

```
javalibs-logging (pom)
├── javalibs-logging-core                             # thuần Java, 0 Spring, 0 Logback
├── javalibs-logging-logback                           # formatter cắm vào Structured Logging SPI của Boot
├── javalibs-logging-spring                             # HttpAccessLogFilter, LogContext
├── javalibs-logging-spring-boot-autoconfigure         # auto-configuration + properties
└── javalibs-logging-spring-boot-starter               # gộp tất cả, chỉ pom
```

| Module | Nội dung chính |
|---|---|
| `javalibs-logging-core` | `LogFields` (hằng số tên field theo schema), `SensitiveKeys`/`SensitiveDataMasker` (denylist đệ quy), `ClientIpResolver` (XFF/X-Real-IP/remoteAddr), `LogHost`, `HttpRequestLog`/`HttpResponseLog`. |
| `javalibs-logging-logback` | `JavalibsJsonLogFormatter` (`extends JsonWriterStructuredLogFormatter<ILoggingEvent>`), `JavalibsLogFormatSettings` (đọc `javalibs.logging.*` từ `Environment`), `JavalibsLoggingEnvironmentPostProcessor`. |
| `javalibs-logging-spring` | `HttpAccessLogFilter` (`OncePerRequestFilter`, thay thế `RequestLoggingFilter` của `javalibs-web`), `PrincipalResolver` (SPI), `LogContext` (scope tag/metadata theo MDC). |
| `javalibs-logging-spring-boot-autoconfigure` | `LoggingProperties` (`javalibs.logging.*`), `AccessLogAutoConfiguration`. |
| `javalibs-logging-spring-boot-starter` | Không có mã nguồn; kéo theo 4 module trên. |

## Bật JSON log

**Mặc định `javalibs.logging.json.enabled` là `false`** — thêm starter vào một service **không** tự động biến console dev thành JSON. Biến console thành JSON ngay khi chỉ mới thêm dependency là hành vi bất ngờ và khó đọc khi phát triển cục bộ; mỗi service tự bật khi cần, thường là trong `application-prod.yml`:

```yaml
javalibs:
  logging:
    json:
      enabled: true
```

Ai muốn tự chỉ định formatter (bỏ qua flag trên) thì đặt thẳng property gốc của Spring Boot:

```yaml
logging:
  structured:
    format:
      console: io.javalibs.logging.logback.JavalibsJsonLogFormatter
```

`JavalibsLoggingEnvironmentPostProcessor` chỉ đụng tới đích **console** — đó là đích mà hạ tầng thu thập log (stdout → Docker/Kubernetes) thực sự đọc. Muốn ghi thêm JSON ra file thì tự đặt `logging.structured.format.file` trỏ vào cùng class; javalibs không tự quyết thay việc ghi file kéo theo quyết định về rotation và dung lượng đĩa.

## Bảng thuộc tính cấu hình

Toàn bộ nằm dưới `javalibs.logging.*`.

| Thuộc tính | Kiểu | Mặc định | Ý nghĩa |
|---|---|---|---|
| `json.enabled` | boolean | `false` | Bật JSON formatter cho console |
| `service` | String | `${spring.application.name}` | Field `service` |
| `host` | String | hostname máy | Field `host` |
| `environment` | String | (không đặt) | → `metadata.env` |
| `version` | String | (không đặt) | → `metadata.version` |
| `metadata.*` | Map | rỗng | Metadata tĩnh thêm vào mọi dòng |
| `tags` | List | rỗng | Tag tĩnh gắn vào mọi dòng |
| `masking.enabled` | boolean | `true` | Bật che dữ liệu nhạy cảm |
| `masking.keys` | List | rỗng | **Cộng dồn** vào denylist mặc định |
| `masking.value` | String | `********` | Giá trị thay thế |
| `stacktrace.enabled` | boolean | `true` | Ghi stacktrace vào `errors[]` |
| `stacktrace.max-length` | int | `4096` | Trần ký tự mỗi stacktrace |
| `access.enabled` | boolean | `true` | Đăng ký `HttpAccessLogFilter` |
| `access.include-headers` | boolean | `false` | Ghi header request |
| `access.included-headers` | List | `Content-Type, User-Agent, Accept` | Allowlist header |
| `access.include-body` | boolean | `false` | Ghi body request/response |
| `access.max-body-length` | int | `2048` | Trần ký tự mỗi body |
| `access.excluded-paths` | List | `/actuator/**` | Ant pattern bỏ qua |
| `access.trust-proxy` | boolean | `false` | Tin `X-Forwarded-For` khi phân giải `ip` |
| `access.slow-threshold-ms` | long | `0` (tắt) | Vượt ngưỡng → log `WARN` |

Ghi chú hành vi: `stacktrace.max-length` nhận giá trị **âm** sẽ rơi về mặc định `4096` (không phải `0`) — một giá trị âm thường mang ý "không giới hạn", và clamp về 0 sẽ xóa sạch stacktrace thay vì giữ nguyên nó.

## Che dữ liệu nhạy cảm

`SensitiveKeys` khớp tên field theo **toàn bộ tên đã chuẩn hóa** (lowercase, bỏ `_`/`-`/khoảng trắng), không phải khớp substring. Nhờ vậy `password`, `PASSWORD`, `Pass_Word`, `pass-word` đều dính cùng một luật, nhưng `tokens` (số nhiều) **không** khớp `token` (số ít), và `passenger` **không** bị che nhầm.

Denylist mặc định gồm **26 key**:

```
password, passwd, token, access_token, refresh_token, id_token,
secret, client_secret, authorization, api_key, apikey,
private_key, otp, pin, card_number, cvv, ssn,
cookie, set_cookie, x_api_key, proxy_authorization, session_id,
jwt, secret_key, api_secret, credit_card
```

`javalibs.logging.masking.keys` **cộng dồn** vào denylist mặc định, không thay thế — một service thêm key riêng (ví dụ `internal_ref`) sẽ không vô tình mở khóa toàn bộ 26 key mặc định. Ngược lại, thêm một key trùng tên field cấp cao của schema (ví dụ `message`) sẽ khiến field đó bị che ở **mọi** dòng log — tránh đặt tên riêng trùng với `LogFields` (`timestamp`, `level`, `message`, `log_id`, `service`, `host`, `user_id`, `ip`, `request`, `response`, `tags`, `errors`, `metadata`).

**Masking không thay thế việc không log dữ liệu không cần thiết.** Cơ chế che có giới hạn thật:

- **POJO/record tự định nghĩa truyền vào `addKeyValue` không được che.** Việc duyệt cấu trúc chỉ đi vào `Map`/`List`/mảng; một object tùy ý sẽ bị chuyển thành chuỗi bằng `toString()` trước khi tới bộ masking, nên masking không nhìn được vào bên trong nó. Chỉ truyền `Map`/`List`/giá trị vô hướng vào `addKeyValue` khi dữ liệu có thể chứa bí mật.
- **Body dạng `text/*` không che được** — chỉ body JSON (được parse thành `Map`) và `application/x-www-form-urlencoded` mới có cấu trúc để masking bám vào; văn bản thuần là một chuỗi không cấu trúc.
- **Body bị cắt do vượt `access.max-body-length` sẽ ghi placeholder `<truncated N bytes>` thay vì phần đầu của nội dung** — chủ ý về bảo mật: nếu ghi phần đầu bị cắt, chuỗi đó nằm ở `request.body` chưa được che (bản body JSON bị cắt không còn parse được thành `Map`, nên "trốn" hoàn toàn masking), rất dễ để lộ mật khẩu nằm ở đầu payload.
- **Secret nằm trong PATH của URL không được che** — masking chỉ chạm tới query string và body, không chạm tới path. Ví dụ `/reset-password/{token}` sẽ ghi nguyên token vào `request.endpoint`; không đặt secret trong path.

## API cho developer

```java
// một dòng: API chuẩn SLF4J 2.x, không cần class nào của javalibs
log.atInfo().addKeyValue("orderId", order.id()).log("Order created");

// cả một phạm vi: tag gắn cho mọi dòng log bên trong
try (LogContext.Scope scope = LogContext.tags("checkout")) {
    log.info("Cart validated");
}
```

`addKeyValue` gắn giá trị vào `metadata` (hoặc `request`/`response`/`tags` nếu dùng đúng key dành riêng); `LogContext.tags(...)` và `LogContext.put(key, value)` gắn qua MDC, tự khôi phục giá trị trước đó khi đóng scope nên các scope lồng nhau không phá nhau.

## Quan hệ với các module khác

- **`javalibs-web`** có sẵn `RequestLoggingFilter` cũ (phát dòng text `GET /x status=200 duration=12ms`). Filter đó **tự lùi** (`@ConditionalOnMissingClass`) khi `javalibs-logging-spring` có mặt trên classpath, để không log đôi mỗi request. Không cần đặt property nào — chỉ cần thêm dependency.
- **`javalibs-observability`**: `correlationId` (từ `CorrelationIdFilter`) cùng `traceId`/`spanId` (từ Micrometer Tracing, nếu có) đều nằm trong MDC nên tự chảy vào `metadata` của mọi dòng log — không cần cấu hình thêm.

## Cảnh báo vận hành

- **`access.include-body` mặc định tắt** vì đây là quyết định có ý thức về PII/GDPR, không phải mặc định vô tình. Bật nó sẽ **đệm trọn response trong bộ nhớ** (`ContentCachingResponseWrapper`) bất kể `max-body-length` — giới hạn đó chỉ áp lúc *ghi* log, không áp lúc *đệm*. Không bật trên service có endpoint tải file/export/streaming, hoặc loại chúng qua `access.excluded-paths`.
- **`access.trust-proxy` mặc định tắt** vì header `X-Forwarded-For` do client kiểm soát trên một ứng dụng lộ trực tiếp — tin nó vô điều kiện cho phép bất kỳ caller nào tự đặt `ip` trong log điều tra sự cố của hệ thống. Chỉ bật khi ứng dụng thật sự nằm sau reverse proxy có ghi đè header này.

Chi tiết schema từng field, luồng dữ liệu qua các filter và cách viết `PrincipalResolver` riêng: xem [`docs/modules/logging.md`](../docs/modules/logging.md).

## Sử dụng nhanh

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-logging-spring-boot-starter</artifactId>
</dependency>
```

Chi tiết starter: [`javalibs-logging-spring-boot-starter/README.md`](javalibs-logging-spring-boot-starter/README.md).
