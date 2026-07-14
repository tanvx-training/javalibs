# javalibs-web

> Tầng HTTP/REST chuẩn hóa cho mọi service javalibs: khuôn dạng response thống nhất (`ApiResponse`, `PageResponse`), hợp đồng lỗi (`ErrorResponse` + `ErrorCode`), xử lý exception toàn cục, ghi log request và CORS filter cấu hình bằng property.

## Artifacts

| artifactId | Packaging | Mô tả |
|---|---|---|
| `javalibs-web` | pom | Parent gộp 4 submodule |
| `javalibs-web-core` | jar | Hợp đồng thuần Java, **zero dependency runtime**: `ApiResponse`, `PageResponse`, `ErrorResponse`, `ErrorCode`/`CommonErrorCode`/`BusinessErrorCode`, các exception chuẩn. Package `io.javalibs.web` |
| `javalibs-web-spring` | jar | Tích hợp Spring MVC: `GlobalExceptionHandler` (`@RestControllerAdvice`), `RequestLoggingFilter` (`OncePerRequestFilter`), `CorsSupport`. Package `io.javalibs.web.spring` |
| `javalibs-web-spring-boot-autoconfigure` | jar | `WebAutoConfiguration`, `RequestLoggingAutoConfiguration`, `CorsAutoConfiguration` + `WebProperties` (namespace `javalibs.web.*`). Package `io.javalibs.web.autoconfigure` |
| `javalibs-web-spring-boot-starter` | jar | Starter không chứa code: kéo core + spring + autoconfigure + `spring-boot-starter-web` + `spring-boot-starter-validation` |

## Khi nào dùng / không dùng

**Dùng khi:**

- Xây REST API bằng Spring MVC (servlet) và muốn mọi service trả về cùng một định dạng JSON cho cả thành công lẫn lỗi.
- Muốn có sẵn xử lý exception toàn cục, log request và CORS mà không phải viết lại boilerplate cho từng service.
- Cần error catalog nghiệp vụ ổn định theo quy ước `ERR-<DOMAIN>-<SEQ>` để frontend/client lập trình theo mã lỗi thay vì parse message.

**Không dùng khi:**

- Ứng dụng WebFlux (reactive) — các auto-configuration chỉ kích hoạt với `ConditionalOnWebApplication.Type.SERVLET`.
- Chỉ cần DTO thuần không kèm Spring: khi đó chỉ phụ thuộc `javalibs-web-core` (không có dependency runtime nào) thay vì cả starter.
- Service đã có chuẩn error contract riêng khác hoàn toàn — khi đó tắt `javalibs.web.exception-handler.enabled=false` hoặc đừng thêm starter.

## Các thành phần chính

### `ApiResponse<T>` (record, `javalibs-web-core`)

Envelope chuẩn cho mọi response: `(boolean success, T data, String message, Instant timestamp, String traceId)`.

| Factory method | Kết quả |
|---|---|
| `ApiResponse.ok(T data)` | `success=true`, `timestamp=Instant.now()`, `message=null`, `traceId=null` |
| `ApiResponse.ok(T data, String message)` | như trên, kèm message |
| `ApiResponse.error(String message)` | `success=false`, `data=null` |
| `ApiResponse.error(String message, String traceId)` | như trên, kèm traceId |

### `PageResponse<T>` (record, `javalibs-web-core`)

Envelope phân trang không phụ thuộc Spring, page tính từ 0: `(List<T> content, int page, int size, long totalElements, int totalPages, boolean hasNext, boolean hasPrevious)`. Constructor chính defensive-copy `content` (null → list rỗng).

- `static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements)` — tự tính `totalPages` (`ceil(totalElements/size)`, bằng 0 khi `size <= 0`), `hasNext` (`page + 1 < totalPages`), `hasPrevious` (`page > 0 && totalPages > 0`).
- `<U> PageResponse<U> map(Function<T, U> mapper)` — map từng phần tử, giữ nguyên toàn bộ metadata phân trang.

### `ErrorCode` / `CommonErrorCode` / `BusinessErrorCode`

`ErrorCode` là interface 2 method: `String code()` và `int httpStatus()`.

`CommonErrorCode` — enum các lỗi kỹ thuật dùng chung:

