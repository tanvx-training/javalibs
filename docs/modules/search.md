# javalibs-search

> Lọc động (dynamic filtering) cho service Spring: chuyển query string HTTP (`?filter=...&sort=...&page=...&size=...`) thành `SearchQuery` có kiểu, rồi thành `Specification` của Spring Data JPA và `PageRequest` — không phải viết tay query cho từng màn hình tìm kiếm.

## Artifacts

| artifactId | Packaging | Mô tả |
|---|---|---|
| `javalibs-search` | pom | Parent gộp 4 submodule |
| `javalibs-search-core` | jar | Mô hình + parser thuần Java, **không phụ thuộc Spring**: `SearchQuery`, `SearchCriterion`, `SortSpec`, `SearchOperator`, `SearchQueryParser`, `SearchParseException`. Package `io.javalibs.search` |
| `javalibs-search-spring` | jar | `SpecificationBuilder` (SearchQuery → JPA `Specification`), `PageRequests` (SearchQuery → `PageRequest`), `SearchQueryArgumentResolver` (cần Spring MVC — dependency `spring-webmvc` khai báo `optional`). Package `io.javalibs.search.spring` |
| `javalibs-search-spring-boot-autoconfigure` | jar | `SearchProperties` (`javalibs.search.*`), `SearchAutoConfiguration` (bean `SearchQueryParser`), `SearchWebMvcAutoConfiguration` (đăng ký argument resolver). Package `io.javalibs.search.autoconfigure` |
| `javalibs-search-spring-boot-starter` | jar | Starter không chứa code: core + spring + autoconfigure + `spring-boot-starter-data-jpa` |

## Khi nào dùng / không dùng

**Dùng khi:**

- API cần màn hình tìm kiếm/danh sách với bộ lọc linh hoạt do client quyết định (admin portal, bảng dữ liệu, báo cáo) trên JPA entity.
- Muốn cú pháp filter thống nhất giữa các service và an toàn injection (whitelist field + bind parameter).

**Không dùng khi:**

- Query phức tạp: join tùy biến, aggregate, full-text search — hãy viết `Specification`/`@Query`/QueryDSL riêng.
- Cần lọc qua quan hệ **collection** (`@OneToMany`/`@ManyToMany`) — `SpecificationBuilder` không hỗ trợ (xem Lưu ý).
- Persistence không phải JPA (MongoDB, Elasticsearch...) — chỉ tầng `javalibs-search-core` (parser) tái dùng được, phần build query phải tự viết.

## Các thành phần chính

### `SearchOperator` (enum, core)

Đủ **13 toán tử**, mỗi toán tử có token chữ thường dùng trong cú pháp `field:op:value`:

| Hằng enum | Token | Ý nghĩa |
|---|---|---|
| `EQ` | `eq` | bằng |
| `NEQ` | `neq` | khác |
| `GT` | `gt` | lớn hơn |
| `GTE` | `gte` | lớn hơn hoặc bằng |
| `LT` | `lt` | nhỏ hơn |
| `LTE` | `lte` | nhỏ hơn hoặc bằng |
| `LIKE` | `like` | chứa chuỗi, không phân biệt hoa thường |
| `NOT_LIKE` | `nlike` | không chứa chuỗi (phủ định của `like`) |
| `IN` | `in` | thuộc danh sách giá trị phân tách bằng dấu phẩy |
| `NOT_IN` | `nin` | không thuộc danh sách |
| `BETWEEN` | `between` | trong khoảng (bao gồm hai đầu), đúng 2 giá trị |
| `IS_NULL` | `isnull` | giá trị là `null` — không có phần value |
| `NOT_NULL` | `notnull` | giá trị khác `null` — không có phần value |

`SearchOperator.fromToken(String)` resolve token không phân biệt hoa thường; token lạ ném `SearchParseException` kèm danh sách token hợp lệ. `token()` trả về token của toán tử.

### `SearchCriterion` (record, core)

