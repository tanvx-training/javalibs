# javalibs-web-spring-boot-starter

Starter Spring Boot cho tầng web chuẩn hóa của javalibs. Module này **không chứa code** — chỉ khai báo dependency để kéo về:

- `javalibs-web-core` — DTO/exception thuần Java (`ApiResponse`, `PageResponse`, `ErrorResponse`, `ApiException`, ...).
- `javalibs-web-spring` — `GlobalExceptionHandler`, `RequestLoggingFilter`, `CorsSupport`.
- `javalibs-web-spring-boot-autoconfigure` — auto-configuration theo namespace `javalibs.web.*`.
- `spring-boot-starter-web` và `spring-boot-starter-validation`.

## Cài đặt

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-web-spring-boot-starter</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Chỉ cần thêm dependency là có ngay:

- Xử lý exception toàn cục trả về `ErrorResponse` JSON chuẩn (bật sẵn).
- Ghi log request: một dòng INFO mỗi request gồm method, URI, status, thời gian xử lý (bật sẵn, bỏ qua `/actuator/**`).
- CORS filter cấu hình bằng property (tắt mặc định, phải bật rõ ràng).

## Ví dụ `application.yml`

### Cấu hình mặc định (không cần khai báo gì)

```yaml
# Không cần cấu hình — exception handler và request logging đã bật sẵn.
```

### Tùy biến ghi log request

```yaml
javalibs:
  web:
    logging:
      enabled: true
      include-payload: true        # log body request/response (chỉ JSON và text/*)
      max-payload-length: 1024     # cắt payload còn tối đa 1024 ký tự
      excluded-paths:
        - /actuator/**
        - /internal/**
```

### Bật CORS

```yaml
javalibs:
  web:
    cors:
      enabled: true                # bắt buộc đặt true thì filter mới được đăng ký
      path: /api/**
      allowed-origins:
        - https://app.example.com
        - https://admin.example.com
      allowed-methods: GET,POST,PUT,PATCH,DELETE,OPTIONS
      allowed-headers: "*"
      exposed-headers:
        - X-Total-Count
      allow-credentials: true      # khi true, origin được xử lý dưới dạng pattern
      max-age: 1800                # giây
```

### Tắt các thành phần

```yaml
javalibs:
  web:
    exception-handler:
      enabled: false               # tự xử lý exception trong ứng dụng
    logging:
      enabled: false               # tắt log request
```

Lưu ý: nếu ứng dụng tự khai báo bean `GlobalExceptionHandler`, auto-configuration sẽ tự động nhường chỗ (không cần tắt bằng property).

## Ví dụ controller

```java
@RestController
@RequestMapping("/api/orders")
class OrderController {

    @GetMapping("/{id}")
    ApiResponse<OrderDto> get(@PathVariable long id) {
        OrderDto order = orderService.find(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        return ApiResponse.ok(order);
    }

    @GetMapping
    ApiResponse<PageResponse<OrderDto>> list(@RequestParam int page, @RequestParam int size) {
        return ApiResponse.ok(orderService.findPage(page, size));
    }
}
```

Khi có lỗi, client luôn nhận được JSON `ErrorResponse` chuẩn — xem chi tiết định dạng và bảng property đầy đủ trong `javalibs-web/README.md`.