| Hằng | code | HTTP status |
|---|---|---|
| `VALIDATION_FAILED` | `ERR_VALIDATION` | 400 |
| `BAD_REQUEST` | `ERR_BAD_REQUEST` | 400 |
| `UNAUTHORIZED` | `ERR_UNAUTHORIZED` | 401 |
| `FORBIDDEN` | `ERR_FORBIDDEN` | 403 |
| `RESOURCE_NOT_FOUND` | `ERR_RESOURCE_NOT_FOUND` | 404 |
| `METHOD_NOT_ALLOWED` | `ERR_METHOD_NOT_ALLOWED` | 405 |
| `CONFLICT` | `ERR_CONFLICT` | 409 |
| `INTERNAL_ERROR` | `ERR_INTERNAL` | 500 |
| `SERVICE_UNAVAILABLE` | `ERR_SERVICE_UNAVAILABLE` | 503 |

`BusinessErrorCode` — record `(String code, int httpStatus)` cho error catalog nghiệp vụ theo quy ước **`ERR-<DOMAIN>-<SEQ>`** (ví dụ `ERR-USER-001`, `ERR-ORDER-042`). Tạo bằng `BusinessErrorCode.of(String code, int httpStatus)`. Validation trong constructor (vi phạm ném `IllegalArgumentException`):

- `code` phải khớp regex `ERR(-[A-Z0-9]+)+` — bắt đầu bằng `ERR`, theo sau ít nhất một segment `-<CHỮ HOA/SỐ>`; chữ thường bị từ chối.
- `httpStatus` phải nằm trong khoảng **400–599** (chỉ 4xx/5xx).

### `ErrorResponse` (record, `javalibs-web-core`)

Body lỗi chuẩn: `(Instant timestamp, int status, String code, String message, String path, String traceId, List<FieldViolation> fieldErrors)`. `fieldErrors` được chuẩn hóa thành list immutable, không bao giờ `null`. Record lồng `FieldViolation(String field, String message)`.

Builder: `ErrorResponse.builder()` (timestamp mặc định `Instant.now()`) với các method `timestamp`, `status`, `code`, `errorCode(ErrorCode)` (đặt cả `status` lẫn `code`), `message`, `path`, `traceId`, `fieldErrors(List)`, `fieldError(field, message)`, `build()`.

**Định dạng JSON đúng theo record** (mọi lỗi đều trả về cấu trúc này):

```json
{
  "timestamp": "2026-07-14T08:30:00.123Z",
  "status": 400,
  "code": "ERR_VALIDATION",
  "message": "Validation failed",
  "path": "/api/users",
  "traceId": "6f1c2a9e-4b3d-4d1f-9a2b-1c0d8e7f6a5b",
  "fieldErrors": [
    { "field": "name", "message": "must not be blank" },
    { "field": "age", "message": "must be greater than or equal to 18" }
  ]
}
```

### Exceptions (`io.javalibs.web.exception`)

| Exception | Constructor | ErrorCode mặc định → HTTP |
|---|---|---|
| `ApiException` | `(ErrorCode, String message)`, `(ErrorCode, String message, Throwable cause)`; getter `getErrorCode()` | theo `ErrorCode` truyền vào |
| `ResourceNotFoundException` | `(String message)` hoặc `(String resource, Object id)` → message `"<resource> with id <id> not found"` | `RESOURCE_NOT_FOUND` → 404 |
| `BusinessException` | `(String message)`, `(String message, Throwable cause)`, `(ErrorCode, String message)` | `BAD_REQUEST` → 400 (hoặc `ErrorCode` tùy chọn) |
| `ConflictException` | `(String message)`, `(String message, Throwable cause)` | `CONFLICT` → 409 |

Tất cả kế thừa `ApiException extends RuntimeException`, được `GlobalExceptionHandler` dịch thành `ErrorResponse` với HTTP status lấy từ `ErrorCode`.

### `GlobalExceptionHandler` (`javalibs-web-spring`)

`@RestControllerAdvice` (không có stereotype scan — được đăng ký bởi autoconfigure hoặc thủ công). Bảng **đầy đủ** các exception được xử lý:

| Exception | HTTP status | `code` | `message` | `fieldErrors` |
|---|---|---|---|---|
| `ApiException` (và mọi subclass) | `errorCode.httpStatus()` | `errorCode.code()` | message của exception | rỗng |
| `BindException` (bao gồm `MethodArgumentNotValidException` — lỗi `@Valid` trên body) | 400 | `ERR_VALIDATION` | `Validation failed` | từng `FieldError` của binding result |
| `ConstraintViolationException` (validation trên method parameter) | 400 | `ERR_VALIDATION` | `Validation failed` | từng violation (`propertyPath` → field) |
| `HttpMessageNotReadableException` | 400 | `ERR_BAD_REQUEST` | `Malformed request body` | rỗng |
| `MethodArgumentTypeMismatchException` | 400 | `ERR_BAD_REQUEST` | `Parameter '<name>' has invalid value '<value>'` | rỗng |
| `NoResourceFoundException` (Spring 6.1+, path không tồn tại) | 404 | `ERR_RESOURCE_NOT_FOUND` | `Resource not found` | rỗng |
| `HttpRequestMethodNotSupportedException` | 405 | `ERR_METHOD_NOT_ALLOWED` | `Request method '<method>' is not supported` | rỗng |
| `Exception` (catch-all) | 500 | `ERR_INTERNAL` | `An unexpected error occurred` (log full stack trace ở ERROR, không lộ chi tiết ra client) | rỗng |

`traceId` trong response được lấy từ SLF4J MDC key `traceId`, fallback sang `correlationId`, `null` nếu không có (tự có khi dùng [javalibs-observability](observability.md)). `path` là `request.getRequestURI()`. `ApiException` được log ở WARN kèm code, method, URI.

### `RequestLoggingFilter` (`javalibs-web-spring`)

`OncePerRequestFilter` ghi **một dòng INFO mỗi request**: `<METHOD> <URI>?<query> status=<status> duration=<ms>ms`.

- Constructor: `RequestLoggingFilter()` (mặc định: không log payload, max 2048 ký tự, loại trừ `/actuator/**`) hoặc `RequestLoggingFilter(boolean includePayload, int maxPayloadLength, List<String> excludedPaths)`. Hằng public: `DEFAULT_MAX_PAYLOAD_LENGTH = 2048`, `DEFAULT_EXCLUDED_PATHS = ["/actuator/**"]`. `maxPayloadLength <= 0` rơi về 2048; `excludedPaths = null` rơi về mặc định.
- **Payload caching**: chỉ khi `includePayload=true`, request/response được bọc bằng `ContentCachingRequestWrapper`/`ContentCachingResponseWrapper`; sau khi log, body response được copy trả lại client (`copyBodyToResponse()`). Payload nối vào dòng log dạng `request=...` / `response=...`, cắt còn tối đa `maxPayloadLength` ký tự.
- **Giới hạn content-type**: chỉ log payload cho `application/json` và `text/*` (so khớp không phân biệt hoa thường); nội dung nhị phân không bao giờ được log. Charset lấy từ encoding của request/response, fallback UTF-8.
- **Excluded paths**: request khớp bất kỳ Ant pattern nào trong `excludedPaths` (so với `getRequestURI()`) bị bỏ qua hoàn toàn (`shouldNotFilter`).

### `CorsSupport` (`javalibs-web-spring`)

Helper tĩnh: `CorsConfiguration build(List<String> allowedOrigins, List<String> allowedMethods, List<String> allowedHeaders, List<String> exposedHeaders, boolean allowCredentials, long maxAgeSeconds)`. List `null`/rỗng thì không đụng tới setting tương ứng. Khi `allowCredentials=true`, origins được đăng ký dưới dạng **origin pattern** (`setAllowedOriginPatterns`) vì Spring từ chối wildcard `*` kết hợp credentials.

### Auto-configuration (`javalibs-web-spring-boot-autoconfigure`)

Ba auto-configuration (khai báo trong `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`), tất cả chỉ kích hoạt với servlet web app:

| Class | Bean | Điều kiện |
|---|---|---|
| `WebAutoConfiguration` | `javalibsGlobalExceptionHandler` (`GlobalExceptionHandler`) | `@ConditionalOnMissingBean` + `javalibs.web.exception-handler.enabled` (mặc định `true`) |
| `RequestLoggingAutoConfiguration` | `javalibsRequestLoggingFilter` (`FilterRegistrationBean<RequestLoggingFilter>`, order `Ordered.LOWEST_PRECEDENCE - 10` — chạy sát handler để status log là status cuối cùng) | `@ConditionalOnMissingBean(RequestLoggingFilter.class)` + `javalibs.web.logging.enabled` (mặc định `true`) |
| `CorsAutoConfiguration` | `javalibsCorsFilter` (`FilterRegistrationBean<CorsFilter>`, order `Ordered.HIGHEST_PRECEDENCE + 10` — chạy sớm để header CORS được áp trước) | `@ConditionalOnMissingBean(CorsFilter.class)` + **opt-in**: chỉ khi `javalibs.web.cors.enabled=true` |