`(String field, SearchOperator operator, List<String> values)` — một điều kiện lọc, value giữ dạng String thô (chuyển kiểu khi build Specification). Constructor validate và ném `SearchParseException` nếu vi phạm:

- **Field regex (whitelist)**: `^[a-zA-Z][a-zA-Z0-9_.]*$` — cho phép path lồng nhau (`customer.name`), chặn mọi chuỗi có thể dùng để injection.
- **Arity theo toán tử**: `BETWEEN` — đúng 2 value; `IN`/`NOT_IN` — ít nhất 1; `IS_NULL`/`NOT_NULL` — 0 value; các toán tử còn lại — đúng 1.

### `SortSpec` (record, core)

`(String field, Direction direction)` với enum `Direction { ASC, DESC }`. Field validate cùng regex whitelist như `SearchCriterion`; field sai hoặc direction `null` ném `SearchParseException`.

### `SearchQuery` (record, core)

`(List<SearchCriterion> criteria, Combinator combinator, List<SortSpec> sorts, int page, int size)` — kết quả parse hoàn chỉnh, immutable. Enum lồng `Combinator { AND, OR }`. Constructor chuẩn hóa: list `null` → rỗng, combinator `null` → `AND`, `page` kẹp `>= 0`, `size` kẹp `>= 1`. `SearchQuery.empty()` trả về query match-all (page 0, size 20).

### `SearchQueryParser` + `ParserConfig` (core)

`parse(Map<String, List<String>> queryParams)` → `SearchQuery`; input `null` xem như rỗng. Mọi input sai cú pháp ném `SearchParseException` với message chỉ đích danh tham số lỗi.

`ParserConfig` là record `(filterParam, sortParam, pageParam, sizeParam, defaultPageSize, maxPageSize, defaultCombinator)`:

- **`ParserConfig.defaults()` = `("filter", "sort", "page", "size", 20, 100, AND)`.**
- Constructor validate: tên tham số không được blank; `maxPageSize >= 1`; `defaultPageSize` trong `[1, maxPageSize]`; `defaultCombinator null` → `AND` (vi phạm ném `IllegalArgumentException`).
- `new SearchQueryParser()` dùng defaults; `new SearchQueryParser(config)` dùng config truyền vào; `config()` trả về config hiện tại.
- Tên tham số combinator **không cấu hình được**: hằng `SearchQueryParser.COMBINATOR_PARAM = "combinator"`.

Hành vi parse:

- `filter` lặp lại nhiều lần → nhiều criterion. Chuỗi tách bằng `:` tối đa 3 phần nên **dấu `:` trong value được giữ nguyên** (`createdAt:gte:2024-01-01T10:15:30Z` hoạt động bình thường).
- Value của `in`/`nin`/`between` tách theo dấu phẩy **chưa escape**; `\,` được unescape thành dấu phẩy literal (cả với toán tử 1 value).
- `sort=field` hoặc `sort=field,asc|desc` (mặc định `asc`); direction khác ném lỗi; lặp lại nhiều lần, ưu tiên theo thứ tự tham số.
- `page`: số âm kẹp về 0; không phải số nguyên → `SearchParseException`.
- `size`: vắng mặt → `defaultPageSize`; có giá trị → kẹp (clamp) vào `[1, maxPageSize]` (không báo lỗi khi vượt trần); không phải số → `SearchParseException`.
- `combinator=and|or` (không phân biệt hoa thường); vắng mặt → `defaultCombinator`; giá trị khác ném lỗi.

### Cú pháp filter — bảng tham chiếu kèm ví dụ URL

