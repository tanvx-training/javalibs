# Cấu trúc dự án mới sử dụng javalibs

Tài liệu này trả lời câu hỏi: **"Tôi tạo một microservice mới — nên tổ chức thư mục, package và phân lớp thế nào để tận dụng tối đa javalibs?"**

> Phần khai báo pom/starter/yaml chi tiết xem [Getting Started](getting-started.md). Tài liệu này tập trung vào **bố cục và phân lớp code**.

## Nguyên tắc xuất phát

1. **Một service = MỘT Maven module.** Kiến trúc 4 tầng `core → spring → autoconfigure → starter` là kiến trúc *của thư viện* (để client chọn lọc dependency) — **đừng** bắt chước nó trong service. Service không có ai "tiêu thụ lại" nên tách module chỉ tạo ma sát.
2. **Phân lớp bằng package + ép chiều phụ thuộc**, không phân lớp bằng Maven module.
3. **javalibs lo phần khung** (security, chuẩn lỗi, logging, correlation, outbox, cache...) — code trong service chỉ còn lại nghiệp vụ. Nếu bạn đang viết một `GlobalExceptionHandler`, một JWT filter hay một bảng outbox thủ công, hãy dừng lại và kiểm tra lại docs.
4. **Không tạo "common/util" package vội.** Tiện ích chung đã có `javalibs-internal` (Strings, Checks, Dates, Ids); thứ thực sự dùng chung nhiều service thì đề xuất thêm vào javalibs ([contributing.md](contributing.md)).

## Chọn một trong hai mức cấu trúc

| | **Mức A — DDD + CQRS** | **Mức B — Layered gọn** |
|---|---|---|
| Dùng khi | Nghiệp vụ phức tạp, nhiều bất biến (invariant), nhiều aggregate, phát domain event | CRUD, BFF, adapter service, service < ~10 endpoint |
| Starter đặc trưng | `javalibs-cqrs-spring-boot-starter`, `javalibs-ddd-spring` | không cần ddd/cqrs |
| Chi phí | Nhiều file hơn, học DDD | Thấp |

Cả hai mức dùng **cùng một bộ starter hạ tầng** (web, security, openapi, persistence, observability, test...). Bắt đầu bằng mức B và nâng cấp lên mức A khi nghiệp vụ dày lên là hoàn toàn bình thường — các starter không đổi.

---

## Mức A — Cấu trúc DDD + CQRS (khuyến nghị cho service nghiệp vụ)

### Cây thư mục đầy đủ

```
orders-service/
├── pom.xml
├── README.md
├── ERROR-CODES.md                          # catalog mã lỗi ERR-ORDER-xxx của service
├── Dockerfile
└── src
    ├── main
    │   ├── java/com/company/orders
    │   │   ├── OrdersServiceApplication.java
    │   │   │
    │   │   ├── api/                        # ── Tầng trình diện (REST)
    │   │   │   ├── OrderController.java
    │   │   │   ├── dto/                    #    request/response record + mapper from()
    │   │   │   │   ├── PlaceOrderRequest.java
    │   │   │   │   └── OrderResponse.java
    │   │   │   └── OrderErrors.java        #    hằng số BusinessErrorCode
    │   │   │
    │   │   ├── application/                # ── Tầng use case (CQRS)
    │   │   │   ├── command/
    │   │   │   │   ├── PlaceOrderCommand.java
    │   │   │   │   └── PlaceOrderHandler.java
    │   │   │   ├── query/
    │   │   │   │   ├── GetOrderQuery.java
    │   │   │   │   └── GetOrderHandler.java
    │   │   │   └── event/                  #    lắng nghe event từ service khác
    │   │   │       └── PaymentEventsListener.java
    │   │   │
    │   │   ├── domain/                     # ── Tầng nghiệp vụ THUẦN JAVA
    │   │   │   ├── model/
    │   │   │   │   ├── Order.java          #    AggregateRoot
    │   │   │   │   ├── OrderLine.java      #    Entity con
    │   │   │   │   ├── OrderStatus.java    #    enum
    │   │   │   │   └── Money.java          #    ValueObject
    │   │   │   ├── event/
    │   │   │   │   └── OrderPlacedEvent.java
    │   │   │   ├── rule/
    │   │   │   │   └── OrderTotalMustBePositive.java
    │   │   │   └── OrderRepository.java    #    PORT — interface, không biết JPA
    │   │   │
    │   │   ├── infrastructure/             # ── Tầng kỹ thuật (adapter)
    │   │   │   ├── persistence/
    │   │   │   │   └── JpaOrderRepository.java
    │   │   │   ├── messaging/              #    Kafka listener vật lý, outbox wiring
    │   │   │   └── client/                 #    RestClient sang service khác
    │   │   │       └── PaymentClient.java
    │   │   │
    │   │   └── config/                     # ── Ghi đè bean javalibs (khi cần)
    │   │       └── SecurityOverrides.java  #    ví dụ: thêm permit-all path
    │   │
    │   └── resources
    │       ├── application.yml
    │       ├── application-local.yml
    │       └── db/migration/
    │           ├── V202607150900__create_orders.sql
    │           └── V202607150901__datahub_outbox.sql   # copy từ jar datahub-spring
    │
    └── test
        ├── java/com/company/orders
        │   ├── domain/model/OrderTest.java          # unit thuần, 0 Spring, chạy ms
        │   ├── application/PlaceOrderHandlerTest.java  # Mockito
        │   └── api/OrderApiIT.java                  # extends BaseIntegrationTest
        └── resources/application-test.yml
```

