# Thiết kế: `javalibs-logging` — structured logging dùng chung

**Ngày:** 2026-08-22
**Trạng thái:** Đã duyệt thiết kế, chờ lập kế hoạch triển khai

## 1. Mục tiêu & phạm vi

Chuẩn hóa cách mọi microservice trong hệ sinh thái phát ra log dưới dạng **một
bản ghi JSON cho mỗi sự kiện**, theo đúng schema đã thống nhất:

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

Phạm vi gồm:

- **Envelope cho mọi dòng log** — `timestamp`, `level`, `message`, `log_id`,
  `service`, `host`, `user_id`, `ip`, `tags`, `errors`, `metadata` xuất hiện
  trên *mọi* dòng log của ứng dụng, kể cả log do Spring/Hibernate/thư viện bên
  thứ ba phát ra.
- **`request` / `response`** — chỉ xuất hiện ở dòng access-log của một HTTP
  request, do filter của javalibs phát ra.
- **Che dữ liệu nhạy cảm** — denylist theo tên field, đệ quy vào JSON lồng nhau.
- **API cho developer** — SLF4J 2.x fluent (`addKeyValue`) cho từng dòng,
  `LogContext` cho phạm vi request.
- **Auto-configuration** theo chuẩn 4 tầng của repo, bật/tắt qua
  `javalibs.logging.*`.

**Ngoài phạm vi phiên bản đầu:** appender đẩy log trực tiếp sang
Elasticsearch/Loki/Kafka (hạ tầng tự thu thập stdout), sampling / rate-limit log,
log audit nghiệp vụ có schema riêng, hỗ trợ Log4j2 (chỉ Logback — runtime mặc
định của Spring Boot), reactive/WebFlux (chỉ servlet).

## 2. Quyết định nền tảng

### 2.1. Cơ chế phát JSON: Structured Logging SPI của Spring Boot

Boot 3.4+ cho phép cắm `StructuredLogFormatter<ILoggingEvent>` qua property
`logging.structured.format.console`. Đã kiểm chứng trên
`spring-boot-3.5.3.jar`, các API sau tồn tại và sẽ được dùng:

| API | Vai trò |
|---|---|
| `org.springframework.boot.logging.structured.StructuredLogFormatter<E>` | interface SPI |
| `…structured.JsonWriterStructuredLogFormatter<E>` | lớp cơ sở, nhận `Consumer<JsonWriter.Members<E>>` |
| `org.springframework.boot.json.JsonWriter.Members<T>` | `add(name, Function)`, `addMapEntries(...)`, `applyingValueProcessor(...)` |
| `org.springframework.boot.json.JsonParserFactory` | parse body JSON không cần Jackson |

Chọn hướng này vì: **mọi** dòng log đều đúng schema (không chỉ access-log),
không thêm dependency bên thứ ba (không cần `logstash-logback-encoder`), và
việc ghi/escape JSON do Boot đảm nhiệm.

**Ràng buộc phải tôn trọng:** formatter được `Instantiator` của Boot dựng ở giai
đoạn khởi tạo logging system — **trước khi có ApplicationContext**. Nó không lấy
được bean, chỉ nhận được `Environment` qua constructor. Hệ quả thiết kế:

- Mọi giá trị formatter cần (service, host, tag tĩnh, cấu hình masking) phải đọc
  trực tiếp từ `Environment`, không qua `@ConfigurationProperties`.
- `javalibs-logging-core` phải thuần Java, không phụ thuộc cơ chế binding của
  Boot, để phần logic này test được độc lập.
- `LoggingProperties` (`@ConfigurationProperties`) chỉ phục vụ tầng filter và
  autoconfigure, không phục vụ formatter.

### 2.2. Vị trí: family module mới `javalibs-logging`

Không đặt trong `javalibs-observability` vì starter của module đó kéo theo
`spring-boot-starter-actuator` + `micrometer-tracing-bridge-otel`; một service
chỉ muốn JSON log không nên phải nuốt cả hai. Không đặt trong `javalibs-web` vì
formatter áp cho mọi log chứ không riêng tầng web — đặt ở đó là sai tầng.

`javalibs-logging-core` phụ thuộc `javalibs-observability-core` (2 class, 0
Spring) để dùng lại `CorrelationId.MDC_KEY` thay vì khai báo trùng hằng số.