| Cú pháp | Ví dụ URL | Kết quả |
|---|---|---|
| `filter=field:op:value` | `/orders?filter=status:eq:OPEN` | `status = OPEN` |
| Lặp nhiều filter (mặc định AND) | `/orders?filter=status:eq:OPEN&filter=total:gte:100` | `status = OPEN AND total >= 100` |
| `combinator=or` | `/orders?filter=status:eq:NEW&filter=total:gte:500&combinator=or` | `status = NEW OR total >= 500` |
| `in` với danh sách phẩy | `/orders?filter=status:in:NEW,OPEN` | `status IN (NEW, OPEN)` |
| `between` với 2 giá trị phẩy | `/orders?filter=createdAt:between:2024-01-01T00:00:00Z,2024-12-31T23:59:59Z` | `createdAt` trong khoảng (bao gồm hai đầu) |
| Escape dấu phẩy `\,` | `/orders?filter=name:in:Nguyen\,%20Van%20A,Le` | 2 giá trị: `Nguyen, Van A` và `Le` |
| `isnull` / `notnull` (không value) | `/orders?filter=customer:isnull` | `customer IS NULL` |
| Field lồng nhau (to-one/embedded) | `/orders?filter=customer.name:like:nguyen` | JOIN sang customer, LIKE không phân biệt hoa thường |
| `sort=field,desc` (lặp được) | `/orders?sort=createdAt,desc&sort=total` | ORDER BY createdAt DESC, total ASC |
| `page`/`size` (kẹp giá trị) | `/orders?page=0&size=500` | page 0, size bị kẹp về `max-page-size` (mặc định 100); `page=-1` → 0 |

### `SpecificationBuilder` (spring)

`static <T> Specification<T> toSpecification(SearchQuery query)` — dịch toàn bộ criteria thành một `Specification`, nối bằng `cb.and`/`cb.or` theo `combinator`; criteria rỗng → `cb.conjunction()` (match tất cả).

- **Path lồng nhau**: mỗi segment của `customer.name` resolve bằng `Path.get(String)` — hỗ trợ embedded attribute và quan hệ **to-one** (JPA provider tạo implicit join). **Không hỗ trợ quan hệ collection.** Field/attribute không tồn tại trên entity ném `SearchParseException` (`"Unknown field ... attribute ... does not exist"`).
- **Chuyển kiểu**: value String được chuyển sang đúng Java type của attribute (`Path.getJavaType()`). Các kiểu hỗ trợ: `String` (kèm `Object`, `char[]`), `UUID`, `Boolean`/`boolean` (chỉ nhận đúng `true`/`false` không phân biệt hoa thường), `Integer`, `Long`, `Short`, `Float`, `Double` (kèm primitive), `BigDecimal`, `BigInteger`, `LocalDate`, `LocalTime`, `LocalDateTime`, `Instant`, `OffsetDateTime` (tất cả ISO-8601) và **mọi enum** (đúng tên hằng, không khớp thì thử viết hoa toàn bộ). Chuyển kiểu thất bại → `SearchParseException` nêu field và kiểu mong đợi; kiểu khác → `SearchParseException` "unsupported type".
- **LIKE semantics**: `like`/`nlike` là *contains* không phân biệt hoa thường — `cb.like(cb.lower(path.as(String.class)), "%" + value.toLowerCase() + "%")`. Client không cần (và không nên) tự thêm `%`.
- **So sánh (`gt/gte/lt/lte/between`)**: giá trị sau chuyển kiểu phải là `Comparable`, nếu không ném `SearchParseException` báo toán tử không được hỗ trợ trên kiểu đó.
- **An toàn SQL**: mọi value đi qua Criteria API dưới dạng tham số bind — không bao giờ ghép vào chuỗi SQL.

### `PageRequests` (spring)

`static PageRequest of(SearchQuery query)` — map page/size/sorts thành Spring Data `PageRequest`; sorts rỗng → `Sort.unsorted()`.

### `SearchQueryArgumentResolver` (spring)

`HandlerMethodArgumentResolver` cho Spring MVC: hỗ trợ đúng tham số kiểu `SearchQuery`, parse `webRequest.getParameterMap()` bằng `SearchQueryParser` được inject qua constructor.

### Auto-configuration

