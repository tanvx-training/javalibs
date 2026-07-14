# javalibs-internal

> Tiện ích thuần Java (zero framework dependency) dùng chung cho toàn bộ hệ sinh thái javalibs: xử lý chuỗi, kiểm tra tiền điều kiện, ngày giờ chuẩn ISO-8601/UTC và sinh định danh.

## Artifacts

| artifactId | Packaging | Mô tả |
|---|---|---|
| `javalibs-internal` | jar | Module đơn, chỉ chứa 4 lớp utility trong package `io.javalibs.internal.util`. Không kéo theo bất kỳ dependency runtime nào. |

## Khi nào dùng / không dùng

**Dùng khi:**
- Cần các helper nhỏ, ổn định (blank check, mask dữ liệu nhạy cảm, snake/camel case, UUID, ISO-8601) mà không muốn kéo cả `commons-lang3` hay Guava vào classpath.
- Viết code domain thuần Java (ví dụ với [javalibs-ddd](ddd.md)) và muốn giữ tầng domain sạch framework.

**Không dùng khi:**
- Cần bộ utility đầy đủ tính năng — module này **cố ý tối giản**, chỉ chứa những helper mà các module javalibs thực sự cần, không phải thư viện commons thay thế.
- Cần helper gắn với Spring (xem [javalibs-spring](spring.md)) hoặc dữ liệu test ngẫu nhiên (xem [javalibs-test](test.md)).

## Các thành phần chính

Tất cả đều là `final class` với constructor private — chỉ dùng qua static method.

### `io.javalibs.internal.util.Strings`

| Method | Hành vi |
|---|---|
| `boolean isBlank(String value)` | `true` khi `null`, rỗng hoặc chỉ chứa whitespace. |
| `boolean isNotBlank(String value)` | Phủ định của `isBlank`. |
| `String defaultIfBlank(String value, String defaultValue)` | Trả về `value` nếu không blank, ngược lại trả về `defaultValue`. |
| `String truncate(String value, int maxLength)` | Cắt còn tối đa `maxLength` ký tự. Trả về nguyên trạng nếu `null` hoặc đã đủ ngắn. Ném `IllegalArgumentException` nếu `maxLength < 0`. |
| `String toSnakeCase(String value)` | camelCase/PascalCase → lower snake_case (`"createdAt"` → `"created_at"`). Trả về nguyên trạng nếu blank. |
| `String toCamelCase(String value)` | snake_case hoặc kebab-case → camelCase (`"created_at"` → `"createdAt"`). |
| `String mask(String value, int visible)` | Che phần giữa, giữ `visible` ký tự mỗi đầu (`mask("0912345678", 2)` → `"09******78"`). Giá trị ngắn hơn `2 * visible + 1` bị che **toàn bộ** để không lộ secret ngắn. `null` trả về `null`; `visible < 0` ném `IllegalArgumentException`. |
| `String maskEmail(String email)` | Giữ ký tự đầu của local part và toàn bộ domain (`"john.doe@acme.com"` → `"j*******@acme.com"`). Chuỗi không phải email bị che toàn bộ. |
| `String lower(String value)` / `String upper(String value)` | Đổi hoa/thường null-safe, dùng `Locale.ROOT` nên ổn định trên mọi locale hệ thống (tránh bug chữ `i` tiếng Thổ). |

### `io.javalibs.internal.util.Checks`

Kiểm tra tiền điều kiện. Các method `not*` **trả về giá trị đã kiểm tra** nên inline được trong assignment/constructor. Tất cả ném `IllegalArgumentException`, riêng `state` ném `IllegalStateException`.

| Method | Hành vi |
|---|---|
| `<T> T notNull(T value, String name)` | Ném `IllegalArgumentException("<name> must not be null")` nếu `null`. |
| `String notBlank(String value, String name)` | Ném nếu `null`/blank, message `"<name> must not be blank"`. |
| `<C extends Collection<?>> C notEmpty(C collection, String name)` | Ném nếu `null` hoặc rỗng, message `"<name> must not be empty"`. |
| `<M extends Map<?, ?>> M notEmpty(M map, String name)` | Tương tự cho `Map`. |
| `void isTrue(boolean condition, String message)` | Ném `IllegalArgumentException(message)` nếu điều kiện sai (dùng cho tham số). |
| `void state(boolean condition, String message)` | Ném `IllegalStateException(message)` nếu điều kiện sai (dùng cho trạng thái nội bộ). |

### `io.javalibs.internal.util.Dates`

Chuẩn hóa ISO-8601 và UTC.