### 2.3. Quan hệ với `RequestLoggingFilter` hiện có

`javalibs-web` đang có `RequestLoggingFilter` phát một dòng text
(`GET /x status=200 duration=12ms`). Filter mới thay thế nó. Để hai module cùng
tồn tại mà không log đôi:

```java
@ConditionalOnMissingClass("io.javalibs.logging.spring.HttpAccessLogFilter")
public class RequestLoggingAutoConfiguration { … }
```

Chỉ tham chiếu theo **tên class dạng chuỗi**, nên `javalibs-web` không sinh
dependency ngược sang `javalibs-logging`. Service chỉ dùng `javalibs-web` giữ
nguyên hành vi cũ; service thêm `javalibs-logging` thì filter cũ tự lùi. Không
breaking change, không cần dev nhớ tắt property nào.

## 3. Bố cục module

```
javalibs-logging/
├── javalibs-logging-core                     # Java thuần, 0 Spring, 0 Logback
│   ├── LogFields                             # hằng số tên field + MDC key
│   ├── SensitiveDataMasker + MaskingConfig   # denylist đệ quy
│   ├── ClientIpResolver                      # XFF / X-Real-IP / remoteAddr
│   ├── LogHost                               # hostname có cache, fallback an toàn
│   └── HttpRequestLog, HttpResponseLog       # record bất biến + toMap()
├── javalibs-logging-logback
│   ├── JavalibsJsonLogFormatter              # extends JsonWriterStructuredLogFormatter
│   ├── JavalibsLogFormatSettings             # đọc javalibs.logging.* từ Environment
│   └── JavalibsLoggingEnvironmentPostProcessor
├── javalibs-logging-spring
│   ├── HttpAccessLogFilter                   # OncePerRequestFilter
│   ├── PrincipalResolver (SPI) + DefaultPrincipalResolver
│   └── LogContext                            # MDC-backed, AutoCloseable scope
├── javalibs-logging-spring-boot-autoconfigure
│   ├── LoggingProperties                     # javalibs.logging.*
│   └── AccessLogAutoConfiguration
└── javalibs-logging-spring-boot-starter      # chỉ pom + README
```

### 3.1. `javalibs-logging-core`

**`LogFields`** — hằng số tên field đúng schema (`timestamp`, `level`,
`message`, `log_id`, `service`, `host`, `user_id`, `ip`, `request`, `response`,
`tags`, `errors`, `metadata`) và MDC key mà javalibs ghi vào
(`userId`, `clientIp`, `logTags`). Có sẵn tập MDC key "đã tiêu thụ" để formatter
biết key nào đã lên field cấp cao và không lặp lại chúng trong `metadata`.

**`SensitiveDataMasker`** — duyệt đệ quy `Map` / `List` / `String`, thay giá trị
của key nhạy cảm bằng `********`.

- So khớp key sau khi chuẩn hóa: lowercase + bỏ `_`, `-`, khoảng trắng. Nhờ vậy
  `password`, `Pass_Word`, `PASSWORD`, `pass-word` đều dính cùng một luật.
- Denylist mặc định: `password`, `passwd`, `token`, `access_token`,
  `refresh_token`, `id_token`, `secret`, `client_secret`, `authorization`,
  `api_key`, `apikey`, `private_key`, `otp`, `pin`, `card_number`, `cvv`, `ssn`.
- Cấu hình của app **cộng dồn** vào mặc định, không thay thế — để một service
  thêm key riêng không vô tình mở khóa toàn bộ denylist.
- Có giới hạn độ sâu đệ quy để payload lồng nhau ác ý không gây `StackOverflow`.

**`ClientIpResolver`** — thứ tự `X-Forwarded-For` (entry đầu tiên, có validate
định dạng) → `X-Real-IP` → `remoteAddr`. **Chỉ tin header proxy khi
`trust-proxy = true`, mặc định `false`.** Nếu mặc định tin, bất kỳ client nào
cũng tự đặt được `ip` trong log của hệ thống — làm hỏng chính giá trị điều tra
sự cố của trường này.

**`LogHost`** — phân giải hostname một lần, cache lại; `UnknownHostException`
trả về `"unknown"` chứ không ném.

**`HttpRequestLog`** (`method`, `endpoint`, `headers`, `body`) và
**`HttpResponseLog`** (`status`, `latencyMs`, `responseBody`) — `record` bất
biến, `toMap()` trả `LinkedHashMap` giữ đúng thứ tự field như schema.