| Class | Bean | Điều kiện |
|---|---|---|
| `SearchAutoConfiguration` | `searchQueryParser` (`SearchQueryParser` build từ `SearchProperties`) | `@ConditionalOnMissingBean` |
| `SearchWebMvcAutoConfiguration` (chạy sau `SearchAutoConfiguration`) | `searchWebMvcConfigurer` (`WebMvcConfigurer` add `SearchQueryArgumentResolver`) | servlet web app + có class `WebMvcConfigurer` |

## Cấu hình

Bind vào class `SearchProperties` (`javalibs.search.*`) — mỗi property map 1:1 sang `ParserConfig`:

| Thuộc tính | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `javalibs.search.filter-param` | String | `filter` | Tên tham số chứa biểu thức lọc |
| `javalibs.search.sort-param` | String | `sort` | Tên tham số chứa biểu thức sắp xếp |
| `javalibs.search.page-param` | String | `page` | Tên tham số chỉ số trang (tính từ 0) |
| `javalibs.search.size-param` | String | `size` | Tên tham số kích thước trang |
| `javalibs.search.default-page-size` | int | `20` | Kích thước trang khi client không truyền size |
| `javalibs.search.max-page-size` | int | `100` | Trần kích thước trang; size lớn hơn bị kẹp về giá trị này |
| `javalibs.search.default-combinator` | `Combinator` (`and`/`or`) | `and` | Cách nối các điều kiện khi không có tham số `combinator` |

## Hướng dẫn sử dụng

### Dependency

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-search-spring-boot-starter</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Starter kéo sẵn `spring-boot-starter-data-jpa`.

### `application.yml` (tùy chọn — mặc định đã dùng được ngay)

```yaml
javalibs:
  search:
    default-page-size: 20
    max-page-size: 100
    default-combinator: and
```

### Ví dụ hoàn chỉnh: controller nhận `SearchQuery` + repository `JpaSpecificationExecutor`