### Chiều phụ thuộc giữa các package

```
api ──────►  application  ──────►  domain
                  ▲                   ▲
                  └── infrastructure ─┘   (implement port của domain)
```

- `domain` **không import gì** ngoài JDK + `javalibs-ddd-core` + `javalibs-internal`. Không Spring, không JPA*, không Jackson. Nhờ vậy unit test domain chạy bằng mili-giây.
- `application` gọi domain + port; đánh dấu transaction; **không** biết HTTP.
- `api` chỉ chuyển đổi HTTP ⇄ command/query; **không** chứa nghiệp vụ.
- `infrastructure` implement các port; là nơi duy nhất biết JPA/Kafka/Redis.
- **Không package nào import `api`.**

\* Cho phép ngoại lệ thực dụng: gắn annotation JPA trực tiếp lên aggregate (xem ghi chú bên dưới).

### javalibs artifact nào được dùng ở lớp nào

| Lớp | Được dùng | Ví dụ |
|---|---|---|
| `domain` | `javalibs-ddd-core`, `javalibs-internal` | `AggregateRoot`, `ValueObject`, `BusinessRule`, `Rules.check`, `Checks.notNull` |
| `application` | `javalibs-cqrs-core`, `javalibs-ddd-spring`, `javalibs-datahub-core` | `Command`/`CommandHandler`, `@ApplicationService`, `TransactionalOutbox`, `EventEnvelope` |
| `api` | `javalibs-web`, `javalibs-search`, `javalibs-security` | `PageResponse`, `ApiException`, `BusinessErrorCode`, `SearchQuery`, `@RequireRole`, `@CurrentUser` |
| `infrastructure` | `javalibs-datahub-spring`, Spring Data, `javalibs-resilience` | `IdempotentEventProcessor`, `CircuitBreakerRegistry` |
| `config` | mọi thứ | override bean qua `@Bean` (javalibs tự lùi nhờ `@ConditionalOnMissingBean`) |

### Code mẫu cho từng lớp

**`domain/model/Order.java`** — aggregate thuần Java:

```java
public class Order extends AggregateRoot<UUID> {

    private final UUID id;
    private final UUID customerId;
    private final Money total;
    private OrderStatus status;

    private Order(UUID id, UUID customerId, Money total) {
        this.id = Checks.notNull(id, "id");
        this.customerId = Checks.notNull(customerId, "customerId");
        this.total = total;
        this.status = OrderStatus.OPEN;
    }

    public static Order place(UUID customerId, Money total) {
        Rules.check(new OrderTotalMustBePositive(total));
        Order order = new Order(UUID.randomUUID(), customerId, total);
        order.registerEvent(new OrderPlacedEvent(order.getId(), customerId));
        return order;
    }

    @Override
    public UUID getId() { return id; }
}
```

**`domain/OrderRepository.java`** — port:

```java
public interface OrderRepository extends Repository<Order, UUID> {
    // io.javalibs.ddd.Repository đã có findById / save / delete
}
```

**`application/command/PlaceOrderHandler.java`** — use case, nơi duy nhất mở transaction:

```java
public record PlaceOrderCommand(UUID customerId, BigDecimal total) implements Command<UUID> {}

@ApplicationService
public class PlaceOrderHandler implements CommandHandler<PlaceOrderCommand, UUID> {

    private final OrderRepository orders;
    private final TransactionalOutbox outbox;

    public PlaceOrderHandler(OrderRepository orders, TransactionalOutbox outbox) {
        this.orders = orders;
        this.outbox = outbox;
    }

    @Transactional
    @Override
    public UUID handle(PlaceOrderCommand cmd) {
        Order order = Order.place(cmd.customerId(), Money.vnd(cmd.total()));
        orders.save(order);
        outbox.enqueue("orders.events",
                EventEnvelope.of("OrderPlaced", "orders-service", OrderPlacedEvent.from(order)));
        return order.getId();
    }
}
```

