# javalibs-ddd

> Các building block Domain-Driven Design chiến thuật: Entity, AggregateRoot, ValueObject, DomainEvent, Repository, BusinessRule — core thuần Java, kèm tích hợp Spring cho việc publish domain event.

## Artifacts

| artifactId | Packaging | Mô tả |
|---|---|---|
| `javalibs-ddd-core` | jar | Building block thuần Java trong package `io.javalibs.ddd`, **zero dependency** — domain logic dựa trên module này unit-test trong mili-giây. |
| `javalibs-ddd-spring` | jar | Tích hợp Spring trong `io.javalibs.ddd.spring`: `SpringDomainEventPublisher`, stereotype `@DomainService`/`@ApplicationService`, và `DddAutoConfiguration` (tự kích hoạt khi có Spring Boot — dependency `spring-boot-autoconfigure` là optional). |

## Khi nào dùng / không dùng

**Dùng khi:**
- Xây service có domain model thực sự (aggregate, invariant, domain event) và muốn tầng domain **không phụ thuộc framework** — chỉ cần `javalibs-ddd-core`.
- Muốn chuẩn hóa flow "persist aggregate → publish domain event" qua Spring event bus với listener `@TransactionalEventListener` — thêm `javalibs-ddd-spring`.

**Không dùng khi:**
- Service chỉ là CRUD mỏng — base class aggregate/event chỉ thêm ceremony không cần thiết.
- Cần đưa event **ra ngoài process** (Kafka, outbox) — `SpringDomainEventPublisher` là in-memory, trong-JVM; dùng [javalibs-datahub](datahub.md) cho event tích hợp giữa các service.
- Cần Command/Query bus — đó là vai trò của [javalibs-cqrs](cqrs.md) (hai module phối hợp tốt: command handler là nơi gọi aggregate).

## Các thành phần chính

### javalibs-ddd-core (`io.javalibs.ddd`)

#### `Entity<ID>` (abstract class)

Base class cho entity — đối tượng định danh bằng identity, không phải thuộc tính.

| Method | Hành vi |
|---|---|
| `abstract ID getId()` | Identity của entity; có thể `null` trước lần persist đầu tiên. |
| `boolean equals(Object other)` | Bằng nhau khi **cùng concrete class và cùng id khác `null`**. Entity có id `null` chỉ bằng chính nó (`this == other`). |
| `int hashCode()` | Trả về `getClass().hashCode()` — hằng số theo class, để hash không đổi khi id được gán lúc persist. |
| `String toString()` | `"<SimpleClassName>[id=<id>]"`. |

#### `AggregateRoot<ID>` (abstract class, extends `Entity<ID>`)

Ranh giới nhất quán của domain model — thực thể duy nhất mà `Repository` load/lưu. Giữ danh sách `DomainEvent` nội bộ (field `transient`).

| Method | Hành vi |
|---|---|
| `protected void registerEvent(DomainEvent event)` | Ghi nhận event sẽ được publish sau khi persist; ném `IllegalArgumentException` nếu `null`. **Protected** — chỉ gọi từ bên trong aggregate. |
| `List<DomainEvent> domainEvents()` | View **chỉ đọc** của các event đã ghi nhận, chưa publish. |
| `List<DomainEvent> pullDomainEvents()` | Trả về bản copy các event **và xóa danh sách nội bộ** — gọi đúng một lần cho mỗi thao tác persist, rồi publish kết quả. |

#### `ValueObject` (marker interface)

Đánh dấu value object: bất biến, định nghĩa hoàn toàn bằng thuộc tính. Nên implement bằng Java `record` để equality/hashing/immutability có sẵn:

```java
public record Money(BigDecimal amount, Currency currency) implements ValueObject {
    public Money {
        Objects.requireNonNull(amount);
        Objects.requireNonNull(currency);
    }
}
```

#### `DomainEvent` (interface) và `AbstractDomainEvent` (abstract class)

Sự kiện domain — sự thật bất biến, đặt tên thì quá khứ (`OrderPlaced`).

| Thành phần | Hành vi |
|---|---|
| `UUID eventId()` | Định danh duy nhất của lần xảy ra event. |
| `Instant occurredOn()` | Thời điểm xảy ra. |
| `default String eventType()` | Mặc định là simple class name (ví dụ `"OrderPlaced"`). |
| `AbstractDomainEvent` | Base class tiện dụng: sinh sẵn `eventId` (`UUID.randomUUID()`) và `occurredOn` (`Instant.now()`) trong constructor; `toString()` in `eventType[eventId=…, occurredOn=…]`. |

#### `Repository<T extends AggregateRoot<ID>, ID>` (interface)

Contract repository độc lập công nghệ — tầng infrastructure implement bằng JPA/JDBC/MongoDB…

```java
Optional<T> findById(ID id);
T save(T aggregate);
void delete(T aggregate);
```

#### `DomainEventPublisher` (interface)