## Cấu hình

Toàn bộ property bind vào record `WebProperties` (`javalibs.web.*`):

| Thuộc tính | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `javalibs.web.exception-handler.enabled` | boolean | `true` | Đăng ký `GlobalExceptionHandler` |
| `javalibs.web.logging.enabled` | boolean | `true` | Đăng ký `RequestLoggingFilter` |
| `javalibs.web.logging.include-payload` | boolean | `false` | Log body request/response (chỉ `application/json` và `text/*`) |
| `javalibs.web.logging.max-payload-length` | int | `2048` | Số ký tự payload tối đa được log (cắt bớt nếu dài hơn) |
| `javalibs.web.logging.excluded-paths` | List&lt;String&gt; | `/actuator/**` | Ant pattern các path không log |
| `javalibs.web.cors.enabled` | boolean | `false` | Bật CORS filter — **chỉ đăng ký khi đặt rõ `true`** |
| `javalibs.web.cors.allowed-origins` | List&lt;String&gt; | (trống) | Origin được phép; là origin *pattern* khi `allow-credentials=true` |
| `javalibs.web.cors.allowed-methods` | List&lt;String&gt; | `GET,POST,PUT,PATCH,DELETE,OPTIONS` | HTTP method được phép |
| `javalibs.web.cors.allowed-headers` | List&lt;String&gt; | `*` | Request header được phép |
| `javalibs.web.cors.exposed-headers` | List&lt;String&gt; | (trống) | Response header trình duyệt được đọc |
| `javalibs.web.cors.allow-credentials` | boolean | `false` | Cho phép cookie/credential cross-origin |
| `javalibs.web.cors.max-age` | long (giây) | `3600` | Thời gian cache preflight |
| `javalibs.web.cors.path` | String | `/**` | URL pattern áp dụng cấu hình CORS |

## Hướng dẫn sử dụng

### Dependency

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-web-spring-boot-starter</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Không cần cấu hình gì thêm: exception handler và request logging đã bật sẵn; CORS tắt mặc định.

### `application.yml` tùy biến

```yaml
javalibs:
  web:
    logging:
      include-payload: true        # log body (chỉ JSON và text/*)
      max-payload-length: 1024
      excluded-paths:
        - /actuator/**
        - /internal/**
    cors:
      enabled: true                # bắt buộc để filter được đăng ký
      path: /api/**
      allowed-origins:
        - https://app.example.com
      allow-credentials: true      # origins xử lý dạng pattern
      max-age: 1800
```

### Ví dụ hoàn chỉnh: error catalog + controller + JSON kết quả

Định nghĩa error catalog nghiệp vụ theo quy ước `ERR-<DOMAIN>-<SEQ>`:

```java
import io.javalibs.web.BusinessErrorCode;

public final class UserErrors {

    public static final BusinessErrorCode NOT_FOUND  = BusinessErrorCode.of("ERR-USER-001", 404);
    public static final BusinessErrorCode SUSPENDED  = BusinessErrorCode.of("ERR-USER-002", 403);
    public static final BusinessErrorCode DUPLICATED = BusinessErrorCode.of("ERR-USER-003", 409);

    private UserErrors() {
    }
}
```

Controller ném `ApiException` với mã trong catalog:

```java
import io.javalibs.web.ApiResponse;
import io.javalibs.web.PageResponse;
import io.javalibs.web.exception.ApiException;
import io.javalibs.web.exception.ResourceNotFoundException;

@RestController
@RequestMapping("/api/users")
class UserController {

    private final UserService userService;

    UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    ApiResponse<UserDto> get(@PathVariable long id) {
        UserDto user = userService.find(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id)); // 404 ERR_RESOURCE_NOT_FOUND

        if (user.suspended()) {
            throw new ApiException(UserErrors.SUSPENDED, "User account is suspended");
        }
        return ApiResponse.ok(user);
    }

    @GetMapping
    ApiResponse<PageResponse<UserDto>> list(@RequestParam int page, @RequestParam int size) {
        return ApiResponse.ok(userService.findPage(page, size));
    }
}
```