**`api/OrderController.java`** — mỏng, chỉ dispatch:

```java
@RestController
@RequestMapping("/api/orders")
class OrderController {

    private final CommandBus commandBus;
    private final QueryBus queryBus;

    OrderController(CommandBus commandBus, QueryBus queryBus) {
        this.commandBus = commandBus;
        this.queryBus = queryBus;
    }

    @PostMapping
    @RequireRole("SALES")
    UUID place(@Valid @RequestBody PlaceOrderRequest req, @CurrentUser UserContext user) {
        return commandBus.dispatch(new PlaceOrderCommand(req.customerId(), req.total()));
    }

    @GetMapping("/{id}")
    OrderResponse get(@PathVariable UUID id) {
        return queryBus.ask(new GetOrderQuery(id));
    }
}
```

**`api/OrderErrors.java`** — error catalog (đồng bộ với `ERROR-CODES.md`):

```java
public final class OrderErrors {
    public static final BusinessErrorCode NOT_FOUND    = BusinessErrorCode.of("ERR-ORDER-001", 404);
    public static final BusinessErrorCode ALREADY_PAID = BusinessErrorCode.of("ERR-ORDER-002", 409);
    private OrderErrors() {}
}
```

### Ghi chú thực dụng về JPA và domain model

Hai lựa chọn, cả hai đều hợp lệ:

- **Thực dụng (khuyến nghị mặc định):** gắn `@Entity`/`@Table` trực tiếp lên aggregate trong `domain/model`, repository port `extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order>` luôn. Đổi lại `domain` có dependency JPA — chấp nhận được với đa số team, và dùng được ngay `javalibs-search`.
- **Thuần khiết:** domain 100% sạch, `infrastructure/persistence` có JPA entity riêng + mapper hai chiều. Chỉ đáng khi nghiệp vụ rất phức tạp hoặc dự định đổi persistence.

Chọn một hướng cho **toàn bộ service** và ghi vào README của service — đừng trộn.

---

## Mức B — Cấu trúc layered gọn (CRUD / BFF / service nhỏ)

Bỏ CQRS và tầng domain riêng — nhưng **giữ nguyên** bộ starter hạ tầng và các chuẩn (error catalog, PageResponse, SearchQuery...):

```
inventory-service/
└── src/main/java/com/company/inventory
    ├── InventoryServiceApplication.java
    ├── web/                       # controller + request/response DTO
    │   ├── ItemController.java
    │   ├── dto/
    │   └── ItemErrors.java        # vẫn duy trì catalog BusinessErrorCode
    ├── service/                   # @Service + @Transactional
    │   └── ItemService.java
    ├── repository/                # extends JpaRepository + JpaSpecificationExecutor
    │   └── ItemRepository.java
    ├── model/                     # JPA entity
    │   └── Item.java
    └── config/                    # ghi đè bean javalibs khi cần
```

Chiều phụ thuộc: `web → service → repository/model`; `config` đứng ngoài. Quy tắc tối thiểu vẫn giữ: controller không chứa nghiệp vụ, exception ném ra là `ApiException`/`ResourceNotFoundException` của javalibs (đừng tự viết handler).

---

## Những phần giống nhau cho cả hai mức

### `pom.xml` khung

```xml
<project>
  <groupId>com.company</groupId>
  <artifactId>orders-service</artifactId>
  <version>0.1.0-SNAPSHOT</version>

  <parent>  <!-- hoặc import spring-boot-dependencies trong dependencyManagement -->
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.3</version>
    <relativePath/>
  </parent>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>io.javalibs</groupId>
        <artifactId>javalibs-dependencies</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <dependencies>
    <!-- Bộ nền gần như mọi service đều cần -->
    <dependency><groupId>io.javalibs</groupId><artifactId>javalibs-web-spring-boot-starter</artifactId></dependency>
    <dependency><groupId>io.javalibs</groupId><artifactId>javalibs-security-spring-boot-starter</artifactId></dependency>
    <dependency><groupId>io.javalibs</groupId><artifactId>javalibs-openapi-spring-boot-starter</artifactId></dependency>
    <dependency><groupId>io.javalibs</groupId><artifactId>javalibs-observability-spring-boot-starter</artifactId></dependency>
    <dependency><groupId>io.javalibs</groupId><artifactId>javalibs-persistence-spring-boot-starter</artifactId></dependency>
    <!-- Theo nhu cầu: search, datahub, cache, resilience, cqrs, ddd-spring -->
    <!-- Hạ tầng Boot: spring-boot-starter-data-jpa, driver DB... -->
    <!-- Test -->
    <dependency><groupId>io.javalibs</groupId><artifactId>javalibs-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>io.javalibs</groupId><artifactId>javalibs-security-test</artifactId><scope>test</scope></dependency>
  </dependencies>
</project>
```

