# javalibs-openapi

> Chuẩn hóa tài liệu Swagger/OpenAPI cho mọi service: thông tin API từ cấu hình `javalibs.openapi.*`, security scheme bearer-JWT dùng chung, và bộ error response toàn cục (400/401/403/404/409/500 theo đúng `ErrorResponse` của [javalibs-web](web.md)) tự động xuất hiện trên mọi operation.

## Artifacts

| artifactId | Packaging | Mô tả |
|---|---|---|
| `javalibs-openapi` | pom | Parent gộp 2 submodule |
| `javalibs-openapi-spring-boot-autoconfigure` | jar | `JavalibsOpenApiAutoConfiguration`, `GlobalErrorResponsesCustomizer`, `OpenApiProperties`. Phụ thuộc `springdoc-openapi-starter-webmvc-api` ở scope `optional`. Package `io.javalibs.openapi.autoconfigure` |
| `javalibs-openapi-spring-boot-starter` | jar | Starter không chứa code: autoconfigure + `springdoc-openapi-starter-webmvc-ui` (kèm Swagger UI) |

## Khi nào dùng / không dùng

**Dùng khi:**

- Service Spring MVC muốn có Swagger UI + spec OpenAPI với info/version/contact/servers thống nhất qua property thay vì code từng service.
- Muốn frontend đọc **một chỗ duy nhất** biết chính xác định dạng lỗi (kể cả mã nghiệp vụ `ERR-<DOMAIN>-<SEQ>`) mà không phải tự viết `@ApiResponse` cho từng endpoint.

**Không dùng khi:**

- Ứng dụng WebFlux — auto-configuration yêu cầu servlet web app và springdoc webmvc.
- Không muốn expose tài liệu API (ví dụ service nội bộ thuần) — đơn giản là không thêm starter, hoặc tắt bằng `javalibs.openapi.enabled=false`.

## Các thành phần chính

### `JavalibsOpenApiAutoConfiguration`

Điều kiện kích hoạt: có class `io.swagger.v3.oas.models.OpenAPI` trên classpath, servlet web app, và `javalibs.openapi.enabled` (mặc định `true`).

Bean `javalibsOpenApi` (`OpenAPI`, `@ConditionalOnMissingBean`) build tài liệu gốc từ `OpenApiProperties` + `Environment`:

- **Title**: `javalibs.openapi.title` nếu có; nếu không → **`"<spring.application.name> API"`** (khi `spring.application.name` cũng vắng mặt thì dùng `"service API"`).
- **Version** (`v1` mặc định) và **description** từ property.
- **Contact** chỉ được thêm khi có ít nhất `contact.name` hoặc `contact.email` (URL đi kèm nếu có).
- **Servers**: mỗi URL trong `javalibs.openapi.servers` thành một entry `Server`.
- **Security scheme**: khi `javalibs.openapi.security.enabled=true` (mặc định), đăng ký scheme HTTP `bearer` với `bearerFormat: JWT` dưới tên `security.scheme-name` (mặc định `bearerAuth`) và thêm `SecurityRequirement` toàn cục → Swagger UI hiện nút **Authorize**, client biết phải gửi `Authorization: Bearer <token>`.

Inner configuration `GlobalResponsesConfiguration`: đăng ký bean `javalibsGlobalErrorResponsesCustomizer` (`@ConditionalOnMissingBean`) khi có class `org.springdoc.core.customizers.GlobalOpenApiCustomizer` trên classpath và `javalibs.openapi.global-responses.enabled` (mặc định `true`).

### `GlobalErrorResponsesCustomizer`

Implement `GlobalOpenApiCustomizer` của springdoc. Hằng public: `ERROR_SCHEMA_NAME = "ErrorResponse"`. Hành vi trong `customise(OpenAPI)`:

1. Đăng ký schema dùng chung `ErrorResponse` vào `components.schemas` — **mirror đúng record `io.javalibs.web.ErrorResponse`**: `timestamp` (string date-time), `status` (integer), `code` (string, mô tả "Machine-readable error code (ERR_* or business ERR-<DOMAIN>-<SEQ>)"), `message`, `path`, `traceId` (nullable), `fieldErrors` (mảng `{field, message}`, nullable).
2. Với **mọi operation** trong `paths`, inject các response sau (body `application/json` tham chiếu `#/components/schemas/ErrorResponse`) — đúng theo map `GLOBAL_RESPONSES` trong source:

| Status | Mô tả |
|---|---|
| `400` | Bad request — validation failed or malformed input |
| `401` | Unauthorized — missing, invalid, expired or revoked token |
| `403` | Forbidden — authenticated but lacking the required role |
| `404` | Not found — resource does not exist |
| `409` | Conflict — state conflict (duplicate, concurrent update) |
| `500` | Internal server error — unexpected failure, safe generic message |

3. **Response đã khai báo sẵn trên operation không bị đụng tới** — chỉ thêm status code chưa tồn tại. Ví dụ endpoint tự khai `@ApiResponse(responseCode = "404", ...)` sẽ giữ nguyên bản của mình.

### `OpenApiProperties`

Class properties bind `javalibs.openapi.*` với 3 nested class `Contact`, `Security`, `GlobalResponses` — xem bảng Cấu hình bên dưới.

### Đăng ký auto-configuration

`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` chứa duy nhất `io.javalibs.openapi.autoconfigure.JavalibsOpenApiAutoConfiguration`.

## Cấu hình

| Thuộc tính | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `javalibs.openapi.enabled` | boolean | `true` | Công tắc tổng của auto-configuration |
| `javalibs.openapi.title` | String | *(null — rơi về `"<spring.application.name> API"`, hoặc `"service API"` nếu không đặt tên app)* | Tiêu đề API |
| `javalibs.openapi.description` | String | *(null)* | Mô tả hiển thị trên trang tài liệu |
| `javalibs.openapi.version` | String | `v1` | Nhãn version của API |
| `javalibs.openapi.servers` | List&lt;String&gt; | `[]` (rỗng) | Danh sách base URL trong tài liệu (ví dụ URL gateway) |
| `javalibs.openapi.contact.name` | String | *(null)* | Tên team sở hữu |
| `javalibs.openapi.contact.email` | String | *(null)* | Email liên hệ |
| `javalibs.openapi.contact.url` | String | *(null)* | URL trang team / runbook |
| `javalibs.openapi.security.enabled` | boolean | `true` | Khai báo scheme bearer-JWT và yêu cầu nó toàn cục |
| `javalibs.openapi.security.scheme-name` | String | `bearerAuth` | Tên security scheme trong `components` |
| `javalibs.openapi.global-responses.enabled` | boolean | `true` | Inject 400/401/403/404/409/500 vào mọi operation |

## Hướng dẫn sử dụng

### Dependency

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-openapi-spring-boot-starter</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Starter kéo sẵn `springdoc-openapi-starter-webmvc-ui`, cho ngay:

- **Swagger UI** tại `/swagger-ui.html`
- **Spec OpenAPI** tại `/v3/api-docs` (JSON; springdoc còn phục vụ `/v3/api-docs/**` cho YAML/nhóm)

Hai path này (cùng `/swagger-ui/**`, `/v3/api-docs/**`) **đã nằm trong danh sách `permit-all` mặc định của [javalibs-security](security.md)** (`SecurityProperties.permitAll`) — dùng chung hai starter thì tài liệu truy cập được mà không cần token, không phải cấu hình thêm.

### `application.yml`

```yaml
spring:
  application:
    name: orders-service         # → title mặc định "orders-service API"

javalibs:
  openapi:
    title: Orders API            # ghi đè title mặc định
    version: v1
    description: Quản lý vòng đời đơn hàng
    servers:
      - https://api.example.com  # URL gateway
    contact:
      name: Orders Team
      email: orders@example.com
    security:
      enabled: true              # nút Authorize (bearer JWT) trong Swagger UI
      scheme-name: bearerAuth
    global-responses:
      enabled: true              # inject 400/401/403/404/409/500 vào mọi operation
```

### Controller — không cần annotation lỗi

```java
@RestController
@RequestMapping("/api/orders")
class OrderController {

    // Không cần @ApiResponse cho 400/401/403/404/409/500 — đã có toàn cục.
    // Chỉ khai báo khi muốn mô tả KHÁC bản mặc định:
    @Operation(summary = "Lấy đơn hàng theo id")
    @ApiResponse(responseCode = "404", description = "Đơn hàng không tồn tại hoặc đã bị ẩn")
    @GetMapping("/{id}")
    ApiResponse<OrderDto> get(@PathVariable long id) { ... }
}
```

