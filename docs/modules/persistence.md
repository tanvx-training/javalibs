# javalibs-persistence

> Chuẩn hóa versioning schema database bằng Flyway: enforce các mặc định an toàn cho production (cấm `clean`, validate tên migration, lịch sử tuyến tính) trên nền auto-configuration Flyway chuẩn của Spring Boot.

## Artifacts

Module có **2 submodule** (không có core/spring — toàn bộ giá trị nằm ở auto-configuration):

| artifactId | Packaging | Mô tả |
|---|---|---|
| `javalibs-persistence` | pom | Parent gộp 2 submodule |
| `javalibs-persistence-spring-boot-autoconfigure` | jar | `JavalibsFlywayAutoConfiguration` + `PersistenceProperties` (namespace `javalibs.persistence.flyway.*`). `flyway-core` là *optional* — không có Flyway trên classpath thì tắt. Package `io.javalibs.persistence.autoconfigure` |
| `javalibs-persistence-spring-boot-starter` | jar | Starter: autoconfigure + **`flyway-core` + `flyway-database-postgresql`** (module dialect PostgreSQL bắt buộc từ Flyway 10) |

## Khi nào dùng / không dùng

**Dùng khi:**

- Service dùng PostgreSQL và quản lý schema bằng Flyway — thêm starter là có ngay Flyway chạy lúc khởi động với các guard production của platform, không cần cấu hình gì thêm ngoài `spring.flyway.*`/datasource chuẩn Boot.
- Muốn mọi service trong hệ thống bị **ép** cùng bộ an toàn: không `clean`, tên migration đúng chuẩn, không out-of-order.
- Cần tạo bảng cho [javalibs-datahub](datahub.md) outbox/idempotency — bằng migration, đúng quy ước ở đây.

**Không dùng khi:**

- Dự án dùng Liquibase hoặc quản lý schema bằng `ddl-auto` của Hibernate (không khuyến khích cho production) — module không liên quan.
- Database không phải PostgreSQL: chỉ cần bỏ starter, dùng trực tiếp `javalibs-persistence-spring-boot-autoconfigure` + `flyway-core` + module dialect tương ứng (`flyway-mysql`...) — phần default vẫn áp dụng.
- Môi trường throwaway thật sự cần `flyway clean` — có thể nới từng guard qua property, nhưng đừng làm mặc định.

## Các thành phần chính

### `JavalibsFlywayAutoConfiguration`

- `@AutoConfiguration(beforeName = "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration")` — chạy **trước** auto-configuration Flyway của Boot để customizer được đăng ký kịp lúc Boot dựng `Flyway`.
- `@ConditionalOnClass(Flyway.class)` — không có Flyway trên classpath thì im lặng tắt.
- `@ConditionalOnProperty(prefix = "javalibs.persistence.flyway", name = "enabled", havingValue = "true", matchIfMissing = true)` — bật sẵn, tắt bằng `enabled=false`.
- Bean duy nhất: `javalibsFlywayDefaultsCustomizer` (`FlywayConfigurationCustomizer`, `@ConditionalOnMissingBean(name = "javalibsFlywayDefaultsCustomizer")`) — được Boot gọi khi build cấu hình Flyway, áp 4 default:

```java
configuration -> configuration
    .cleanDisabled(properties.isCleanDisabled())              // true
    .validateMigrationNaming(properties.isValidateMigrationNaming()) // true
    .outOfOrder(properties.isOutOfOrder())                    // false
    .baselineOnMigrate(properties.isBaselineOnMigrate());     // false
```

### 4 mặc định và lý do

| Mặc định | Giá trị | Lý do |
|---|---|---|
| `clean-disabled` | `true` | `flyway clean` **drop toàn bộ object trong schema** — một lần lỡ tay trên production là mất sạch. Chỉ nới ở môi trường throwaway |
| `validate-migration-naming` | `true` | File trong thư mục migration không đúng format `V<version>__<description>.sql` sẽ **fail migration** thay vì bị Flyway âm thầm bỏ qua (bug kinh điển: migration "không chạy" vì thiếu một dấu `_`) |
| `out-of-order` | `false` | Từ chối migration có version nhỏ hơn version đã áp — giữ lịch sử tuyến tính, tránh drift giữa các môi trường |
| `baseline-on-migrate` | `false` | Baseline schema có sẵn (pre-Flyway) phải là quyết định chủ động, không tự động — tránh vô tình bỏ qua migration đầu trên DB không trống |

