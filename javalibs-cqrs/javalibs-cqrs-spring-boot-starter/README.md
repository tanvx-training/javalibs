# javalibs-cqrs-spring-boot-starter

Starter Spring Boot cho bộ thư viện CQRS của javalibs. Module này **không chứa code** — nó chỉ gom 3 dependency sau thành một:

- `javalibs-cqrs-core` — các abstraction thuần Java (`Command`, `Query`, handler, bus).
- `javalibs-cqrs-spring` — `SpringCommandBus`, `SpringQueryBus`.
- `javalibs-cqrs-spring-boot-autoconfigure` — tự động cấu hình hai bus.

## Cài đặt

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-cqrs-spring-boot-starter</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Ví dụ sử dụng

Định nghĩa command dưới dạng record, viết handler là một `@Component`, sau đó inject `CommandBus` và dispatch:

```java
import io.javalibs.cqrs.Command;
import io.javalibs.cqrs.CommandHandler;
import io.javalibs.cqrs.CommandBus;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import java.util.UUID;

// 1. Command: yêu cầu tạo đơn hàng, kết quả trả về là UUID của đơn mới.
public record CreateOrderCommand(String productCode, int quantity) implements Command<UUID> {}

// 2. Handler: bean Spring, tự động được bus phát hiện và đăng ký.
@Component
public class CreateOrderHandler implements CommandHandler<CreateOrderCommand, UUID> {
    @Override
    public UUID handle(CreateOrderCommand command) {
        UUID orderId = UUID.randomUUID();
        // ... nghiệp vụ tạo đơn hàng ...
        return orderId;
    }
}

// 3. Inject CommandBus và dispatch.
@Service
public class OrderService {

    private final CommandBus commandBus;

    public OrderService(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    public UUID placeOrder(String productCode, int quantity) {
        return commandBus.dispatch(new CreateOrderCommand(productCode, quantity));
    }
}
```

## Cấu hình

| Thuộc tính | Mặc định | Ý nghĩa |
|---|---|---|
| `javalibs.cqrs.enabled` | `true` | Đặt `false` để tắt auto-configuration (không đăng ký `CommandBus`/`QueryBus` mặc định). |

Ứng dụng có thể ghi đè bus mặc định bằng cách tự khai báo bean `CommandBus` hoặc `QueryBus` — auto-configuration sẽ tự động nhường.

Xem thêm hướng dẫn chi tiết tại `javalibs-cqrs/README.md`.