### 3.2. `javalibs-logging-logback`

**`JavalibsJsonLogFormatter extends JsonWriterStructuredLogFormatter<ILoggingEvent>`**
— constructor nhận `Environment`, dựng members đúng thứ tự schema:

| Field | Nguồn |
|---|---|
| `timestamp` | `event.getInstant()` → ISO-8601 UTC, mili giây |
| `level` | `event.getLevel()` |
| `message` | `event.getFormattedMessage()` |
| `log_id` | `UUID.randomUUID()` — duy nhất cho từng dòng |
| `service` | `javalibs.logging.service`, fallback `spring.application.name` |
| `host` | `javalibs.logging.host`, fallback `LogHost` |
| `user_id` | MDC `userId` |
| `ip` | MDC `clientIp` |
| `request` / `response` | `KeyValuePair` do `HttpAccessLogFilter` gắn |
| `tags` | tag tĩnh `javalibs.logging.tags` + MDC `logTags` + KeyValuePair `tags` |
| `errors` | `event.getThrowableProxy()`, đi hết chuỗi cause |
| `metadata` | `javalibs.logging.metadata.*` + `environment` → `env` + `version` + MDC còn lại + KeyValuePair còn lại |

**Quy tắc field vắng mặt:** một field chỉ được ghi khi có giá trị. Log phát ra
ngoài phạm vi HTTP (tác vụ `@Scheduled`, consumer Kafka, khởi động ứng dụng)
không có `user_id` / `ip` / `request` / `response` thì **bỏ hẳn field** chứ
không ghi `null` — dòng log gọn hơn và index phía thu thập không sinh field rỗng.
Ngoại lệ duy nhất là `errors`, luôn hiện diện dưới dạng `[]` khi không có lỗi,
đúng như schema đã thống nhất.

- `errors` là mảng `{ type, message, stacktrace }`, **luôn có mặt**, `[]` khi
  không có lỗi — đúng như schema. `stacktrace` cắt theo `stacktrace.max-length`.
- Masking cài qua `Members.applyingValueProcessor(...)` nên áp cho **mọi** giá
  trị đi qua writer, kể cả field lồng sâu trong `request.body`.
- MDC key đã lên field cấp cao (`userId`, `clientIp`, `logTags`, `correlationId`)
  không lặp lại trong `metadata`; `correlationId`, `traceId`, `spanId` đi vào
  `metadata` để nối được với `javalibs-observability`.

**`JavalibsLogFormatSettings`** — đọc và cache cấu hình từ `Environment` một lần
lúc dựng formatter, tránh tra property trên mỗi dòng log.

**`JavalibsLoggingEnvironmentPostProcessor`** — khi
`javalibs.logging.json.enabled=true` thì tự đặt
`logging.structured.format.console` trỏ vào formatter, để dev không phải nhớ tên
class đầy đủ. Đăng ký qua `META-INF/spring.factories`;
`EnvironmentPostProcessorApplicationListener` chạy trước
`LoggingApplicationListener` nên property kịp có hiệu lực. Nếu app đã tự đặt
`logging.structured.format.console` thì EPP không ghi đè.

EPP **chỉ** đụng tới đích `console` — đó là đích mà hạ tầng thu thập log
(stdout → Docker/Kubernetes) thực sự đọc. Service nào muốn thêm file JSON tự đặt
`logging.structured.format.file` trỏ vào cùng formatter; javalibs không tự quyết
thay vì ghi file kéo theo quyết định về rotation và dung lượng đĩa.

### 3.3. `javalibs-logging-spring`

**`HttpAccessLogFilter extends OncePerRequestFilter`** — thay thế
`RequestLoggingFilter`:

- Đặt MDC `userId` (qua `PrincipalResolver`) và `clientIp` (qua
  `ClientIpResolver`) **trước** khi vào chain, để mọi log nghiệp vụ phát ra
  trong request cũng mang hai field này; dọn trong `finally`.
- Kết thúc request, phát một dòng:
  ```java
  log.atInfo()
     .addKeyValue(LogFields.REQUEST, requestMap)
     .addKeyValue(LogFields.RESPONSE, responseMap)
     .log("{} {} {} {}ms", method, endpoint, status, latencyMs);
  ```
  Message vẫn là câu người đọc được, nên khi **không** bật JSON, dòng log degrade
  sạch thành text như trước chứ không thành dòng trống.
