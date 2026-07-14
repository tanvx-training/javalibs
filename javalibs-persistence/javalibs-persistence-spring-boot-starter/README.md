# javalibs-persistence-spring-boot-starter

Chuẩn hóa quản lý version database bằng **Flyway** với các mặc định an toàn cho production.

## Cài đặt

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-persistence-spring-boot-starter</artifactId>
</dependency>
```

Kèm sẵn `flyway-core` + `flyway-database-postgresql`. Cấu hình datasource/locations vẫn theo chuẩn Spring Boot (`spring.flyway.*`).

## Mặc định được enforce (ghi đè qua `javalibs.persistence.flyway.*`)

| Mặc định | Giá trị | Lý do |
|---|---|---|
| `clean-disabled` | `true` | Chặn `flyway:clean` xóa sạch schema production |
| `validate-migration-naming` | `true` | Bắt buộc đúng format tên file |
| `out-of-order` | `false` | Lịch sử migration tuyến tính |
| `baseline-on-migrate` | `false` | Baseline schema cũ phải là quyết định chủ động |

## Quy ước migration của platform

- Đường dẫn: `src/main/resources/db/migration/`
- Tên file: `V<yyyyMMddHHmm>__<mo_ta_ngan>.sql` — ví dụ `V202607141030__create_orders_table.sql` (timestamp làm version tránh xung đột giữa các nhánh)
- Mỗi thay đổi schema = một migration mới; **không bao giờ sửa** migration đã merge
- Migration phải backward-compatible với version app đang chạy (expand → migrate → contract)
- Bảng outbox/idempotency của `javalibs-datahub`: copy DDL từ `META-INF/datahub/outbox-schema-postgres.sql` vào một migration
