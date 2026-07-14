# javalibs-cqrs

Bộ thư viện CQRS (Command Query Responsibility Segregation) dùng chung cho các dự án Java/Spring Boot. Module cung cấp các abstraction tối giản để tách rõ **Command** (ghi/thay đổi trạng thái) và **Query** (đọc dữ liệu), cùng cơ chế bus tự động định tuyến tới đúng handler.

## Mục đích

- Chuẩn hoá cách viết use case theo mô hình CQRS: mỗi command/query có đúng **một** handler.
- Không ràng buộc framework ở tầng core — có thể dùng trong bất kỳ dự án Java 21 nào.
- Tích hợp Spring Boot "cắm là chạy": chỉ cần thêm starter, khai báo handler dưới dạng bean, inject `CommandBus`/`QueryBus` và sử dụng.

## Kiến trúc 4 module con

| Module | Nội dung | Phụ thuộc |
|---|---|---|
| `javalibs-cqrs-core` | Các interface thuần Java: `Command<R>`, `Query<R>`, `CommandHandler`, `QueryHandler`, `CommandBus`, `QueryBus` và bộ exception (`CqrsException`, `NoHandlerFoundException`, `DuplicateHandlerException`). | Không có (pure Java) |
| `javalibs-cqrs-spring` | `SpringCommandBus`, `SpringQueryBus`: nhận danh sách handler, phân giải generic type bằng `ResolvableType` (hỗ trợ cả handler bị proxy, ví dụ `@Transactional`) và định tuyến theo class của command/query. | core + spring-core + spring-context + slf4j-api |
| `javalibs-cqrs-spring-boot-autoconfigure` | `CqrsAutoConfiguration` tự động đăng ký hai bus từ mọi handler bean trong context; `CqrsProperties` bind namespace `javalibs.cqrs.*`. Tự động "nhường" khi ứng dụng định nghĩa bus riêng. | spring + spring-boot-autoconfigure |
| `javalibs-cqrs-spring-boot-starter` | Không chứa code; gom 3 module trên thành một dependency duy nhất. | core + spring + autoconfigure |

## Cấu hình

| Thuộc tính | Kiểu | Mặc định | Ý nghĩa |
|---|---|---|---|
| `javalibs.cqrs.enabled` | `boolean` | `true` | Bật/tắt toàn bộ auto-configuration CQRS. Đặt `false` để không đăng ký `CommandBus`/`QueryBus` mặc định. |

## Cách sử dụng

Thêm starter vào `pom.xml` của ứng dụng:

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-cqrs-spring-boot-starter</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 1. Định nghĩa command và handler

```java
import io.javalibs.cqrs.Command;
import io.javalibs.cqrs.CommandHandler;
import org.springframework.stereotype.Component;
import java.util.UUID;

/** Command tạo đơn hàng, trả về id của đơn mới. */
public record CreateOrderCommand(String productCode, int quantity) implements Command<UUID> {}

@Component
public class CreateOrderHandler implements CommandHandler<CreateOrderCommand, UUID> {
    @Override
    public UUID handle(CreateOrderCommand command) {
        UUID orderId = UUID.randomUUID();
        // ... lưu đơn hàng ...
        return orderId;
    }
}
```

### 2. Định nghĩa query và handler

```java
import io.javalibs.cqrs.Query;
import io.javalibs.cqrs.QueryHandler;
import org.springframework.stereotype.Component;

public record FindOrderQuery(String orderId) implements Query<OrderView> {}

@Component
public class FindOrderHandler implements QueryHandler<FindOrderQuery, OrderView> {
    @Override
    public OrderView handle(FindOrderQuery query) {
        return orderRepository.findView(query.orderId());
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
    public OrderView find(@PathVariable String id) {
        return queryBus.ask(new FindOrderQuery(id));
    }
}
```

## Quy tắc và lỗi thường gặp

- **Mỗi command/query chỉ có một handler.** Nếu hai handler cùng xử lý một loại command/query, bus ném `DuplicateHandlerException` ngay khi khởi tạo (fail fast).
- **Không có handler phù hợp** khi dispatch → `NoHandlerFoundException` (message chứa tên class của command/query).
- **Không dùng lambda làm handler.** Lambda không giữ thông tin generic nên bus không thể phân giải loại command/query — hãy dùng class cụ thể (hoặc anonymous class).
- Handler bị proxy (ví dụ `@Transactional`) vẫn hoạt động bình thường: bus phân giải generic dựa trên user class.
- Muốn thay thế bus mặc định? Chỉ cần khai báo bean `CommandBus`/`QueryBus` của riêng bạn — auto-configuration sẽ tự động nhường (`@ConditionalOnMissingBean`).

## Build

```bash
mvn -f javalibs-cqrs/pom.xml package
```