| Method | Hành vi |
|---|---|
| `void publish(DomainEvent event)` | Publish một event. |
| `default void publishAll(Collection<? extends DomainEvent> events)` | Publish lần lượt từng event. |
| `default void publishFrom(AggregateRoot<?> aggregate)` | Gọi `aggregate.pullDomainEvents()` (lấy **và xóa**) rồi publish từng event — gọi **sau khi** aggregate đã persist thành công. |

#### `BusinessRule`, `Rules`, `BusinessRuleViolationException`

| Thành phần | Hành vi |
|---|---|
| `BusinessRule.isViolated()` | `true` khi rule bị vi phạm ở trạng thái hiện tại. |
| `BusinessRule.message()` | Mô tả vi phạm cho người đọc. |
| `default BusinessRule.code()` | Mã rule ổn định cho payload lỗi API; mặc định là simple class name. |
| `Rules.check(BusinessRule... rules)` | Kiểm tra tuần tự, ném `BusinessRuleViolationException` cho rule **đầu tiên** bị vi phạm. |
| `BusinessRuleViolationException` | `RuntimeException` mang theo rule bị vi phạm: `getRule()`, `getRuleCode()`; message là `rule.message()`. |

### javalibs-ddd-spring (`io.javalibs.ddd.spring`)

| Thành phần | Hành vi |
|---|---|
| `SpringDomainEventPublisher` | `DomainEventPublisher` ủy quyền cho `ApplicationEventPublisher` của Spring (in-memory event bus). Constructor: `SpringDomainEventPublisher(ApplicationEventPublisher delegate)`. Log DEBUG mỗi event publish. |
| `@DomainService` | Stereotype cho domain service stateless (nghiệp vụ không thuộc về một aggregate). Meta-annotated `@Component` (có alias `value()` cho tên bean) nên được component scan bắt. |
| `@ApplicationService` | Stereotype cho application service (use-case orchestrator): load aggregate, gọi hành vi domain, persist, publish event — **không chứa business rule**. Cũng meta-annotated `@Component`. |
| `DddAutoConfiguration` | `@AutoConfiguration` đăng ký bean `SpringDomainEventPublisher` với `@ConditionalOnMissingBean(DomainEventPublisher.class)`. Được khai báo trong `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` nên tự chạy trong app Spring Boot. |

## Cấu hình

Module **không có thuộc tính cấu hình `javalibs.*` nào**. `DddAutoConfiguration` không bind properties — nó chỉ đăng ký một bean duy nhất và tự nhường khi ứng dụng đã có `DomainEventPublisher` riêng.

## Hướng dẫn sử dụng

Dependency (tầng domain chỉ cần core; app Spring Boot thêm spring):

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-ddd-core</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-ddd-spring</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Ví dụ hoàn chỉnh: aggregate `Order` với event `OrderPlaced`

**1. Domain event** — kế thừa `AbstractDomainEvent` để có sẵn `eventId`/`occurredOn`:

```java
import io.javalibs.ddd.AbstractDomainEvent;
import java.util.UUID;

public class OrderPlaced extends AbstractDomainEvent {

    private final UUID orderId;

    public OrderPlaced(UUID orderId) {
        this.orderId = orderId;
    }

    public UUID orderId() {
        return orderId;
    }
}
```

**2. Business rule** — record implement `BusinessRule`:

```java
import io.javalibs.ddd.BusinessRule;
import java.util.List;

public record OrderMustHaveItems(List<OrderItem> items) implements BusinessRule {

    @Override
    public boolean isViolated() {
        return items.isEmpty();
    }

    @Override
    public String message() {
        return "An order must contain at least one item";
    }
    // code() mặc định = "OrderMustHaveItems"
}
```

**3. Aggregate** — kiểm tra rule rồi ghi nhận event bằng `registerEvent`:

```java
import io.javalibs.ddd.AggregateRoot;
import io.javalibs.ddd.Rules;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Order extends AggregateRoot<UUID> {

    private final UUID id;
    private final List<OrderItem> items = new ArrayList<>();
    private boolean placed;

    public Order(UUID id) {
        this.id = id;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public void addItem(OrderItem item) {
        items.add(item);
    }

    public void place() {
        Rules.check(new OrderMustHaveItems(items));  // ném BusinessRuleViolationException nếu rỗng
        this.placed = true;
        registerEvent(new OrderPlaced(id));          // ghi nhận, CHƯA publish
    }
}
```

**4. Application service** — persist trước, publish sau:

```java
import io.javalibs.ddd.DomainEventPublisher;
import io.javalibs.ddd.spring.ApplicationService;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@ApplicationService
public class PlaceOrderService {

    private final OrderRepository orders;          // implements Repository<Order, UUID>
    private final DomainEventPublisher events;     // bean do DddAutoConfiguration cung cấp

    public PlaceOrderService(OrderRepository orders, DomainEventPublisher events) {
        this.orders = orders;
        this.events = events;
    }

    @Transactional
    public void placeOrder(UUID orderId) {
        Order order = orders.findById(orderId).orElseThrow();
        order.place();               // rule check + registerEvent(OrderPlaced)
        orders.save(order);          // 1. persist
        events.publishFrom(order);   // 2. pull (lấy + xóa) rồi publish từng event
    }
}
```

