# javalibs-cache

Chuẩn hóa caching cho toàn hệ thống: format key thống nhất, serialize JSON an toàn, TTL linh hoạt theo tên cache, tự động chọn provider (Redis ↔ Caffeine).

## Kiến trúc submodule

| Submodule | Vai trò |
|---|---|
| `javalibs-cache-core` | Java thuần: `CacheKeys` (quy ước key), `CacheSpec` (ttl/max-size) |
| `javalibs-cache-spring` | `RedisCacheConfigurations` (JSON + prefix + TTL), `CaffeineCaches` |
| `javalibs-cache-spring-boot-autoconfigure` | Chọn `CacheManager`: Redis khi có `RedisConnectionFactory`, ngược lại Caffeine; chỉ kích hoạt khi app có `@EnableCaching` |
| `javalibs-cache-spring-boot-starter` | Điểm chạm của client (kèm Caffeine; thêm starter-data-redis để chuyển sang Redis) |

## Thuộc tính cấu hình

| Thuộc tính | Mặc định | Ý nghĩa |
|---|---|---|
| `javalibs.cache.enabled` | `true` | Bật/tắt toàn bộ auto-configuration |
| `javalibs.cache.provider` | `auto` | `auto` (ưu tiên Redis) / `redis` / `caffeine` |
| `javalibs.cache.key-prefix` | `spring.application.name` | Prefix key Redis |
| `javalibs.cache.default-ttl` | `10m` | TTL mặc định |
| `javalibs.cache.cache-null-values` | `false` | Có cache giá trị null không |
| `javalibs.cache.caches.<tên>.ttl` | — | TTL riêng cho cache `<tên>` |
| `javalibs.cache.caches.<tên>.max-size` | — | Giới hạn entry (Caffeine) |

Mọi bean đều `@ConditionalOnMissingBean` — service tự định nghĩa `CacheManager` thì thư viện tự lùi.
