# javalibs-test

> Bộ hỗ trợ test dùng chung: base class integration test với PostgreSQL Testcontainers (singleton container), factory dữ liệu test ngẫu nhiên và assertion JSON — service tiêu thụ artifact này với `scope=test`.

## Artifacts

| artifactId | Packaging | Mô tả |
|---|---|---|
| `javalibs-test` | jar | Module đơn, 4 lớp trong package `io.javalibs.test`. Các dependency bên trong (spring-boot-starter-test, spring-boot-testcontainers, testcontainers `junit-jupiter` + `postgresql`, jackson-databind + jackson-datatype-jsr310) **cố ý để scope compile** — consumer chỉ cần khai báo một dependency `scope=test` là kéo đủ cả bộ test classpath. |

## Khi nào dùng / không dùng

**Dùng khi:**
- Viết integration test full-context cho service Spring Boot dùng PostgreSQL — kế thừa `BaseIntegrationTest` là có sẵn app chạy random port, profile `test` và database thật, cách ly theo build.
- Cần dữ liệu test ngẫu nhiên (`TestData`) hoặc so sánh JSON không phụ thuộc thứ tự key (`Jsons`).

**Không dùng khi:**
- Unit test thuần — đừng kéo Testcontainers vào; test domain logic (ví dụ aggregate của [javalibs-ddd](ddd.md)) bằng JUnit thường.
- Service không dùng PostgreSQL — `PostgresContainerSupport` khởi động container Postgres ngay khi class được load; nếu chỉ cần `TestData`/`Jsons` thì dùng trực tiếp hai lớp đó, **không** kế thừa base class.
- Môi trường CI không có Docker daemon — mọi test kế thừa `PostgresContainerSupport` sẽ fail (xem [Lưu ý](#lưu-ý--bẫy-thường-gặp)).

## Các thành phần chính

### `io.javalibs.test.BaseIntegrationTest`

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest extends PostgresContainerSupport {
}
```

Base class cho integration test full-context: boot ứng dụng trên **random port**, kích hoạt profile `test` (khớp quy ước `Profiles.TEST` của [javalibs-spring](spring.md)) và thừa hưởng database PostgreSQL cách ly từ `PostgresContainerSupport`. Không có method nào — chỉ là tổ hợp annotation + container.

### `io.javalibs.test.PostgresContainerSupport`

Cung cấp **một** container PostgreSQL chia sẻ cho cả JVM test và nối cấu hình kết nối vào Spring context. **Yêu cầu Docker daemon đang chạy.**

| Thành phần | Chi tiết |
|---|---|
| `public static final String IMAGE` | `"postgres:16-alpine"`. |
| Static initializer | Tạo `PostgreSQLContainer` với database `it_db`, user `it_user`, password `it_pass` rồi `start()` ngay khi class load — **singleton container pattern**: khởi động một lần, mọi test class kế thừa dùng chung, không stop thủ công; Ryuk (resource reaper của Testcontainers) dọn container khi JVM thoát. Suite chạy nhanh vì không tốn một container mỗi class. |
| `@DynamicPropertySource static void registerDatasourceProperties(DynamicPropertyRegistry registry)` | Đăng ký `spring.datasource.url`, `spring.datasource.username`, `spring.datasource.password` trỏ vào container. Vì `@DynamicPropertySource` chạy **trước khi** Spring context được tạo và supplier lấy giá trị lazy từ container đang chạy, ứng dụng kết nối đúng port ngẫu nhiên mà Docker cấp. Cùng một bộ property cho mọi test class nên Spring test context cache vẫn hoạt động (không phải boot lại context mỗi class). |
| `protected static PostgreSQLContainer<?> postgres()` | Truy cập trực tiếp container — ví dụ chạy SQL fixture (`postgres().getJdbcUrl()`, `execInContainer(...)`). |

### `io.javalibs.test.TestData`

Factory dữ liệu ngẫu nhiên (dùng `ThreadLocalRandom`). Giá trị **khác nhau mỗi lần gọi** — chỉ assert hành vi, đừng assert giá trị cụ thể.

| Method | Hành vi |
|---|---|
| `String string(int length)` | Chuỗi alphanumeric ngẫu nhiên độ dài `length`. |
| `String email()` | Email unique dạng `user-<10 ký tự>@example.com` (domain example.com được reserve, không gửi nhầm mail thật). |
| `String fullName()` | Họ tên hợp lý ngẫu nhiên (họ + tên từ danh sách tiếng Việt không dấu). |
| `int intBetween(int minInclusive, int maxExclusive)` | Số nguyên trong `[min, max)`. |
| `String uuid()` | UUID ngẫu nhiên dạng chuỗi. |
| `Instant instantInPast(Duration window)` | Mốc thời gian ngẫu nhiên trong quá khứ, cách hiện tại tối đa `window`, độ chính xác millisecond. |

### `io.javalibs.test.Jsons`

Helper JSON cho test, dùng một `ObjectMapper` chia sẻ đã đăng ký `JavaTimeModule` và tắt `WRITE_DATES_AS_TIMESTAMPS` (java.time serialize thành chuỗi ISO-8601).

| Method | Hành vi |
|---|---|
| `ObjectMapper mapper()` | Mapper chia sẻ, cấu hình sẵn. |
| `String toJson(Object value)` | Serialize; lỗi bọc thành `AssertionError` (test fail rõ ràng thay vì checked exception). |
| `<T> T fromJson(String json, Class<T> type)` | Deserialize; lỗi bọc thành `AssertionError`. |
| `void assertEquivalent(String expectedJson, String actualJson)` | So sánh **cấu trúc** hai document JSON (bỏ qua thứ tự key và whitespace, so sánh bằng `JsonNode.equals`). Khác nhau → `AssertionError` in cả hai document; JSON không hợp lệ cũng ném `AssertionError` nêu rõ bên nào lỗi. |

## Cấu hình

Module **không có auto-configuration và không có thuộc tính `javalibs.*` nào**. Cấu hình duy nhất nó tác động là 3 property `spring.datasource.*` được bơm qua `@DynamicPropertySource`.

## Hướng dẫn sử dụng

Khai báo dependency **với scope test**:

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-test</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <scope>test</scope>
</dependency>
```

Integration test điển hình:

```java
import io.javalibs.test.BaseIntegrationTest;
import io.javalibs.test.Jsons;
import io.javalibs.test.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class OrderApiIT extends BaseIntegrationTest {

    @Autowired
    TestRestTemplate rest;   // có sẵn nhờ webEnvironment = RANDOM_PORT

    @Test
    void createsOrder() {
        var request = new CreateOrderRequest(TestData.email(), TestData.intBetween(1, 5));

        ResponseEntity<String> response = rest.postForEntity("/api/orders", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Jsons.assertEquivalent("""
                {"status": "NEW", "customerEmail": "%s"}
                """.formatted(request.customerEmail()), response.getBody());
    }
}
```

Chạy SQL fixture trực tiếp trên container chia sẻ:

```java
@BeforeEach
void seed() throws Exception {
    postgres().execInContainer("psql", "-U", "it_user", "-d", "it_db",
            "-c", "TRUNCATE TABLE orders CASCADE");
}
```

### Kết hợp với `javalibs-security-test` cho API cần xác thực

Service dùng [javalibs-security](security.md) (JWT mode) có thể mint token test bằng `JwtTestFactory` (package `io.javalibs.security.test`, artifact `javalibs-security-test`, cũng `scope=test`):

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-security-test</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <scope>test</scope>
</dependency>
```

Trỏ secret của service dưới profile `test` về secret mặc định của factory — `JwtTestFactory.DEFAULT_TEST_SECRET` có giá trị `javalibs-test-secret-key-0123456789abcdef` — trong `src/test/resources/application-test.yml` (profile `test` đã được `BaseIntegrationTest` kích hoạt sẵn):

```yaml
javalibs:
  security:
    jwt:
      secret: javalibs-test-secret-key-0123456789abcdef   # = JwtTestFactory.DEFAULT_TEST_SECRET
```

Token mint ra sẽ validate thành công ngay vì claim convention (`roles`, `preferred_username`, `email`, `tenant`) khớp với security core:

```java
import io.javalibs.security.test.JwtTestFactory;
import org.springframework.http.*;

class AdminApiIT extends BaseIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void adminCanListUsers() {
        String bearer = JwtTestFactory.create()
                .subject("admin-1")
                .username("admin")
                .roles("ADMIN")
                .bearer();                       // "Bearer <token>"

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, bearer);

        ResponseEntity<String> response = rest.exchange(
                "/api/admin/users", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
```

Muốn test token hết hạn: `JwtTestFactory.create().issuedAt(Instant.now().minus(Duration.ofHours(2))).expiresIn(Duration.ofHours(1))` — expiration luôn là `issuedAt + expiresIn`.

## Ghi đè & mở rộng

Module không đăng ký bean nên không có `@ConditionalOnMissingBean`. Điểm mở rộng là **kế thừa**:

- Không muốn full stack HTTP? Bỏ qua `BaseIntegrationTest`, kế thừa thẳng `PostgresContainerSupport` và tự đặt `@SpringBootTest`/`@DataJpaTest` + `@ActiveProfiles` theo ý mình — phần wiring datasource vẫn được thừa hưởng.
- Cần image/database khác (phiên bản Postgres khác, thêm container Redis/Kafka)? Viết support class riêng theo đúng singleton pattern của `PostgresContainerSupport` (static field + start trong static initializer + `@DynamicPropertySource`); các hằng số của lớp gốc không đổi được vì container khởi tạo cứng trong static block.
- `Jsons.mapper()` là mapper chia sẻ — cần cấu hình khác thì tạo `ObjectMapper` riêng trong test, **đừng** mutate mapper chung.

## Testing

Đây chính là module test — nó được kiểm chứng bằng unit test của chính nó (`javalibs-test/src/test/java/io/javalibs/test/JsonsAndTestDataTest.java`). Khi dùng trong service:

- Chạy `mvn verify` với Docker daemon đang chạy; lần chạy đầu sẽ pull image `postgres:16-alpine`.
- Đặt integration test theo hậu tố quy ước (`*IT` cho failsafe hoặc `*Test` cho surefire tùy cấu hình build của service).

## Lưu ý & bẫy thường gặp

- **Bắt buộc có Docker daemon** (Docker Desktop, Colima, …). Không có Docker thì mọi test kế thừa `PostgresContainerSupport` fail ngay khi load class (exception từ static initializer của Testcontainers). CI phải cấp Docker (hoặc Docker-in-Docker/socket mount).
- **Container là singleton theo JVM, dữ liệu KHÔNG tự dọn giữa các test** — schema và data tồn tại xuyên suốt suite. Tự dọn bằng `@Sql` hoặc truncate trong `@BeforeEach`; đừng giả định database trống. Lưu ý: `@Transactional` trên test **không** rollback được thay đổi do request HTTP qua `TestRestTemplate` gây ra (request chạy transaction riêng phía server).
- Container khởi động **ngay khi class được load** — chỉ cần một test kế thừa base class trong JVM là trả chi phí start container, kể cả khi test đó bị `@Disabled`. Fork nhiều JVM (surefire `forkCount > 1`) → mỗi fork một container riêng.
- `@DynamicPropertySource` chỉ set `spring.datasource.*` — service dùng R2DBC, hay property datasource đặt tên khác, phải tự đăng ký thêm trong lớp con (method `@DynamicPropertySource` riêng, đọc từ `postgres()`).
- Container **không bị stop trong code** — đây là chủ đích; Ryuk dọn sau khi JVM thoát. Tắt Ryuk (`TESTCONTAINERS_RYUK_DISABLED=true`) trên CI thì phải tự dọn container mồ côi.
- `TestData` là ngẫu nhiên thật (không seed) — test phải chịu được mọi giá trị sinh ra; assert theo hành vi, không theo nội dung cụ thể.
- `Jsons.assertEquivalent` so sánh **toàn bộ** document (equality của `JsonNode`) — không phải "subset match"; response thừa field sẽ fail. So sánh một phần thì parse bằng `Jsons.mapper().readTree(...)` và assert từng node.
- Profile `test` được kích hoạt sẵn — đặt cấu hình test vào `application-test.yml`; đừng vô tình kích hoạt profile khác đè lên (xem quy ước profile trong [javalibs-spring](spring.md)).
