# javalibs-web

Tầng HTTP/REST chuẩn hóa cho các dịch vụ xây dựng trên javalibs: khuôn dạng response thống nhất, hợp đồng lỗi (error contract), xử lý exception toàn cục, ghi log request và hỗ trợ CORS.

## Mục đích

- Chuẩn hóa **định dạng response** (`ApiResponse`, `PageResponse`) và **định dạng lỗi** (`ErrorResponse`) cho mọi service.
- Cung cấp **bộ exception chuẩn** (`ApiException`, `ResourceNotFoundException`, `BusinessException`, `ConflictException`) gắn với `ErrorCode` và HTTP status tương ứng.
- Tự động đăng ký **GlobalExceptionHandler**, **RequestLoggingFilter** và **CorsFilter** thông qua Spring Boot auto-configuration, cấu hình bằng namespace `javalibs.web.*`.

## Kiến trúc module con

| Module | Mô tả |
|---|---|
| `javalibs-web-core` | Java thuần, **không phụ thuộc Spring**. Chứa DTO (`ApiResponse`, `PageResponse`, `ErrorResponse`), `ErrorCode`/`CommonErrorCode` và các exception chuẩn. Package `io.javalibs.web`. |
| `javalibs-web-spring` | Tích hợp Spring MVC: `GlobalExceptionHandler` (`@RestControllerAdvice`), `RequestLoggingFilter` (`OncePerRequestFilter`), `CorsSupport`. Package `io.javalibs.web.spring`. |
| `javalibs-web-spring-boot-autoconfigure` | Auto-configuration cho Spring Boot: `WebAutoConfiguration`, `RequestLoggingAutoConfiguration`, `CorsAutoConfiguration` cùng `WebProperties` (`javalibs.web.*`). Package `io.javalibs.web.autoconfigure`. |
| `javalibs-web-spring-boot-starter` | Starter không chứa code: kéo về core + spring + autoconfigure cùng `spring-boot-starter-web` và `spring-boot-starter-validation`. |

Ứng dụng thông thường chỉ cần khai báo starter:

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-web-spring-boot-starter</artifactId>
</dependency>
```

## Bảng thuộc tính cấu hình (`javalibs.web.*`)

| Thuộc tính | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `javalibs.web.exception-handler.enabled` | boolean | `true` | Bật/tắt đăng ký `GlobalExceptionHandler`. Tự động nhường chỗ (back off) nếu ứng dụng khai báo bean `GlobalExceptionHandler` riêng. |
| `javalibs.web.logging.enabled` | boolean | `true` | Bật/tắt filter ghi log request (mỗi request một dòng INFO: method, URI, status, thời gian xử lý). |
| `javalibs.web.logging.include-payload` | boolean | `false` | Ghi kèm body của request/response (chỉ với `application/json` và `text/*`, không bao giờ log nội dung nhị phân). |
| `javalibs.web.logging.max-payload-length` | int | `2048` | Số ký tự tối đa của payload được ghi log (cắt bớt nếu dài hơn). |
| `javalibs.web.logging.excluded-paths` | List&lt;String&gt; | `/actuator/**` | Danh sách Ant pattern các đường dẫn không ghi log. |
| `javalibs.web.cors.enabled` | boolean | `false` | Bật CORS filter. **Chỉ đăng ký khi đặt rõ `true`.** |
| `javalibs.web.cors.allowed-origins` | List&lt;String&gt; | (trống) | Danh sách origin được phép. Khi `allow-credentials=true`, các giá trị được đăng ký dưới dạng origin *pattern*. |
| `javalibs.web.cors.allowed-methods` | List&lt;String&gt; | `GET,POST,PUT,PATCH,DELETE,OPTIONS` | Các HTTP method được phép. |
| `javalibs.web.cors.allowed-headers` | List&lt;String&gt; | `*` | Các request header được phép. |
| `javalibs.web.cors.exposed-headers` | List&lt;String&gt; | (trống) | Các response header trình duyệt được phép đọc. |
| `javalibs.web.cors.allow-credentials` | boolean | `false` | Cho phép gửi cookie/credential trong request cross-origin. |
| `javalibs.web.cors.max-age` | long (giây) | `3600` | Thời gian cache kết quả preflight. |
| `javalibs.web.cors.path` | String | `/**` | URL pattern mà cấu hình CORS áp dụng. |

## Định dạng lỗi JSON chuẩn

Mọi lỗi đều được trả về theo cùng một cấu trúc `ErrorResponse`:

```json
{
  "timestamp": "2026-07-13T08:30:00.123Z",
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

- `code`: mã lỗi ổn định cho máy đọc (xem `CommonErrorCode`: `ERR_VALIDATION`, `ERR_BAD_REQUEST`, `ERR_UNAUTHORIZED`, `ERR_FORBIDDEN`, `ERR_RESOURCE_NOT_FOUND`, `ERR_METHOD_NOT_ALLOWED`, `ERR_CONFLICT`, `ERR_INTERNAL`, `ERR_SERVICE_UNAVAILABLE`).
- `traceId`: lấy từ MDC key `traceId` (fallback `correlationId`), `null` nếu không có.
- `fieldErrors`: chỉ có phần tử khi lỗi validation; luôn là mảng (có thể rỗng).
- Với lỗi không lường trước (500), message luôn là `"An unexpected error occurred"` — chi tiết exception chỉ nằm trong log, không bao giờ trả về client.

## Ví dụ sử dụng exception

```java
// 404 — "Order with id 42 not found"
throw new ResourceNotFoundException("Order", 42);

// 400 — vi phạm nghiệp vụ
throw new BusinessException("So du khong du de thanh toan");

// 409 — xung đột trạng thái
throw new ConflictException("Ma don hang da ton tai");

// Tùy biến với ErrorCode riêng của ứng dụng
throw new ApiException(CommonErrorCode.SERVICE_UNAVAILABLE, "He thong dang bao tri");
```

Xem thêm ví dụ cấu hình `application.yml` trong README của `javalibs-web-spring-boot-starter`.
