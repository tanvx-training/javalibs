# javalibs-search

Thư viện lọc động (dynamic filtering) cho các dịch vụ Spring: chuyển tham số HTTP
(`?filter=...&sort=...&page=...&size=...`) thành `SearchQuery` có kiểu, rồi thành
`Specification` của Spring Data JPA và `PageRequest` — không cần viết tay từng câu query
cho mỗi màn hình tìm kiếm.

## Kiến trúc submodule

| Module | Nội dung | Phụ thuộc chính |
|---|---|---|
| `javalibs-search-core` | Mô hình và parser thuần Java: `SearchQuery`, `SearchCriterion`, `SortSpec`, `SearchOperator`, `SearchQueryParser`, `SearchParseException`. **Không phụ thuộc Spring.** | Không có |
| `javalibs-search-spring` | `SpecificationBuilder` (SearchQuery → JPA `Specification`), `PageRequests` (SearchQuery → `PageRequest`), `SearchQueryArgumentResolver` (tùy chọn, cần Spring MVC). | core, spring-data-jpa, jakarta.persistence-api; spring-webmvc *(optional)* |
| `javalibs-search-spring-boot-autoconfigure` | `SearchProperties` (`javalibs.search.*`), `SearchAutoConfiguration` (bean `SearchQueryParser`), `SearchWebMvcAutoConfiguration` (đăng ký argument resolver cho ứng dụng web servlet). | spring, spring-boot-autoconfigure |
| `javalibs-search-spring-boot-starter` | Starter gộp: core + spring + autoconfigure + `spring-boot-starter-data-jpa`. Không chứa mã Java. | tất cả các module trên |

Ứng dụng Spring Boot chỉ cần thêm **starter**:

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-search-spring-boot-starter</artifactId>
</dependency>
```

## Cú pháp filter

Mỗi tham số `filter` có dạng `field:op:value` (toán tử `isnull`/`notnull` không có phần
value: `field:isnull`). Có thể lặp lại `filter` nhiều lần; mặc định các điều kiện được nối
bằng `AND` (đổi bằng `combinator=or`).

| Toán tử (token) | Ý nghĩa | Số value | Ví dụ |
|---|---|---|---|
| `eq` | bằng | 1 | `status:eq:OPEN` |
| `neq` | khác | 1 | `status:neq:CLOSED` |
| `gt` | lớn hơn | 1 | `total:gt:100` |
| `gte` | lớn hơn hoặc bằng | 1 | `total:gte:100` |
| `lt` | nhỏ hơn | 1 | `total:lt:100` |
| `lte` | nhỏ hơn hoặc bằng | 1 | `total:lte:100` |
| `like` | chứa chuỗi, không phân biệt hoa thường | 1 | `customerName:like:nguyen` |
| `nlike` | không chứa chuỗi | 1 | `customerName:nlike:test` |
| `in` | thuộc danh sách (phân tách bằng dấu phẩy) | ≥ 1 | `status:in:NEW,OPEN` |
| `nin` | không thuộc danh sách | ≥ 1 | `status:nin:CLOSED` |
| `between` | trong khoảng, bao gồm hai đầu | đúng 2 | `age:between:18,30` |
| `isnull` | giá trị là `null` | 0 | `customer:isnull` |
| `notnull` | giá trị khác `null` | 0 | `deletedAt:notnull` |

Ghi chú cú pháp:

- **Dấu phẩy trong value**: escape bằng `\,`, ví dụ `name:in:Nguyen\, Van A,Le` cho ra hai
  giá trị `Nguyen, Van A` và `Le`.
- **Dấu hai chấm trong value**: được giữ nguyên (chỉ tách tối đa 3 phần), nên
  `createdAt:gte:2024-01-01T10:15:30Z` hoạt động bình thường.
- **Field lồng nhau**: `customer.name` đi qua quan hệ to-one hoặc embedded; **không** hỗ
  trợ quan hệ dạng collection.
- **Sort**: `sort=field,asc|desc` hoặc chỉ `sort=field` (mặc định `asc`); lặp lại nhiều
  lần để sort theo nhiều cột.
- **Phân trang**: `page` tính từ 0 (giá trị âm bị đưa về 0); `size` bị kẹp (clamp) vào
  khoảng `[1, max-page-size]` thay vì báo lỗi.
- **Kiểu dữ liệu**: value được chuyển sang đúng kiểu Java của thuộc tính: `String`, `UUID`,
  `Boolean`, số (`Integer`, `Long`, `Short`, `Float`, `Double`, `BigDecimal`, `BigInteger`),
  ngày giờ ISO-8601 (`LocalDate`, `LocalTime`, `LocalDateTime`, `Instant`, `OffsetDateTime`)
  và mọi enum (đúng tên hằng, nếu không khớp sẽ thử viết hoa).
- Mọi input sai cú pháp đều ném `SearchParseException` với thông điệp mô tả chính xác lỗi
  — thích hợp để trả về HTTP 400.

## Cấu hình (`javalibs.search.*`)

| Property | Mặc định | Mô tả |
|---|---|---|
| `javalibs.search.filter-param` | `filter` | Tên tham số chứa biểu thức lọc |
| `javalibs.search.sort-param` | `sort` | Tên tham số chứa biểu thức sắp xếp |
| `javalibs.search.page-param` | `page` | Tên tham số chỉ số trang (tính từ 0) |
| `javalibs.search.size-param` | `size` | Tên tham số kích thước trang |
| `javalibs.search.default-page-size` | `20` | Kích thước trang khi client không truyền `size` |
| `javalibs.search.max-page-size` | `100` | Trần kích thước trang; `size` lớn hơn bị kẹp về giá trị này |
| `javalibs.search.default-combinator` | `and` | Cách nối các điều kiện khi không có tham số `combinator` (`and`/`or`) |

Định nghĩa bean `SearchQueryParser` riêng trong ứng dụng sẽ khiến auto-configuration
tự động nhường (back off).

## Lưu ý bảo mật

- **Whitelist tên field**: mọi tên field (filter lẫn sort) phải khớp regex
  `^[a-zA-Z][a-zA-Z0-9_.]*$`. Chuỗi kiểu `name;DROP TABLE users` hay `name OR 1=1` bị từ
  chối ngay từ bước parse với `SearchParseException`.
- **Không có SQL injection qua value**: giá trị lọc luôn đi qua Criteria API của JPA dưới
  dạng tham số bind, không bao giờ được ghép vào chuỗi SQL. Kể cả khi giá trị chứa ký tự
  đặc biệt, nó chỉ được so sánh như dữ liệu.
- **Field không tồn tại**: khi build `Specification`, field không có trong entity sẽ ném
  `SearchParseException` (metamodel của JPA kiểm tra), không rơi xuống tầng SQL.
- Nếu cần giới hạn chặt hơn (chỉ cho lọc trên một tập cột nhất định), hãy kiểm tra
  `SearchQuery.criteria()` trong tầng service trước khi gọi `SpecificationBuilder`.

Xem README của `javalibs-search-spring-boot-starter` để có ví dụ controller/repository
hoàn chỉnh.