```java
// Entity
@Entity
public class Order {
    @Id @GeneratedValue
    private Long id;

    @Enumerated(EnumType.STRING)
    private Status status;          // enum NEW, OPEN, CLOSED

    private String customerName;
    private BigDecimal total;
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    private Customer customer;      // to-one: lọc được customer.name
    // getters/setters...
}

// Repository — bắt buộc mở rộng JpaSpecificationExecutor
public interface OrderRepository
        extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
}

// Controller — tham số SearchQuery được argument resolver tự parse từ query string
import io.javalibs.search.SearchQuery;
import io.javalibs.search.spring.PageRequests;
import io.javalibs.search.spring.SpecificationBuilder;

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

Gọi thử:

```text
GET /orders?filter=status:eq:OPEN&filter=total:gte:100&sort=createdAt,desc&page=0&size=20
GET /orders?filter=status:in:NEW,OPEN&filter=createdAt:between:2024-01-01T00:00:00Z,2024-12-31T23:59:59Z
GET /orders?filter=customer.name:like:nguyen&sort=total,desc
GET /orders?filter=customer:isnull
GET /orders?filter=status:eq:NEW&filter=total:gte:500&combinator=or
```

Kết hợp với [javalibs-web](web.md): map `Page` sang `PageResponse` để giữ hợp đồng JSON thống nhất:

```java
Page<Order> page = orders.findAll(SpecificationBuilder.toSpecification(q), PageRequests.of(q));
PageResponse<OrderDto> body = PageResponse.of(
        page.map(OrderDto::from).getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
```

## Ghi đè & mở rộng

- **`SearchQueryParser`**: bean autoconfigure là `@ConditionalOnMissingBean` — khai báo bean `SearchQueryParser` riêng (ví dụ với `ParserConfig` tùy biến) là autoconfigure nhường; argument resolver sẽ dùng bean của bạn (được inject vào `SearchWebMvcAutoConfiguration`).
- **Đổi tên tham số / trần page size**: chỉ cần property `javalibs.search.*`, không cần code.
- **Không dùng argument resolver**: có thể gọi parser trực tiếp — `parser.parse(request.getParameterMap()...)` hoặc tự dựng `SearchQuery`/`SearchCriterion` bằng constructor (validation chạy như nhau).
- **Giới hạn cột được lọc**: parser chỉ whitelist *cú pháp* tên field; nếu cần giới hạn theo danh sách cột nghiệp vụ, kiểm tra `query.criteria()`/`query.sorts()` trong service trước khi gọi `SpecificationBuilder`.

## Testing

- **Parser (core)**: unit test thuần — `new SearchQueryParser().parse(Map.of("filter", List.of("status:eq:OPEN")))`, assert trên `SearchQuery`; input lỗi assert `SearchParseException` (xem `SearchQueryParserTest`, `SearchCriterionTest`, `SearchOperatorTest`).
- **SpecificationBuilder**: test với `@DataJpaTest` + H2 và repository `JpaSpecificationExecutor` — đúng cách module tự test trong `SpecificationBuilderTest` (entity mẫu `SampleOrder`/`SampleCustomer`, seed bằng `TestEntityManager`).
- **Argument resolver**: `MockMvc` với controller khai báo tham số `SearchQuery` (xem `SearchQueryArgumentResolverTest`), hoặc gọi `resolveArgument` trực tiếp với `NativeWebRequest` giả.
- Auto-configuration test bằng `ApplicationContextRunner`/`WebApplicationContextRunner` (xem `SearchAutoConfigurationTest`, `SearchWebMvcAutoConfigurationTest`).

## Lưu ý & bẫy thường gặp

- **`SearchParseException` là `RuntimeException`** — nếu không xử lý sẽ rơi vào catch-all 500. Khi dùng cùng [javalibs-web](web.md), nên thêm một `@ExceptionHandler(SearchParseException.class)` (hoặc bọc trong `BusinessException`) để trả HTTP 400; message của exception an toàn để trả thẳng cho client.
- **Không lọc được qua collection**: `customer.name` (to-one) chạy; `items.productName` (`@OneToMany`) ném lỗi lúc resolve path. Cần thì viết `Specification` join riêng.
- **`size` không báo lỗi khi vượt trần** — bị kẹp im lặng về `max-page-size`; client truyền `size=1000` nhận tối đa 100 bản ghi/trang (mặc định).
- **`combinator` áp cho TOÀN BỘ criteria** — không có nhóm điều kiện hỗn hợp (a AND (b OR c)). Cần logic phức tạp hơn thì compose `Specification` thủ công.
- **`like` trên field không phải String**: builder gọi `path.as(String.class)` — hành vi cast tùy JPA provider/database; an toàn nhất chỉ dùng `like`/`nlike` cho cột String.
- **Boolean chỉ nhận `true`/`false`** (không nhận `1`/`0`/`yes`); enum thử đúng tên hằng rồi mới thử viết hoa (`open` → `OPEN`).
- **Ngày giờ phải đúng ISO-8601** theo kiểu của field: `LocalDate` = `2024-01-31`, `Instant` = `2024-01-31T00:00:00Z`. Sai format → 400 (nếu đã map `SearchParseException`).
- **Field name phân biệt hoa thường** theo tên thuộc tính Java của entity (`customerName`, không phải `customer_name`).
- **Bảo mật**: tên field whitelist bằng regex `^[a-zA-Z][a-zA-Z0-9_.]*$` (chặn `name;DROP TABLE`, `name OR 1=1` ngay từ parse); value luôn bind qua Criteria API nên không có SQL injection; field không tồn tại bị metamodel JPA chặn trước khi xuống SQL. Rủi ro còn lại là *data exposure qua filter/sort tùy ý* (đoán dữ liệu qua `like`, timing) — giới hạn cột lọc ở tầng service nếu dữ liệu nhạy cảm.