Khi user bị suspend, client gọi `GET /api/users/42` nhận HTTP **403** với body:

```json
{
  "timestamp": "2026-07-14T08:30:00.123Z",
  "status": 403,
  "code": "ERR-USER-002",
  "message": "User account is suspended",
  "path": "/api/users/42",
  "traceId": null,
  "fieldErrors": []
}
```

## Ghi đè & mở rộng

- **Thay `GlobalExceptionHandler`**: khai báo bean kiểu `GlobalExceptionHandler` (hoặc subclass) trong ứng dụng — bean autoconfigure là `@ConditionalOnMissingBean` nên tự nhường. Muốn thay bằng chuẩn khác hoàn toàn thì tắt `javalibs.web.exception-handler.enabled=false` rồi tự viết `@RestControllerAdvice`.
- **Thay `RequestLoggingFilter`**: khai báo bean `RequestLoggingFilter` riêng — autoconfigure back off (`@ConditionalOnMissingBean(RequestLoggingFilter.class)`).
- **Thay CORS**: khai báo bean `CorsFilter` riêng — autoconfigure back off (`@ConditionalOnMissingBean(CorsFilter.class)`); hoặc dựng `CorsConfiguration` từ `CorsSupport.build(...)` để tái dùng logic origin-pattern.
- **Error code riêng**: implement `ErrorCode` (enum hoặc dùng `BusinessErrorCode.of`) rồi ném qua `ApiException`/`BusinessException(errorCode, message)` — không cần đăng ký gì thêm.

## Testing

- Test `GlobalExceptionHandler` bằng MockMvc standalone, không cần Spring context (đúng cách module tự test trong `GlobalExceptionHandlerTest`):

```java
MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DummyController())
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
```

- `ApiResponse`/`PageResponse`/`BusinessErrorCode` là record thuần Java — unit test trực tiếp với JUnit/AssertJ (xem `ApiResponseTest`, `PageResponseTest`, `BusinessErrorCodeTest` trong `javalibs-web-core`).
- `RequestLoggingFilter` test được với `MockHttpServletRequest`/`MockHttpServletResponse` (xem `RequestLoggingFilterTest`).
- Nếu serialize `Instant`, nhớ đăng ký `JavaTimeModule` cho `ObjectMapper` trong test standalone (Boot app thực tế đã tự có).

## Lưu ý & bẫy thường gặp

- **CORS là opt-in**: khác với logging và exception handler (bật sẵn), CORS filter chỉ được đăng ký khi đặt rõ `javalibs.web.cors.enabled=true`.
- **`BusinessErrorCode` chỉ nhận chữ hoa**: `ERR-user-001` hay `ERR_USER_001` (gạch dưới) ném `IllegalArgumentException` ngay khi khởi tạo — hãy khởi tạo catalog dưới dạng hằng `static final` để lỗi lộ sớm khi load class.
- **Catch-all 500 giấu chi tiết**: message luôn là `"An unexpected error occurred"`; chi tiết chỉ nằm trong log ERROR. Đừng mong client nhận được message của exception không thuộc `ApiException`.
- **`include-payload` có chi phí**: bật payload logging khiến request/response bị buffer trong bộ nhớ (`ContentCachingRequestWrapper` giới hạn theo `max-payload-length`, response wrapper buffer toàn bộ body); cân nhắc với response lớn hoặc streaming.
- **Payload request chỉ đọc được sau khi body đã được consume**: `ContentCachingRequestWrapper` chỉ cache phần đã đọc — với request bị chặn trước khi vào controller (ví dụ 401 từ security filter chạy trước), phần `request=` có thể trống.
- **Handler không tự scan**: `GlobalExceptionHandler` không có `@Component` — nếu không dùng autoconfigure thì phải tự khai báo bean.
- **WebFlux không được hỗ trợ**: mọi auto-configuration đều yêu cầu servlet stack.
- `traceId` chỉ khác `null` khi MDC có key `traceId`/`correlationId` — kết hợp với [javalibs-observability](observability.md) để tự động có correlation ID. Lỗi 401/403 do security filter chain trả về trước khi tới MVC sẽ do [javalibs-security](security.md) định dạng, không đi qua `GlobalExceptionHandler`.
