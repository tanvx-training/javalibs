# javalibs-cqrs

> Building block CQRS (Command Query Responsibility Segregation): abstraction Command/Query thuần Java, bus in-memory chạy trên Spring, auto-configuration và starter "cắm là chạy".

## Artifacts

| artifactId | Packaging | Mô tả |
|---|---|---|
| `javalibs-cqrs-core` | jar | Abstraction thuần Java trong `io.javalibs.cqrs`: `Command`, `Query`, handler, bus và bộ exception. **Zero dependency runtime.** |
| `javalibs-cqrs-spring` | jar | `SpringCommandBus`, `SpringQueryBus` trong `io.javalibs.cqrs.spring` — phân giải generic bằng `ResolvableType`, định tuyến theo class của message. |
| `javalibs-cqrs-spring-boot-autoconfigure` | jar | `CqrsAutoConfiguration` + `CqrsProperties` trong `io.javalibs.cqrs.autoconfigure` — tự đăng ký hai bus từ mọi handler bean. |
| `javalibs-cqrs-spring-boot-starter` | jar | Không chứa code; gom 3 artifact trên thành một dependency. |

## Khi nào dùng / không dùng

**Dùng khi:**
- Muốn chuẩn hóa use case theo CQRS: mỗi command/query là một record bất biến với đúng **một** handler, controller/service chỉ biết `CommandBus`/`QueryBus` thay vì hàng chục service.
- Kết hợp với [javalibs-ddd](ddd.md): command handler là nơi orchestrate aggregate.

