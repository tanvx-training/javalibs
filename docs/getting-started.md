# Getting Started — Dựng microservice với javalibs

Hướng dẫn này dựng một service `orders-service` hoàn chỉnh: REST API chuẩn hóa, xác thực JWT, tìm kiếm động, tài liệu OpenAPI, migration DB và integration test — trong khoảng 15 phút.

## 0. Điều kiện tiên quyết

- JDK 21+, Maven 3.6.3+
- Docker (cho integration test với Testcontainers)
- javalibs đã được publish lên repository nội bộ, hoặc build local:

```bash
cd javalibs-root && mvn clean install
```

## 1. Tạo project và import BOM

Tạo Spring Boot project (qua start.spring.io hoặc archetype nội bộ) rồi khai báo trong `pom.xml`:

```xml
<dependencyManagement>
  <dependencies>
    <!-- BOM của Spring Boot (nếu không dùng spring-boot-starter-parent) -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-dependencies</artifactId>
      <version>3.5.3</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
    <!-- BOM của javalibs: từ đây mọi artifact javalibs không cần version -->
    <dependency>
      <groupId>io.javalibs</groupId>
      <artifactId>javalibs-dependencies</artifactId>
      <version>1.0.0-SNAPSHOT</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

## 2. Khai báo starter theo nhu cầu

```xml
<dependencies>
  <!-- REST API: exception handler chuẩn, PageResponse, request logging, CORS -->
  <dependency>
    <groupId>io.javalibs</groupId>
    <artifactId>javalibs-web-spring-boot-starter</artifactId>
  </dependency>

  <!-- Xác thực JWT stateless -->
  <dependency>
    <groupId>io.javalibs</groupId>
    <artifactId>javalibs-security-spring-boot-starter</artifactId>
  </dependency>

  <!-- Tìm kiếm động: HTTP params → JPA Specification -->
  <dependency>
    <groupId>io.javalibs</groupId>
    <artifactId>javalibs-search-spring-boot-starter</artifactId>
  </dependency>

  <!-- Swagger UI + mã lỗi toàn cục trong docs -->
  <dependency>
    <groupId>io.javalibs</groupId>
    <artifactId>javalibs-openapi-spring-boot-starter</artifactId>
  </dependency>

  <!-- Flyway với default an toàn -->
  <dependency>
    <groupId>io.javalibs</groupId>
    <artifactId>javalibs-persistence-spring-boot-starter</artifactId>
  </dependency>

  <!-- Correlation ID, metrics, tracing -->
  <dependency>
    <groupId>io.javalibs</groupId>
    <artifactId>javalibs-observability-spring-boot-starter</artifactId>
  </dependency>

  <!-- Driver DB của bạn -->
  <dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
  </dependency>

  <!-- Test support -->
  <dependency>
    <groupId>io.javalibs</groupId>
    <artifactId>javalibs-test</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>io.javalibs</groupId>
    <artifactId>javalibs-security-test</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
```

Không cần `@Import` hay `@ComponentScan` gì thêm — Spring Boot tự quét file `AutoConfiguration.imports` trong từng starter.

## 3. Cấu hình `application.yml`

```yaml
spring:
  application:
    name: orders-service
  datasource:
    url: jdbc:postgresql://localhost:5432/orders
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate   # schema do Flyway quản lý

javalibs:
  security:
    jwt:
      secret: ${JWT_SECRET}   # HMAC >= 32 bytes; hoặc dùng public-key (RSA PEM)
  web:
    cors:
      enabled: true
      allowed-origins: ["https://app.example.com"]
  openapi:
    title: Orders API
    contact:
      name: Orders Team
      email: orders@example.com

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
```

Tra cứu đầy đủ mọi thuộc tính tại [Tham chiếu cấu hình](configuration-reference.md).

## 4. Migration đầu tiên

`src/main/resources/db/migration/V202607141000__create_orders.sql`:

```sql
CREATE TABLE orders (
    id          UUID PRIMARY KEY,
    status      VARCHAR(20)    NOT NULL,
    customer_id UUID           NOT NULL,
    total       NUMERIC(19, 2) NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL
);
```

Quy ước tên file: `V<yyyyMMddHHmm>__<mô_tả>.sql` — xem [persistence.md](modules/persistence.md).

## 5. Viết API đầu tiên

```java
@RestController
@RequestMapping("/api/orders")
class OrderController {