Vì đi qua `FlywayConfigurationCustomizer` của Boot, mọi thứ còn lại (locations, datasource, placeholders, schemas...) **vẫn là cấu hình Spring Boot chuẩn `spring.flyway.*`** — module không thay thế, chỉ xếp chồng default lên trên.

### `PersistenceProperties`

`@ConfigurationProperties(prefix = "javalibs.persistence.flyway")` — 5 property boolean, xem bảng Cấu hình.

### Tương tác với `spring.flyway.*`

- Boot vẫn là bên tạo bean `Flyway` và chạy migrate lúc khởi động; customizer của javalibs chỉ được gọi chen vào lúc build `FluentConfiguration`.
- Customizer chạy **sau** khi Boot áp `spring.flyway.*`, nên với 4 setting trên, giá trị `javalibs.persistence.flyway.*` là giá trị cuối cùng — muốn đổi 4 setting đó phải đổi qua namespace `javalibs.*` (đặt `spring.flyway.clean-disabled=false` sẽ bị customizer ghi đè lại thành `true`).
- Các setting khác (`spring.flyway.locations`, `spring.flyway.enabled`, `spring.flyway.placeholders.*`, `spring.flyway.default-schema`...) hoạt động bình thường theo tài liệu Boot.

## Cấu hình

Toàn bộ property bind vào `PersistenceProperties` (`javalibs.persistence.flyway.*`):

| Thuộc tính | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `javalibs.persistence.flyway.enabled` | boolean | `true` | Có áp bộ default Flyway của javalibs không (tắt = trở về hành vi Boot thuần) |
| `javalibs.persistence.flyway.clean-disabled` | boolean | `true` | Cấm `flyway clean` (guard production) |
| `javalibs.persistence.flyway.validate-migration-naming` | boolean | `true` | Fail khi file migration sai format `V<version>__<desc>.sql` |
| `javalibs.persistence.flyway.out-of-order` | boolean | `false` | Chấp nhận migration out-of-order (giữ `false` để lịch sử tuyến tính) |
| `javalibs.persistence.flyway.baseline-on-migrate` | boolean | `false` | Tự baseline schema có sẵn ở lần migrate đầu (opt-in cho DB legacy) |

## Hướng dẫn sử dụng

### Dependency

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-persistence-spring-boot-starter</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Kèm sẵn `flyway-core` + `flyway-database-postgresql`. Không cần bật gì thêm: có datasource là Flyway chạy migrate lúc khởi động (hành vi chuẩn Boot).

### `application.yml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://db.internal:5432/orders
    username: orders
    password: ${DB_PASSWORD}
  flyway:                       # cấu hình Flyway vẫn theo chuẩn Boot
    locations: classpath:db/migration   # (đây cũng là mặc định của Boot)

# chỉ khi cần nới guard — bình thường KHÔNG cần khai gì:
javalibs:
  persistence:
    flyway:
      baseline-on-migrate: true   # ví dụ: đưa Flyway vào DB legacy có sẵn schema
```

### Quy ước migration của platform

- Đường dẫn: `src/main/resources/db/migration/`.
- Tên file: **`V<yyyyMMddHHmm>__<mo_ta_ngan>.sql`** — ví dụ `V202607141030__create_orders_table.sql`. Dùng timestamp làm version để hai nhánh phát triển song song không tranh nhau số thứ tự.
- Mỗi thay đổi schema = một migration mới; **không bao giờ sửa migration đã merge** — Flyway lưu checksum, sửa file cũ làm validate fail trên mọi môi trường đã áp.
- Migration phải backward-compatible với version app đang chạy, theo chu trình **expand → migrate → contract**: (1) *expand* — thêm cột/bảng mới song song, app cũ vẫn chạy; (2) *migrate* — deploy app mới ghi/đọc cấu trúc mới, backfill dữ liệu; (3) *contract* — migration sau cùng xóa cấu trúc cũ khi không còn ai dùng.

### Tạo bảng outbox/idempotency cho javalibs-datahub

Bật outbox/idempotency của [javalibs-datahub](datahub.md) cần 2 bảng — thư viện **không tự chạy DDL**. Copy nội dung file `META-INF/datahub/outbox-schema-postgres.sql` (trong jar `javalibs-datahub-spring`) vào một migration:

```
src/main/resources/db/migration/V202607141100__datahub_outbox.sql
```

```sql
CREATE TABLE datahub_outbox_event (
    id             VARCHAR(64)  PRIMARY KEY,
    topic          VARCHAR(255) NOT NULL,
    event_type     VARCHAR(255) NOT NULL,
    source         VARCHAR(255),
    correlation_id VARCHAR(128),
    partition_key  VARCHAR(255),
    headers_json   TEXT,
    payload_json   TEXT         NOT NULL,
    payload_type   VARCHAR(512),
    status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempts       INT          NOT NULL DEFAULT 0,
    last_error     TEXT,
    created_at     TIMESTAMPTZ  NOT NULL,
    published_at   TIMESTAMPTZ
);