- Body: chỉ khi `include-body=true` và content-type là văn bản
  (`application/json`, `text/*`). Parse bằng `JsonParserFactory` để nhúng thành
  **object** đúng như schema; parse thất bại thì giữ nguyên chuỗi. Cắt theo
  `max-body-length` trước khi parse.
- Headers: chỉ khi `include-headers=true`, và chỉ những header trong allowlist
  `included-headers` (mặc định `Content-Type`, `User-Agent`, `Accept`). Đây là
  allowlist chứ không phải denylist vì header mang credential
  (`Authorization`, `Cookie`, `X-Api-Key`) là mặc định-nguy hiểm.
- `slow-threshold-ms > 0` và latency vượt ngưỡng → phát ở level `WARN`.
- Luôn `copyBodyToResponse()` trong `finally`.

**`PrincipalResolver`** — SPI một hàm, mặc định đọc
`request.getUserPrincipal().getName()`. Nhờ vậy module **không** phụ thuộc
`javalibs-security` hay Spring Security; app nào muốn lấy `userId` từ
`UserContext` chỉ cần đăng ký bean thay thế.

**`LogContext`** — tiện ích tĩnh trên MDC cho phạm vi request:

```java
try (var scope = LogContext.tags("auth", "login")) {
    log.info("User login successful");
}                       // scope.close() khôi phục trạng thái MDC trước đó
```

Có `LogContext.put(key, value)` cho metadata phạm vi request. `close()` khôi
phục giá trị cũ (không phải xóa trắng) để các scope lồng nhau không phá nhau.

### 3.4. `javalibs-logging-spring-boot-autoconfigure`

`LoggingProperties` (record, `@ConfigurationProperties("javalibs.logging")`) và
`AccessLogAutoConfiguration`:
`@ConditionalOnWebApplication(type = SERVLET)`,
`@ConditionalOnProperty(prefix = "javalibs.logging.access", name = "enabled", matchIfMissing = true)`,
bean `@ConditionalOnMissingBean`. Đăng ký filter ở order
`LOWEST_PRECEDENCE - 10` (giống filter cũ) để status ghi được là status cuối.

### 3.5. `javalibs-logging-spring-boot-starter`

Không chứa mã Java. Gom `-core`, `-logback`, `-spring`, `-autoconfigure`.

`logback-classic` và `spring-boot` là dependency **compile** của `-logback`
(formatter implement interface của Boot trên kiểu `ILoggingEvent` của Logback),
nên starter không cần khai báo lại — mọi ứng dụng Spring Boot đã có sẵn cả hai
qua `spring-boot-starter-logging`.

## 4. Bảng cấu hình

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

**Vì sao `json.enabled` mặc định `false`:** JSON hóa console của mọi dev ngay
khi thêm starter là hành vi bất ngờ và khó chịu khi phát triển cục bộ. Service
bật trong `application-prod.yml`.

**Vì sao `include-body` / `include-headers` mặc định `false`:** giữ đúng tinh
thần `includePayload=false` của filter hiện tại. Ghi body là quyết định có ý
thức về PII/GDPR, không phải mặc định vô tình.

## 5. Luồng dữ liệu

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

Mọi dòng log phát ra *bên trong* request đều đã mang `user_id`, `ip`,
`correlationId` nhờ MDC được đặt ở filter — không cần developer làm gì thêm.

## 6. Xử lý lỗi

- **Formatter tuyệt đối không được ném exception.** Một dòng log hỏng không được
  phép giết request đang phục vụ. Mỗi phép trích xuất field bọc phòng thủ: lỗi
  thì bỏ field đó, giữ nguyên phần còn lại của dòng.
- **Filter luôn `copyBodyToResponse()` và dọn MDC trong `finally`**, kể cả khi
  chain ném exception — nếu không, response bị nuốt hoặc MDC rò sang request kế
  tiếp trên cùng thread của pool.
- **Trần cứng cho body capture** (`max-body-length`), áp *trước* khi parse JSON,
  để một upload lớn không thổi bay heap.
- **Giới hạn độ sâu đệ quy** trong masker, chống payload lồng nhau ác ý.
- **`LogHost` nuốt `UnknownHostException`** → `"unknown"`; DNS hỏng không được
  làm ứng dụng không khởi động được.