Trong spec sinh ra, operation trên có `404` với mô tả riêng (được giữ nguyên) và `400/401/403/409/500` từ bộ toàn cục, tất cả cùng schema `#/components/schemas/ErrorResponse`.

## Ghi đè & mở rộng

- **Ghi đè bean `OpenAPI`**: bean `javalibsOpenApi` là `@ConditionalOnMissingBean` — service cần tùy biến sâu (tag, license, extension...) chỉ việc khai báo bean `OpenAPI` của riêng mình, auto-configuration nhường hoàn toàn:

```java
@Configuration
class OpenApiConfig {
    @Bean
    OpenAPI customOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Orders API").version("v2")
                        .license(new License().name("Internal")));
        // Lưu ý: tự lo security scheme nếu vẫn cần nút Authorize.
    }
}
```

- **Ghi đè customizer lỗi toàn cục**: bean `GlobalErrorResponsesCustomizer` cũng là `@ConditionalOnMissingBean` — khai báo bean cùng kiểu để thay logic; hoặc tắt hẳn bằng `javalibs.openapi.global-responses.enabled=false`.
- **Thêm customizer khác**: đăng ký thêm bean `GlobalOpenApiCustomizer`/`OpenApiCustomizer` của springdoc — chạy song song, không xung đột với javalibs.
- **Tắt yêu cầu bearer toàn cục** (API public): `javalibs.openapi.security.enabled=false` — spec sẽ không có `securitySchemes` lẫn `security` requirement.
- **Chỉ cần spec, không cần UI**: phụ thuộc trực tiếp `javalibs-openapi-spring-boot-autoconfigure` + `springdoc-openapi-starter-webmvc-api` thay vì starter.

## Testing

- Auto-configuration test bằng `WebApplicationContextRunner` (đúng cách module tự test trong `JavalibsOpenApiAutoConfigurationTest`):

```java
new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JavalibsOpenApiAutoConfiguration.class))
        .withPropertyValues("spring.application.name=orders-service")
        .run(context -> {
            OpenAPI openApi = context.getBean(OpenAPI.class);
            assertThat(openApi.getInfo().getTitle()).isEqualTo("orders-service API");
        });
```

- `GlobalErrorResponsesCustomizer` test thuần không cần context: dựng `OpenAPI` với `Paths`/`Operation` thủ công, gọi `customise(openApi)`, assert các key `400/401/403/404/409/500` và schema `ErrorResponse` trong components.
- Test tích hợp end-to-end: `@SpringBootTest(webEnvironment = RANDOM_PORT)` + gọi `GET /v3/api-docs` và assert JSON.

## Lưu ý & bẫy thường gặp

- **Bộ status toàn cục là cố định trong code** (`400/401/403/404/409/500`) — không có property để thêm/bớt status (ví dụ 422); muốn khác phải ghi đè bean `GlobalErrorResponsesCustomizer`.
- **Ghi đè bean `OpenAPI` là thay thế toàn bộ**: title mặc định, contact, servers và security scheme từ property sẽ không còn được áp — không phải "merge".
- **Customizer cần springdoc**: `GlobalErrorResponsesCustomizer` chỉ được đăng ký khi có `GlobalOpenApiCustomizer` trên classpath; autoconfigure khai báo springdoc là `optional`, nên nếu không dùng starter thì phải tự thêm `springdoc-openapi-starter-webmvc-api` (hoặc `-ui`).
- **Schema lỗi phải khớp thực tế**: schema `ErrorResponse` trong spec chỉ đúng khi service thực sự dùng `GlobalExceptionHandler` của [javalibs-web](web.md) (và entry point/denied handler của [javalibs-security](security.md) cho 401/403). Tắt exception handler mà vẫn bật global responses sẽ khiến tài liệu "nói dối".
- **Đừng quên `spring.application.name`**: không đặt thì title mặc định là `"service API"` — xấu trong portal tài liệu.
- **Môi trường production**: nếu không muốn lộ tài liệu, tắt bằng `javalibs.openapi.enabled=false` **và** tắt springdoc (`springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false`) — property javalibs chỉ gỡ bean `OpenAPI`/customizer của javalibs, endpoint của springdoc là do springdoc quản.
- Hai path tài liệu nằm trong permit-all mặc định của [javalibs-security](security.md); nếu bạn **ghi đè** `javalibs.security.permit-all` thì nhớ thêm lại `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**` (ghi đè property là thay thế cả list, không phải nối thêm).
