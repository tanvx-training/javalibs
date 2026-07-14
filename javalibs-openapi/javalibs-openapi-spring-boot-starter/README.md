# javalibs-openapi-spring-boot-starter

Chuẩn hóa tài liệu API (Swagger/OpenAPI) cho mọi service: thông tin API từ cấu hình, security scheme `Authorization: Bearer` dùng chung, và **bộ mã lỗi toàn cục** (400/401/403/404/409/500 theo đúng format `ErrorResponse` của `javalibs-web`) tự động xuất hiện trên mọi endpoint — frontend chỉ cần đọc một chỗ.

## Cài đặt

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-openapi-spring-boot-starter</artifactId>
</dependency>
```

Kèm sẵn `springdoc-openapi-starter-webmvc-ui` → Swagger UI tại `/swagger-ui.html`, spec tại `/v3/api-docs`. (Hai path này đã nằm trong `permit-all` mặc định của `javalibs-security`.)

## Cấu hình

```yaml
javalibs:
  openapi:
    title: Orders API            # mặc định: "<spring.application.name> API"
    version: v1
    description: Quản lý vòng đời đơn hàng
    servers:
      - https://api.example.com  # URL gateway
    contact:
      name: Orders Team
      email: orders@example.com
    security:
      enabled: true              # nút Authorize (bearer JWT) trong Swagger UI
    global-responses:
      enabled: true              # inject 400/401/403/404/409/500 vào mọi operation
```

Bean `OpenAPI` là `@ConditionalOnMissingBean` — service cần tùy biến sâu chỉ việc tự khai báo bean của mình.