**Không dùng khi:**
- Cần command bus **phân tán** (gửi qua message broker, cross-service) — bus ở đây là in-memory, đồng bộ, trong một JVM; event tích hợp dùng [javalibs-datahub](datahub.md).
- Cần middleware pipeline (validation, logging, retry quanh handler) — module không có khái niệm decorator dựng sẵn; có thể tự ghi đè bus (xem [Ghi đè & mở rộng](#ghi-đè--mở-rộng)).
- Ứng dụng CRUD nhỏ mà một service class là đủ rõ ràng.

## Các thành phần chính

### javalibs-cqrs-core (`io.javalibs.cqrs`)

| Thành phần | Chữ ký / hành vi |
|---|---|
| `Command<R>` | Marker interface — ý định **thay đổi trạng thái**; `R` là kiểu kết quả (`Void` nếu không trả gì). Nên implement bằng record bất biến. |
| `Query<R>` | Marker interface — ý định **đọc dữ liệu**, không đổi trạng thái. |
| `CommandHandler<C extends Command<R>, R>` | `@FunctionalInterface` với `R handle(C command)`. Stateless, đăng ký làm bean. Đúng một handler cho mỗi loại command. |
| `QueryHandler<Q extends Query<R>, R>` | `@FunctionalInterface` với `R handle(Q query)`. |
| `CommandBus` | `<R> R dispatch(Command<R> command)` — định tuyến tới handler duy nhất của concrete type, trả kết quả của handler. Ném `NoHandlerFoundException` khi không có handler. |
| `QueryBus` | `<R> R ask(Query<R> query)` — tương tự cho query. |
| `CqrsException` | `RuntimeException` gốc cho mọi lỗi hạ tầng CQRS. Constructor `(String message)` và `(String message, Throwable cause)`. |
| `NoHandlerFoundException` | Kế thừa `CqrsException`; ném lúc dispatch/ask khi không có handler. `getMessageType()` trả về class của command/query không được xử lý; message chứa tên class đầy đủ. |
| `DuplicateHandlerException` | Kế thừa `CqrsException`; ném **lúc khởi tạo bus** (fail fast) khi hai handler đăng ký cùng một loại message. Constructor `(Class<?> messageType, Class<?> existingHandler, Class<?> newHandler)`; `getMessageType()` trả về class bị trùng. |

### javalibs-cqrs-spring (`io.javalibs.cqrs.spring`)

#### `SpringCommandBus` / `SpringQueryBus`

- Constructor: `SpringCommandBus(List<CommandHandler<?, ?>> commandHandlers)` / `SpringQueryBus(List<QueryHandler<?, ?>> queryHandlers)` — danh sách không được `null`.
- **Lúc khởi tạo**: với mỗi handler, bus phân giải concrete class của command/query từ generic type parameter (qua `HandlerTypeResolver`) và ghi vào registry map bất biến. Trùng loại → `DuplicateHandlerException` ngay lập tức.
- **Lúc dispatch/ask**: lookup map theo `command.getClass()` / `query.getClass()` (O(1)), gọi `handle(...)` **đồng bộ trên thread hiện tại** và trả kết quả. Không có handler → `NoHandlerFoundException`. Message `null` → `NullPointerException`.
- Handler bị **proxy** (ví dụ `@Transactional`) vẫn hoạt động: generic được phân giải trên user class (`ClassUtils.getUserClass`), không phải class proxy runtime.
- Handler khai báo qua **cây kế thừa** (extends một base class generic) và **anonymous class** đều phân giải được.

#### `HandlerTypeResolver` (package-private, internal)

```java
static Class<?> resolveMessageType(Class<?> handlerInterface, Class<?> markerType, Object handler)
```

Lấy user class của handler rồi resolve generic đầu tiên của `handlerInterface` (`CommandHandler.class`/`QueryHandler.class`) bằng `ResolvableType`. Nếu kết quả là `null` hoặc chính marker interface (`Command`/`Query`) — tức không phải một message class cụ thể — ném `CqrsException` với hướng dẫn khắc phục.

**Vì sao lambda bị từ chối:** lambda trong Java bị type erasure hoàn toàn ở runtime — class sinh ra cho lambda **không giữ** thông tin generic của interface nó implement, nên `ResolvableType` chỉ resolve được về bound của type variable, tức bare marker `Command`/`Query`, không bao giờ ra concrete class. Bus không thể biết lambda đó xử lý command nào, nên fail fast lúc khởi tạo với message:

```
Unable to resolve the message type handled by [<class>]. Implement CommandHandler
with a concrete class (lambdas do not retain generic type information).
```

**Cách sửa:** viết handler là **class cụ thể** (khuyến nghị — bean Spring bình thường) hoặc anonymous class (giữ generic nên vẫn resolve được):

```java
// SAI — ném CqrsException khi tạo bus:
CommandHandler<CreateOrderCommand, UUID> bad = cmd -> UUID.randomUUID();

// ĐÚNG:
@Component
class CreateOrderHandler implements CommandHandler<CreateOrderCommand, UUID> {
    public UUID handle(CreateOrderCommand command) { ... }
}
```

### javalibs-cqrs-spring-boot-autoconfigure (`io.javalibs.cqrs.autoconfigure`)

| Thành phần | Hành vi |
|---|---|
| `CqrsAutoConfiguration` | `@AutoConfiguration`, `@EnableConfigurationProperties(CqrsProperties.class)`, `@ConditionalOnProperty(prefix = "javalibs.cqrs", name = "enabled", havingValue = "true", matchIfMissing = true)`. Đăng ký: `springCommandBus(ObjectProvider<CommandHandler<?, ?>>)` với `@ConditionalOnMissingBean(CommandBus.class)` và `springQueryBus(ObjectProvider<QueryHandler<?, ?>>)` với `@ConditionalOnMissingBean(QueryBus.class)`. Handler được thu thập bằng `orderedStream()` (tôn trọng `@Order`). Context không có handler nào vẫn tạo bus rỗng hợp lệ. |
| `CqrsProperties` | `record CqrsProperties(@DefaultValue("true") boolean enabled)`, bind prefix `javalibs.cqrs`. |

## Cấu hình

| Thuộc tính | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `javalibs.cqrs.enabled` | `boolean` | `true` | Bật/tắt toàn bộ auto-configuration. Đặt `false` để không đăng ký `CommandBus`/`QueryBus` mặc định (cả hai bean đều biến mất). |

Đây là thuộc tính duy nhất của module.

## Hướng dẫn sử dụng

Thêm starter (kéo đủ core + spring + autoconfigure):

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-cqrs-spring-boot-starter</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 1. Command + handler

```java
import io.javalibs.cqrs.Command;
import io.javalibs.cqrs.CommandHandler;
import org.springframework.stereotype.Component;
import java.util.UUID;

/** Command tạo đơn hàng; kết quả là id của đơn mới. */
public record CreateOrderCommand(String productCode, int quantity) implements Command<UUID> {}

@Component
public class CreateOrderHandler implements CommandHandler<CreateOrderCommand, UUID> {

    private final OrderRepository orders;

    public CreateOrderHandler(OrderRepository orders) {
        this.orders = orders;
    }

    @Override
    public UUID handle(CreateOrderCommand command) {
        Order order = Order.create(command.productCode(), command.quantity());
        orders.save(order);
        return order.getId();
    }
}
```

### 2. Query + handler

```java
import io.javalibs.cqrs.Query;
import io.javalibs.cqrs.QueryHandler;
import org.springframework.stereotype.Component;
import java.util.UUID;

public record GetOrderQuery(UUID orderId) implements Query<OrderView> {}

@Component
public class GetOrderHandler implements QueryHandler<GetOrderQuery, OrderView> {

    private final OrderViewRepository views;

    public GetOrderHandler(OrderViewRepository views) {
        this.views = views;
    }

    @Override
    public OrderView handle(GetOrderQuery query) {
        return views.findById(query.orderId());
    }
}
```

### 3. Inject bus và dispatch

```java
import io.javalibs.cqrs.CommandBus;
import io.javalibs.cqrs.QueryBus;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final CommandBus commandBus;
    private final QueryBus queryBus;

    public OrderController(CommandBus commandBus, QueryBus queryBus) {
        this.commandBus = commandBus;
        this.queryBus = queryBus;
    }

    @PostMapping
    public UUID create(@RequestBody CreateOrderRequest request) {
        return commandBus.dispatch(new CreateOrderCommand(request.productCode(), request.quantity()));
    }

    @GetMapping("/{id}")
    public OrderView get(@PathVariable UUID id) {
        return queryBus.ask(new GetOrderQuery(id));
    }
}
```

Không dùng Spring Boot? Dùng `javalibs-cqrs-spring` trực tiếp và tự dựng bus: `new SpringCommandBus(List.of(handler1, handler2, ...))`.

## Ghi đè & mở rộng

- **Thay bus mặc định**: khai báo bean `CommandBus` hoặc `QueryBus` của riêng bạn — mỗi bean auto-configured đều `@ConditionalOnMissingBean` nên tự nhường **độc lập nhau** (ghi đè `CommandBus` không ảnh hưởng `SpringQueryBus`). Đây cũng là cách chèn decorator (logging, metrics, validation) bọc quanh `SpringCommandBus`:

  ```java
  @Bean
  CommandBus commandBus(ObjectProvider<CommandHandler<?, ?>> handlers) {
      CommandBus delegate = new SpringCommandBus(handlers.orderedStream().toList());
      return new LoggingCommandBus(delegate);   // decorator của bạn
  }
  ```

- **Tắt hẳn**: `javalibs.cqrs.enabled=false`.
- Cần transaction quanh handler? Đánh dấu `@Transactional` lên chính handler bean — bus hỗ trợ handler bị proxy.

## Testing

- **Unit test handler**: gọi thẳng `handler.handle(command)` — không cần bus, không cần Spring.
- **Unit test với bus thật** (nhanh, không context):

  ```java
  SpringCommandBus bus = new SpringCommandBus(List.of(new CreateOrderHandler(fakeRepo)));
  UUID id = bus.dispatch(new CreateOrderCommand("BOOK-1", 2));
  ```

- **Test auto-configuration** bằng `ApplicationContextRunner` (xem `javalibs-cqrs-spring-boot-autoconfigure/src/test/java/io/javalibs/cqrs/autoconfigure/CqrsAutoConfigurationTest.java`):

  ```java
  new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(CqrsAutoConfiguration.class))
          .withUserConfiguration(HandlersConfiguration.class)
          .run(context -> {
              assertThat(context).hasSingleBean(CommandBus.class);
              assertThat(context.getBean(CommandBus.class)
                      .dispatch(new CreateOrderCommand("book", 1))).isNotNull();
          });
  ```

- Test tích hợp full-stack: kế thừa `BaseIntegrationTest` của [javalibs-test](test.md) và gọi API qua `TestRestTemplate`.

## Lưu ý & bẫy thường gặp

- **Không dùng lambda làm handler** — bus ném `CqrsException` ("Unable to resolve the message type...") ngay khi khởi tạo vì lambda không giữ generic. Dùng class cụ thể (hoặc anonymous class).
- **Mỗi command/query đúng một handler** — hai handler cùng loại làm app **fail lúc startup** với `DuplicateHandlerException` (chủ đích fail fast, message nêu rõ hai class xung đột).
- Dispatch một message chưa có handler → `NoHandlerFoundException` lúc runtime — dễ gặp khi quên `@Component` trên handler hoặc handler nằm ngoài phạm vi component scan.
- **Định tuyến theo class runtime chính xác** (`command.getClass()`), không xét kế thừa của command: subclass của một command đã có handler sẽ **không** khớp handler của lớp cha. Giữ command/query là record `final` — đúng như thiết kế.
- Bus **đồng bộ, in-memory**: exception từ handler bay thẳng lên caller; không có retry/async/persistence. `@ConditionalOnMissingBean` chỉ nhìn kiểu `CommandBus`/`QueryBus`.
- Registry được chốt lúc tạo bus (`Map.copyOf`) — handler đăng ký vào context **sau** khi bus khởi tạo (bean lazy, đăng ký runtime) sẽ không được thấy.
- Đặt `javalibs.cqrs.enabled=false` mà vẫn inject `CommandBus` sẽ fail khi start context vì bean không tồn tại — tắt property đồng nghĩa tự cung cấp bus.