**5. Listener** — chỉ chạy sau khi transaction commit:

```java
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderPlacedListener {

    @TransactionalEventListener
    public void on(OrderPlaced event) {
        // Chạy ở phase AFTER_COMMIT (mặc định): đơn hàng chắc chắn đã nằm trong DB.
        // Ví dụ: gửi email xác nhận, cập nhật read model...
    }
}
```

**Flow `pullDomainEvents` + `publishFrom`:** `place()` chỉ **ghi nhận** event vào danh sách nội bộ của aggregate. Sau khi `save()` thành công, `publishFrom(order)` gọi `order.pullDomainEvents()` — trả về bản copy các event và **xóa sạch** danh sách (nên gọi lại lần hai trả về rỗng, không publish trùng) — rồi đẩy từng event vào `ApplicationEventPublisher`. Vì `publishFrom` chạy **bên trong** method `@Transactional`, Spring xếp event vào hàng đợi transaction và chỉ giao cho `@TransactionalEventListener` sau khi commit; nếu transaction rollback, listener không bao giờ chạy.

## Ghi đè & mở rộng

- **`DomainEventPublisher`**: `DddAutoConfiguration` đăng ký `SpringDomainEventPublisher` với `@ConditionalOnMissingBean(DomainEventPublisher.class)` — chỉ cần khai báo bean `DomainEventPublisher` riêng (ví dụ publisher ghi outbox qua [javalibs-datahub](datahub.md)) là auto-configuration tự nhường.
- Ngoài Spring Boot (app Spring thuần), auto-configuration không chạy — tự khai báo `new SpringDomainEventPublisher(applicationEventPublisher)` làm bean.
- `Repository`, `BusinessRule`, `DomainEvent` đều là interface — ứng dụng tự do implement theo công nghệ của mình.

## Testing

Tầng domain test thuần JUnit, không cần Spring:

```java
@Test
void placingOrderWithItemsRegistersOrderPlaced() {
    Order order = new Order(UUID.randomUUID());
    order.addItem(new OrderItem("SKU-1", 2));

    order.place();

    assertThat(order.domainEvents()).hasSize(1);           // view chỉ đọc, chưa bị xóa
    List<DomainEvent> events = order.pullDomainEvents();   // lấy + xóa
    assertThat(events).singleElement().isInstanceOf(OrderPlaced.class);
    assertThat(order.domainEvents()).isEmpty();
}

@Test
void placingEmptyOrderViolatesRule() {
    Order order = new Order(UUID.randomUUID());

    assertThatThrownBy(order::place)
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessage("An order must contain at least one item");
}
```

Test tích hợp publisher + listener bằng `ApplicationContextRunner` (xem `javalibs-ddd-spring/src/test/java/io/javalibs/ddd/spring/DddAutoConfigurationTest.java`):

```java
new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(DddAutoConfiguration.class))
        .withUserConfiguration(ListenerConfig.class)
        .run(context -> {
            context.getBean(DomainEventPublisher.class).publish(new OrderPlaced(UUID.randomUUID()));
            // assert listener đã nhận event
        });
```

Với listener `@TransactionalEventListener`, test full-context (ví dụ kế thừa `BaseIntegrationTest` của [javalibs-test](test.md)) và nhớ thao tác phải chạy trong transaction thật.

## Lưu ý & bẫy thường gặp

- **`pullDomainEvents()` xóa danh sách** — gọi đúng một lần mỗi lần persist. Gọi để "xem" event sẽ làm mất event; muốn xem dùng `domainEvents()`.
- **Publish phải nằm trong transaction** nếu listener là `@TransactionalEventListener`: phase mặc định là `AFTER_COMMIT`, và khi **không có** transaction active thì listener mặc định **không chạy** (`fallbackExecution = false`). Listener không cần transaction thì dùng `@EventListener` thường.
- `SpringDomainEventPublisher` là **in-memory, đồng bộ, trong một JVM** — không phải message broker. Service khác không nhận được; độ bền không đảm bảo (app chết sau commit nhưng trước khi listener chạy là mất event). Cần độ tin cậy liên service → transactional outbox của [javalibs-datahub](datahub.md).
- Equality của `Entity`: hai entity **cùng id `null` không bằng nhau** (trừ khi cùng instance), và `hashCode` là hằng số theo class — bỏ nhiều entity cùng loại vào `HashSet`/`HashMap` sẽ dồn về một bucket, chấp nhận được cho collection nhỏ trong aggregate nhưng đừng dùng cho tập lớn.
- `Rules.check` ném cho rule **đầu tiên** vi phạm — thứ tự tham số quyết định rule nào được báo; nó không gom tất cả vi phạm.
- Trường `domainEvents` là `transient` — không được serialize; đừng kỳ vọng event sống sót qua serialize/deserialize (cache, session).
- `registerEvent` là `protected` — chỉ gọi từ trong aggregate; đây là chủ đích để mọi event đều bắt nguồn từ hành vi domain.
- Exception `BusinessRuleViolationException` nên được map thành HTTP 4xx tại tầng web — xem [javalibs-web](web.md) về error handling chuẩn.