CREATE INDEX idx_datahub_outbox_pending
    ON datahub_outbox_event (status, created_at);

CREATE TABLE datahub_processed_event (
    handler      VARCHAR(255) NOT NULL,
    event_id     VARCHAR(64)  NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (handler, event_id)
);

CREATE INDEX idx_datahub_processed_cleanup
    ON datahub_processed_event (processed_at);
```

Sau đó bật `javalibs.datahub.outbox.enabled=true` / `javalibs.datahub.idempotency.enabled=true`.

## Ghi đè & mở rộng

- **Nới từng guard**: đặt property tương ứng, vd `javalibs.persistence.flyway.clean-disabled=false` cho môi trường dev throwaway, `baseline-on-migrate=true` khi onboard DB legacy — chỉ nên đặt trong profile của môi trường đó.
- **Tắt toàn bộ default**: `javalibs.persistence.flyway.enabled=false` — Flyway vẫn chạy nhưng theo hành vi Boot thuần.
- **Customizer riêng**: khai báo bean tên `javalibsFlywayDefaultsCustomizer` (kiểu `FlywayConfigurationCustomizer`) để thay hẳn bộ default (`@ConditionalOnMissingBean` theo tên). Muốn **bổ sung** cấu hình lập trình (callback, resolver...) thì thêm bean `FlywayConfigurationCustomizer` khác tên — Boot áp tất cả customizer.
- **Database khác PostgreSQL**: dùng artifact autoconfigure trực tiếp + `flyway-core` + module dialect của DB đó thay vì starter.
- **Tắt Flyway hẳn** (vd test slice): `spring.flyway.enabled=false` — chuẩn Boot, không liên quan module.

## Testing

- **Auto-configuration** (cách module tự test trong `JavalibsFlywayAutoConfigurationTest`): `ApplicationContextRunner` + `AutoConfigurations.of(JavalibsFlywayAutoConfiguration.class)`, lấy bean `FlywayConfigurationCustomizer`, gọi `customize(new FluentConfiguration())` rồi assert `isCleanDisabled()`, `isValidateMigrationNaming()`, `isOutOfOrder()`, `isBaselineOnMigrate()` — không cần database.
- Assert override: `withPropertyValues("javalibs.persistence.flyway.baseline-on-migrate=true", ...)`.
- Test migration thật: chạy trên PostgreSQL Testcontainers ([javalibs-test](test.md)) để bắt lỗi cú pháp/dialect sớm — H2 không thay thế được PostgreSQL cho DDL như `TIMESTAMPTZ`.

## Lưu ý & bẫy thường gặp

- **4 setting bị customizer "khóa"**: đặt `spring.flyway.clean-disabled` / `spring.flyway.validate-migration-naming` / `spring.flyway.out-of-order` / `spring.flyway.baseline-on-migrate` **không có tác dụng** khi module này bật — customizer chạy sau và ghi đè. Đổi chúng qua `javalibs.persistence.flyway.*`.
- **Sai tên file là fail ngay** (`validate-migration-naming=true`): để một file `.sql` rác/ghi chú trong `db/migration/` cũng làm app không khởi động — đó là chủ đích.
- **Không sửa migration đã merge**: checksum mismatch làm `validate` fail trên môi trường đã áp; cần sửa thì viết migration mới. (Trường hợp khẩn: `flyway repair` — quyết định vận hành có cân nhắc, xem tài liệu Flyway.)
- **`baseline-on-migrate` chỉ bật cho DB legacy** và tốt nhất theo profile; bật mặc định toàn hệ thống làm mất cảnh báo khi trỏ nhầm sang DB không trống.
- **Timestamp version phải tăng dần theo thời gian merge**: nhánh sống lâu có thể mang timestamp cũ hơn migration đã áp → bị từ chối vì `out-of-order=false`; đổi timestamp của migration đó thành thời điểm merge.
- **Starter đóng đinh PostgreSQL**: `flyway-database-postgresql` nằm sẵn trong starter; DB khác thì đừng dùng starter (xem Ghi đè & mở rộng).
- Migration của app là nơi tạo bảng cho các thư viện javalibs khác (outbox/idempotency của [javalibs-datahub](datahub.md)) — thư viện không bao giờ tự chạy DDL.