| Thành phần | Hành vi |
|---|---|
| `DateTimeFormatter ISO_MILLIS_UTC` (hằng số) | Pattern `yyyy-MM-dd'T'HH:mm:ss.SSSX`, zone UTC — ví dụ `2026-07-13T08:30:00.000Z`. |
| `Instant nowMillis()` | `Instant.now()` cắt về millisecond — round-trip JSON/database ổn định (tránh lệch nano giữa JVM và PostgreSQL). |
| `String formatIso(Instant instant)` | Format bằng `ISO_MILLIS_UTC`; `null` → `null`. |
| `Instant parseIso(String value)` | `Instant.parse(value.trim())`; blank → `null`. |
| `Instant toInstant(LocalDateTime dateTime, ZoneId zone)` | Quy đổi local date-time theo zone; `dateTime == null` → `null`, `zone == null` → `IllegalArgumentException`. |
| `LocalDate toLocalDate(Instant instant, ZoneId zone)` | Quy đổi ngược về ngày local; `instant == null` → `null`. |
| `long daysBetween(Instant from, Instant to)` | Số ngày nguyên giữa hai mốc (cắt phần dư, có thể âm). Cả hai tham số bắt buộc khác `null`. |

### `io.javalibs.internal.util.Ids`

| Method | Hành vi |
|---|---|
| `String uuid()` | UUID ngẫu nhiên dạng canonical 36 ký tự. |
| `String compactUuid()` | UUID không dấu gạch (32 ký tự hex) — gọn cho header, cache key. |
| `String shortId(int length)` | Chuỗi Base62 ngẫu nhiên độ dài `length`, sinh bằng `SecureRandom`. Ném `IllegalArgumentException` nếu `length <= 0`. Phù hợp cho mã tham chiếu human-facing, **không** thay thế UUID. |

## Cấu hình

Module **không có auto-configuration và không có thuộc tính cấu hình nào** — chỉ là static utility, thêm dependency là dùng được.

## Hướng dẫn sử dụng

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-internal</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

```java
import io.javalibs.internal.util.Checks;
import io.javalibs.internal.util.Dates;
import io.javalibs.internal.util.Ids;
import io.javalibs.internal.util.Strings;

public class Customer {

    private final String id;
    private final String email;
    private final java.time.Instant createdAt;

    public Customer(String email) {
        this.id = Ids.uuid();
        this.email = Checks.notBlank(email, "email");   // trả về value, inline được
        this.createdAt = Dates.nowMillis();
    }

    /** Log an toàn, không lộ PII. */
    @Override
    public String toString() {
        return "Customer[id=%s, email=%s, createdAt=%s]"
                .formatted(id, Strings.maskEmail(email), Dates.formatIso(createdAt));
    }
}
```

## Ghi đè & mở rộng

Không áp dụng — module không đăng ký bean nào, chỉ chứa static method. Không có gì để override.

## Testing

Utility thuần nên test bằng JUnit thường, không cần Spring context:

```java
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Test
void masksPhoneNumber() {
    assertThat(Strings.mask("0912345678", 2)).isEqualTo("09******78");
}

@Test
void rejectsBlankEmail() {
    assertThatThrownBy(() -> Checks.notBlank(" ", "email"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("email must not be blank");
}
```

## Lưu ý & bẫy thường gặp

- `Strings.mask` che **toàn bộ** chuỗi khi độ dài `< 2 * visible + 1` — đây là chủ đích chống lộ secret ngắn, đừng ngạc nhiên khi `mask("abc", 2)` trả về `"***"`.
- `Strings.toCamelCase` hạ thường mọi ký tự không nằm sau `_`/`-`, nên `toCamelCase("CreatedAt")` cho `"createdat"` — nó dành cho input snake/kebab, không phải để đổi PascalCase → camelCase.
- `Dates.parseIso` và `formatIso` là null-safe (trả về `null`), nhưng `daysBetween` thì **không** — cả hai tham số bắt buộc.
- `Dates.nowMillis()` cố ý cắt về millisecond; nếu bạn so sánh với `Instant.now()` gốc (độ chính xác nano) hai giá trị sẽ khác nhau.
- `Ids.shortId` dùng `SecureRandom` nên an toàn về mặt đoán trước, nhưng chuỗi ngắn thì xác suất trùng cao — chỉ dùng làm mã tham chiếu, kèm ràng buộc unique ở database nếu cần.
- `Checks.isTrue` ném `IllegalArgumentException` còn `Checks.state` ném `IllegalStateException` — chọn đúng method để tầng web (ví dụ [javalibs-web](web.md)) map lỗi chính xác 400 vs 500.