Chọn starter theo nhu cầu: [ma trận tính năng ↔ starter](index.md#ma-trận-tính-năng--starter).

### Main class — giữ tối giản

```java
@SpringBootApplication
@EnableCaching   // chỉ khi dùng javalibs-cache
public class OrdersServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrdersServiceApplication.class, args);
    }
}
```

Không cần `@Import`/`@ComponentScan` cho javalibs — auto-configuration tự kích hoạt.

### Package `config/` — điểm ghi đè duy nhất

Mọi tùy biến javalibs đi qua **một trong hai cách**, ưu tiên theo thứ tự:

1. **Property** `javalibs.*` trong yaml ([tham chiếu đầy đủ](configuration-reference.md)).
2. **Bean cùng type** trong `config/` — bean javalibs tự lùi (`@ConditionalOnMissingBean`).

Gom hết vào `config/` để reviewer thấy ngay service này "lệch chuẩn" chỗ nào. Một service lý tưởng có `config/` **rỗng**.

### `resources/` và profile

```
resources/
├── application.yml            # cấu hình chung + javalibs.* ; secret qua ${ENV_VAR}
├── application-local.yml      # DB localhost, log DEBUG — profile "local" khi dev
└── db/migration/              # Flyway V<yyyyMMddHHmm>__<mô_tả>.sql
```

- Profile production không cần file riêng — khác biệt bơm qua biến môi trường/ConfigMap.
- Test dùng `src/test/resources/application-test.yml` (profile `test` được `BaseIntegrationTest` kích hoạt sẵn) — đặt `javalibs.security.jwt.secret` test ở đây.
- Dùng outbox/idempotency: copy DDL từ `META-INF/datahub/outbox-schema-postgres.sql` (jar `javalibs-datahub-spring`) thành một migration.

### Cấu trúc test — kim tự tháp 3 tầng

| Tầng | Vị trí | Đặc điểm |
|---|---|---|
| Domain unit | `test/.../domain/` | JUnit thuần, 0 Spring context, nhanh nhất — nhiều nhất |
| Handler/service unit | `test/.../application/` | Mockito mock port; verify nghiệp vụ + outbox enqueue |
| API integration | `test/.../api/*IT.java` | `extends BaseIntegrationTest` (PostgreSQL Testcontainers), token qua `JwtTestFactory` |

Đừng viết test cho những gì javalibs đã test (format lỗi JSON, parse token, relay outbox...) — chỉ test **nghiệp vụ của bạn** và các wiring quan trọng.

---

## Checklist khởi tạo service mới

- [ ] Tạo project 1 module, parent/BOM Spring Boot 3.5.x + import BOM `javalibs-dependencies`
- [ ] Chọn mức cấu trúc (A: DDD+CQRS / B: layered) và ghi lựa chọn vào README của service
- [ ] Khai báo bộ starter nền (web, security, openapi, observability, persistence) + starter theo nhu cầu
- [ ] `application.yml`: `spring.application.name`, datasource, `javalibs.security.jwt.secret` qua env, `ddl-auto: validate`
- [ ] Migration Flyway đầu tiên (+ DDL outbox nếu dùng datahub)
- [ ] Tạo `ERROR-CODES.md` + class `*Errors` với `BusinessErrorCode` đầu tiên
- [ ] `application-test.yml` với secret test; viết IT đầu tiên extends `BaseIntegrationTest`
- [ ] Chạy `mvn verify` xanh; mở `swagger-ui.html` kiểm tra bearer scheme + global error responses
- [ ] CI: build + verify trên PR (tham khảo `.github/workflows/ci.yml` của javalibs)

## Xem thêm

- [Getting Started](getting-started.md) — các bước khai báo chi tiết từng starter
- [Cookbook](cookbook.md) — outbox, idempotent consumer, revoke token, cache, circuit breaker...
- [Kiến trúc javalibs](architecture.md) — hiểu vì sao thư viện tách 4 tầng (còn service thì không)