## 7. Chiến lược test (TDD — viết test đỏ trước)

**`-core`** (không Spring, chạy mili giây):
- `SensitiveDataMasker`: map/list lồng nhau; biến thể tên key
  (`Pass_Word` / `PASSWORD` / `pass-word`); key thường **không** bị đụng; key
  cấu hình thêm không làm mất denylist mặc định; giới hạn độ sâu.
- `ClientIpResolver`: `trust-proxy=false` → **bỏ qua** `X-Forwarded-For` giả
  mạo; `trust-proxy=true` → lấy entry đầu; XFF rác → fallback `remoteAddr`.
- `LogHost`: có cache, fallback không ném.

**`-logback`**:
- Format một `ILoggingEvent` → **parse ngược JSON** → assert từng field và thứ
  tự field.
- `errors` là `[]` khi không có throwable; có `type`/`message`/`stacktrace` và
  đi hết chuỗi cause khi có.
- `metadata` gom được cả MDC lẫn `KeyValuePair`; MDC key đã lên field cấp cao
  không bị lặp.
- Masking áp được vào `request.body` lồng sâu.
- MDC không rò giữa hai event liên tiếp.
- Formatter không ném khi gặp event dị dạng (message `null`, MDC `null`).

**`-spring`**:
- `MockHttpServletRequest/Response`: status, latency, endpoint, method.
- Body đã bị che; content-type nhị phân bị bỏ qua; body vượt trần bị cắt.
- `excluded-paths` không phát log.
- **Response body vẫn tới được client** sau khi filter đọc (regression cho
  `copyBodyToResponse`).
- MDC sạch sau khi request kết thúc, kể cả khi chain ném exception.
- `LogContext`: scope lồng nhau khôi phục đúng giá trị trước đó.

**`-autoconfigure`** (`ApplicationContextRunner`):
- Bật/tắt qua `access.enabled`; `@ConditionalOnMissingBean` cho phép app ghi đè;
  không kích hoạt khi không phải web servlet.
- `JavalibsLoggingEnvironmentPostProcessor` thực sự đặt property, và **không**
  ghi đè khi app đã tự cấu hình.
- Với `javalibs-web-spring-boot-autoconfigure` ở test scope: khẳng định
  `RequestLoggingFilter` cũ **không** được đăng ký khi
  `HttpAccessLogFilter` có trên classpath.

## 8. Tài liệu & tích hợp repo

- `javalibs-logging/README.md` — tiếng Việt, theo phong cách README các module
  khác: mục đích, kiến trúc module, bảng thuộc tính, ví dụ JSON đầu ra, cách
  dùng nhanh.
- `javalibs-logging-spring-boot-starter/README.md`.
- `docs/modules/logging.md` — tham chiếu chi tiết.
- Cập nhật: bảng module trong `README.md` gốc, `docs/index.md`,
  `docs/configuration-reference.md`, `docs/architecture.md` (nếu có sơ đồ module).
- `javalibs-dependencies/pom.xml`: thêm 5 artifact vào BOM.
- `pom.xml` gốc: thêm `<module>javalibs-logging</module>`.
- `javalibs-web-spring-boot-autoconfigure`: thêm `@ConditionalOnMissingClass`
  vào `RequestLoggingAutoConfiguration` + test đi kèm.

## 9. Rủi ro & cách giảm thiểu

| Rủi ro | Giảm thiểu |
|---|---|
| Formatter dựng ngoài ApplicationContext, dễ viết nhầm thành cần bean | Toàn bộ cấu hình qua `Environment`; core thuần Java; có test dựng formatter trần không context |
| Ghi body làm rò PII vào log tập trung | Mặc định tắt; masking bật sẵn; header dùng allowlist; tài liệu nêu rõ trách nhiệm GDPR |
| `ip` bị giả mạo qua `X-Forwarded-For` | `trust-proxy` mặc định `false` |
| Log đôi mỗi request khi dùng cả web lẫn logging | `@ConditionalOnMissingClass` + test khẳng định |
| API structured logging của Boot đổi ở 4.x | Cô lập toàn bộ phụ thuộc Boot trong `-logback`; core/spring không biết tới nó |
| Chi phí CPU khi masking mọi dòng log | Masker chỉ duyệt cấu trúc đã có, không parse lại; `masking.enabled=false` tắt được |
