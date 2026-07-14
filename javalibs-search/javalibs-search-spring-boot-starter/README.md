# javalibs-search-spring-boot-starter

Starter Spring Boot cho bộ thư viện lọc động `javalibs-search`. Thêm một dependency là có
ngay: parser `SearchQueryParser` (bean tự cấu hình từ `javalibs.search.*`), argument
resolver cho tham số controller kiểu `SearchQuery`, cùng `SpecificationBuilder` và
`PageRequests` để thực thi truy vấn bằng Spring Data JPA.

## Cài đặt

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-search-spring-boot-starter</artifactId>
</dependency>
```

Starter đã kéo sẵn `spring-boot-starter-data-jpa`.

## Sử dụng

Repository cần mở rộng `JpaSpecificationExecutor`:

```java
public interface OrderRepository
        extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
}
```

Controller khai báo thẳng tham số `SearchQuery` — argument resolver tự parse query string:

```java
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderRepository orders;

    public OrderController(OrderRepository orders) {
        this.orders = orders;
    }

    @GetMapping
    public Page<Order> list(SearchQuery q) {
        return orders.findAll(
                SpecificationBuilder.toSpecification(q),
                PageRequests.of(q));
    }
}
```

## Ví dụ URL

```text
/orders?filter=status:eq:OPEN&filter=total:gte:100&sort=createdAt,desc&page=0&size=20
/orders?filter=status:in:NEW,OPEN&filter=createdAt:between:2024-01-01T00:00:00Z,2024-12-31T23:59:59Z
/orders?filter=customer.name:like:nguyen&sort=total,desc
/orders?filter=customer:isnull
/orders?filter=status:eq:NEW&filter=total:gte:500&combinator=or
```

- `filter=field:op:value` — lặp lại nhiều lần; các toán tử: `eq`, `neq`, `gt`, `gte`,
  `lt`, `lte`, `like`, `nlike`, `in`, `nin`, `between`, `isnull`, `notnull`.
- `sort=field,asc|desc` — lặp lại nhiều lần để sort đa cột; bỏ phần direction thì mặc
  định `asc`.
- `page` tính từ 0; `size` bị kẹp về tối đa `javalibs.search.max-page-size` (mặc định 100).
- `combinator=and|or` quyết định cách nối các filter (mặc định `and`).

## Cấu hình

```yaml
javalibs:
  search:
    filter-param: filter
    sort-param: sort
    page-param: page
    size-param: size
    default-page-size: 20
    max-page-size: 100
    default-combinator: and
```

Input sai cú pháp ném `io.javalibs.search.SearchParseException` (một `RuntimeException`)
— nên bắt trong `@ControllerAdvice` và trả về HTTP 400.

Chi tiết cú pháp filter, bảng property đầy đủ và các lưu ý bảo mật: xem README của module
cha `javalibs-search`.