    private final OrderRepository repository;

    OrderController(OrderRepository repository) {
        this.repository = repository;
    }

    /** GET /api/orders?filter=status:eq:OPEN&filter=total:gte:100&sort=createdAt,desc&page=0&size=20 */
    @GetMapping
    ApiResponse<PageResponse<OrderDto>> search(SearchQuery query) {
        Page<Order> page = repository.findAll(
                SpecificationBuilder.toSpecification(query), PageRequests.of(query));
        return ApiResponse.ok(PageResponse.of(
                page.getContent().stream().map(OrderDto::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements()));
    }

    @GetMapping("/{id}")
    ApiResponse<OrderDto> get(@PathVariable UUID id) {
        // Mọi 2xx body đều bọc envelope ApiResponse (xem modules/web.md); lỗi thì KHÔNG bọc.
        return repository.findById(id)
                .map(OrderDto::from)
                .map(ApiResponse::ok)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        // → tự động thành JSON 404 chuẩn: {"status":404,"code":"ERR_RESOURCE_NOT_FOUND",...}
    }

    @DeleteMapping("/{id}")
    @RequireRole("ADMIN")                      // 403 nếu thiếu role
    void delete(@PathVariable UUID id, @CurrentUser UserContext user) {
        // user.userId(), user.roles()... lấy từ JWT
        repository.deleteById(id);
    }
}
```

`OrderRepository` chỉ cần `extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order>`.

## 6. Chạy thử

```bash
mvn spring-boot:run
```

- Swagger UI: `http://localhost:8080/swagger-ui.html` (đã nằm trong permit-all mặc định của security)
- Gọi API cần token: `curl -H "Authorization: Bearer <token>" http://localhost:8080/api/orders`
- Không token → 401 JSON chuẩn; sai role → 403 JSON chuẩn
- Mỗi response có header `X-Correlation-Id`; log có `%X{correlationId}`

## 7. Integration test đầu tiên

```java
class OrderApiIT extends BaseIntegrationTest {   // tự dựng PostgreSQL bằng Testcontainers

    @Autowired
    TestRestTemplate rest;

    @Test
    void searchRequiresAuthentication() {
        var response = rest.getForEntity("/api/orders", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void adminCanDelete() {
        String bearer = JwtTestFactory.create()
                .subject("admin-1")
                .roles("ADMIN")
                .secret(/* trùng javalibs.security.jwt.secret của profile test */)
                .bearer();

        var headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, bearer);
        var response = rest.exchange("/api/orders/" + id, HttpMethod.DELETE,
                new HttpEntity<>(headers), Void.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
```

(Đặt `javalibs.security.jwt.secret` trong `application-test.yml` trùng với secret dùng trong `JwtTestFactory` — xem [security.md](modules/security.md) và [test.md](modules/test.md).)

## Bước tiếp theo

- Tổ chức thư mục/package cho service theo chuẩn → [Cấu trúc dự án](project-structure.md)
- Bắn event Kafka an toàn với Transactional Outbox → [Cookbook § Outbox](cookbook.md#3-bắn-event-an-toàn-với-transactional-outbox)
- Định nghĩa bộ mã lỗi nghiệp vụ `ERR-ORDER-001` → [Cookbook § Error catalog](cookbook.md#7-định-nghĩa-bộ-mã-lỗi-nghiệp-vụ)
- Cache, circuit breaker, revoke token → [Cookbook](cookbook.md)
