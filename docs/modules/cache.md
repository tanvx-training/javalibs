# javalibs-cache

> Chuẩn hóa caching cho toàn platform: quy ước key thống nhất (`<prefix>::<cacheName>::<key>`), serialize JSON, TTL/size theo từng cache, và tự động chọn provider — Redis khi có kết nối, Caffeine in-memory làm fallback.

## Artifacts

| artifactId | Packaging | Mô tả |
|---|---|---|
| `javalibs-cache` | pom | Parent gộp 4 submodule |
| `javalibs-cache-core` | jar | Thuần Java, zero dependency runtime: `CacheKeys` (quy ước key), `CacheSpec` (tuning ttl/max-size). Package `io.javalibs.cache` |
| `javalibs-cache-spring` | jar | Building block theo provider: `RedisCacheConfigurations` (JSON + prefix + TTL), `CaffeineCaches`. spring-data-redis / caffeine / jackson đều *optional*. Package `io.javalibs.cache.spring` |
| `javalibs-cache-spring-boot-autoconfigure` | jar | `JavalibsRedisCacheAutoConfiguration`, `JavalibsCaffeineCacheAutoConfiguration`, `ProviderConditions` + `JavalibsCacheProperties` (namespace `javalibs.cache.*`). Package `io.javalibs.cache.autoconfigure` |
| `javalibs-cache-spring-boot-starter` | jar | Starter: core + spring + autoconfigure + `spring-boot-starter-cache` + **caffeine** + jackson-databind + jackson-datatype-jsr310. **Không kèm `spring-boot-starter-data-redis`** — ứng dụng muốn Redis phải tự thêm |

## Khi nào dùng / không dùng

**Dùng khi:**

- Dùng Spring Cache abstraction (`@Cacheable`/`@CacheEvict`) và muốn key Redis, serializer, TTL thống nhất giữa các service thay vì mỗi service một kiểu.
- Muốn dev/local chạy in-memory (Caffeine) còn production chạy Redis **mà không đổi code** — chỉ khác dependency/cấu hình.
- Cần TTL/max-size riêng cho từng cache khai báo bằng property, không viết `CacheManager` thủ công.

**Không dùng khi:**

- Cần thao tác Redis trực tiếp (data structure, pub/sub, lock phân tán) — đó là việc của `RedisTemplate`, module này chỉ lo tầng cache abstraction.
- Cache phải chia sẻ giữa các service **không dùng chung class DTO** — serializer nhúng `@class` (xem bẫy bên dưới) khiến cache chỉ đọc được bởi JVM có cùng class.
- Cần near-cache 2 tầng (Caffeine trước Redis) — module chọn **một** provider, không xếp tầng.

## Các thành phần chính

### `CacheKeys` (`javalibs-cache-core`)

Quy ước key toàn platform. Key Redis đầy đủ có dạng `<app-prefix>::<cacheName>::<part1>:<part2>:...` — prefix ứng dụng và tên cache do cấu hình javalibs tự gắn; code nghiệp vụ **chỉ build phần key cuối** bằng `join`:

- `public static final String SECTION_SEPARATOR = "::"` — ngăn cách prefix / cacheName / key.
- `public static final char PART_SEPARATOR = ':'` — ngăn cách các phần trong key.
- `static String join(String... parts)` — nối các phần bằng `':'`, vd `join("by-customer", "42")` → `"by-customer:42"`. Validation (vi phạm ném `IllegalArgumentException`):
  - phải có ít nhất 1 phần;
  - mỗi phần không được `null`/blank;
  - không chứa whitespace hoặc ký tự điều khiển;
  - không chứa separator dành riêng `"::"`.

### `CacheSpec` (record, `javalibs-cache-core`)

Tuning theo cache, độc lập provider: `record CacheSpec(Duration ttl, Long maxSize)`.

- `ttl` `null` = dùng TTL mặc định; khác `null` phải dương (0/âm → `IllegalArgumentException`).
- `maxSize` `null` = không giới hạn; khác `null` phải dương. Chỉ provider in-memory (Caffeine) tôn trọng `maxSize`.
- `static CacheSpec ofTtl(Duration ttl)` — spec chỉ có TTL.

### `RedisCacheConfigurations` (`javalibs-cache-spring`)

Factory cho `RedisCacheConfiguration` chuẩn javalibs:

- `static RedisCacheConfiguration standard(String keyPrefix, Duration ttl, boolean cacheNullValues)` — key String với `prefixCacheNameWith(keyPrefix + "::")` ⇒ **key đầy đủ `<prefix>::<cacheName>::<key>`**, value JSON, `entryTtl(ttl)`; `cacheNullValues=false` thì `disableCachingNullValues()`.
- `static GenericJackson2JsonRedisSerializer jsonSerializer()` — `ObjectMapper` riêng với `JavaTimeModule`, tắt `WRITE_DATES_AS_TIMESTAMPS` (date dạng ISO), đăng ký null-value serializer, và `activateDefaultTyping(LaissezFaireSubTypeValidator, DefaultTyping.EVERYTHING, As.PROPERTY)`.

**Trade-off serialize (quan trọng)**: default typing nhúng **tên class Java vào JSON qua property `@class`** để deserialize không cần cấu hình type cho từng cache (record/final class round-trip được). Hệ quả:

1. **Đổi tên/di chuyển class DTO đã cache ⇒ entry cũ không deserialize được** — phải **flush các cache liên quan khi refactor** kiểu được cache.
2. Cache **không được chia sẻ** giữa các service không dùng chung class DTO.
3. Chỉ nên cache DTO nhỏ chuyên dụng, không cache entity JPA.

### `CaffeineCaches` (`javalibs-cache-spring`)

`static Caffeine<Object, Object> builder(CacheSpec spec, Duration defaultTtl)` — dựng builder Caffeine từ spec: TTL của spec (fallback `defaultTtl` khi spec không có; cả hai `null` thì không expire) áp thành `expireAfterWrite`; `maxSize` (nếu có) thành `maximumSize`. `spec = null` hợp lệ (chỉ dùng `defaultTtl`).

### Auto-configuration (`javalibs-cache-spring-boot-autoconfigure`)

Hai auto-configuration trong `AutoConfiguration.imports`, cả hai chạy **trước** `CacheAutoConfiguration` của Boot:

| Class | Bean | Điều kiện |
|---|---|---|
| `JavalibsRedisCacheAutoConfiguration` | `javalibsRedisCacheManager` (`RedisCacheManager`) | `@ConditionalOnClass(RedisConnectionFactory, RedisCacheManager)`; `@ConditionalOnBean(CacheAspectSupport)`; `javalibs.cache.enabled` (mặc định `true`); `@Conditional(ProviderConditions.RedisAllowed)`; bean cần `@ConditionalOnBean(RedisConnectionFactory)` + `@ConditionalOnMissingBean(CacheManager)` |
| `JavalibsCaffeineCacheAutoConfiguration` | `javalibsCaffeineCacheManager` (`CaffeineCacheManager`) | `after = JavalibsRedisCacheAutoConfiguration`; `@ConditionalOnClass(Caffeine, CaffeineCacheManager)`; `@ConditionalOnBean(CacheAspectSupport)`; `javalibs.cache.enabled`; `@Conditional(ProviderConditions.CaffeineAllowed)`; bean `@ConditionalOnMissingBean(CacheManager)` |

Điểm mấu chốt:

- **Gate `@EnableCaching`**: cả hai đều `@ConditionalOnBean(CacheAspectSupport.class)` — bean này chỉ tồn tại khi ứng dụng khai `@EnableCaching`. Chưa bật caching thì thư viện không tạo `CacheManager` nào (giống hành vi `CacheAutoConfiguration` của Boot).
- **Redis thắng trong AUTO**: Redis config chạy trước; khi có bean `RedisConnectionFactory` (Boot tự tạo khi thêm `spring-boot-starter-data-redis`), `RedisCacheManager` được đăng ký trước và guard `@ConditionalOnMissingBean(CacheManager)` khiến Caffeine lùi.
- **`RedisCacheManager`** dựng bằng `RedisCacheConfigurations.standard` làm default (prefix = `key-prefix`, fallback `spring.application.name`, fallback cuối `"app"`), và mỗi entry trong `javalibs.cache.caches` thành một `RedisCacheConfiguration` riêng với TTL của nó (fallback `default-ttl`). `max-size` không áp dụng cho Redis.
- **`CaffeineCacheManager`**: builder mặc định cho cache tạo on-demand (chỉ `default-ttl`), còn mỗi entry trong `caches` được `registerCustomCache` với TTL/max-size riêng.

### `ProviderConditions` (`javalibs-cache-spring-boot-autoconfigure`)

Đọc property `javalibs.cache.provider` **trực tiếp từ Environment** (mặc định `"auto"`, trim + không phân biệt hoa thường):

- `RedisAllowed` khớp khi provider là `auto` hoặc `redis`.
- `CaffeineAllowed` khớp khi provider là `auto` hoặc `caffeine`.

Ngữ nghĩa 3 giá trị của `provider`:

| Giá trị | Hành vi |
|---|---|
| `AUTO` (mặc định) | Redis khi có bean `RedisConnectionFactory`, ngược lại Caffeine |
| `REDIS` | Chỉ Redis; **không có Redis thì không `CacheManager` nào được tạo** — `@EnableCaching` sẽ làm context fail lúc refresh (guard của chính Spring, giúp lỗi lộ sớm thay vì âm thầm chạy in-memory) |
| `CAFFEINE` | Ép in-memory Caffeine, kể cả khi Redis có trên classpath và có connection factory |

## Cấu hình

Toàn bộ property bind vào `JavalibsCacheProperties` (`javalibs.cache.*`):

| Thuộc tính | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `javalibs.cache.enabled` | boolean | `true` | Bật/tắt toàn bộ auto-configuration cache của javalibs |
| `javalibs.cache.provider` | enum `AUTO`/`REDIS`/`CAFFEINE` | `AUTO` | Chiến lược chọn provider (xem bảng ngữ nghĩa trên) |
| `javalibs.cache.key-prefix` | String | *(null → `spring.application.name` → `"app"`)* | Prefix key mức ứng dụng; key đầy đủ `<key-prefix>::<cacheName>::<key>` |
| `javalibs.cache.default-ttl` | Duration | `10m` | TTL cho cache không có entry riêng trong `caches` |
| `javalibs.cache.cache-null-values` | boolean | `false` | Có cache giá trị `null` không (thường tắt để không cache miss) |
| `javalibs.cache.caches.<tên>.ttl` | Duration | *(fallback `default-ttl`)* | TTL riêng cho cache `<tên>` |
| `javalibs.cache.caches.<tên>.max-size` | Long | *(không giới hạn)* | Số entry tối đa — **chỉ Caffeine tôn trọng** |

## Hướng dẫn sử dụng

### Dependency

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-cache-spring-boot-starter</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>

<!-- Muốn dùng Redis? Ứng dụng tự thêm (starter KHÔNG kèm): -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

Và **bắt buộc** bật caching:

```java
@SpringBootApplication
@EnableCaching   // thiếu annotation này thì thư viện không tạo CacheManager nào
public class OrderServiceApplication { }
```

### Ví dụ hoàn chỉnh: `@Cacheable` + `CacheKeys.join` + TTL theo cache

`application.yml`:

```yaml
spring:
  application:
    name: orders-service     # trở thành key prefix mặc định
  data:
    redis:
      host: redis.internal   # có RedisConnectionFactory → AUTO chọn Redis

javalibs:
  cache:
    provider: auto
    default-ttl: 10m
    caches:
      orders:
        ttl: 30s             # TTL riêng
      customers:
        ttl: 1h
        max-size: 5000       # chỉ có tác dụng khi provider là Caffeine
```

Code:

```java
@Service
public class OrderQueryService {

  @Cacheable(cacheNames = "orders",
             key = "T(io.javalibs.cache.CacheKeys).join('by-customer', #customerId)")
  public List<OrderDto> findByCustomer(String customerId) {
    // ... truy vấn DB ...
  }

  @CacheEvict(cacheNames = "orders",
              key = "T(io.javalibs.cache.CacheKeys).join('by-customer', #customerId)")
  public void invalidateCustomerOrders(String customerId) {
  }
}
```

Key Redis thực tế: `orders-service::orders::by-customer:42` — TTL 30s theo cấu hình cache `orders`.

## Ghi đè & mở rộng

- **`CacheManager` riêng**: khai báo bean `CacheManager` bất kỳ — cả hai auto-config đều `@ConditionalOnMissingBean(CacheManager)` nên tự lùi.
- **Tắt hoàn toàn**: `javalibs.cache.enabled=false` — lưu ý nếu vẫn giữ `@EnableCaching` mà không tự cung cấp `CacheManager` (Boot cũng không tạo được), context sẽ fail lúc refresh.
- **Ép provider**: `provider: caffeine` để chạy in-memory kể cả khi Redis sẵn sàng (vd môi trường test); `provider: redis` để fail-fast khi thiếu Redis thay vì âm thầm rơi về in-memory.
- **Tái dùng building block**: tự dựng `RedisCacheManager` với cấu hình khác nhưng vẫn theo chuẩn key/JSON bằng `RedisCacheConfigurations.standard(prefix, ttl, cacheNullValues)` hoặc chỉ lấy `RedisCacheConfigurations.jsonSerializer()`; tương tự `CaffeineCaches.builder(spec, defaultTtl)`.
- **Prefix riêng theo môi trường**: đặt `javalibs.cache.key-prefix` (vd `orders-service-staging`) để tách namespace khi nhiều môi trường chung một Redis.

## Testing

- **Auto-configuration** (cách module tự test trong `JavalibsCacheAutoConfigurationTest`): `ApplicationContextRunner` + `AutoConfigurations.of(JavalibsRedisCacheAutoConfiguration, JavalibsCaffeineCacheAutoConfiguration)`, thêm class `@Configuration @EnableCaching` để có `CacheAspectSupport`; Redis giả lập bằng `Mockito.mock(RedisConnectionFactory.class)` — đủ để assert `RedisCacheManager` được chọn, key prefix (`getKeyPrefixFor`) và TTL function, không cần Redis thật.
- **Assert tuning Caffeine**: lấy native cache qua `CaffeineCache.getNativeCache()` rồi kiểm tra `policy().expireAfterWrite()` / `policy().eviction()`.
- **`CacheKeys`/`CacheSpec`** là Java thuần — unit test trực tiếp (xem `CacheKeysTest`).
- Test tích hợp với Redis thật: dùng Testcontainers từ [javalibs-test](test.md).

## Lưu ý & bẫy thường gặp

- **Quên `@EnableCaching` = không có gì chạy**: `@Cacheable` bị bỏ qua âm thầm và thư viện cũng không tạo `CacheManager` (gate `CacheAspectSupport`). Đây là bẫy phổ biến nhất.
- **`@class` trong JSON**: refactor (đổi tên/di chuyển package) DTO đã cache ⇒ entry cũ ném lỗi deserialize khi đọc. **Flush cache khi deploy bản refactor DTO** (hoặc đổi `key-prefix`/tên cache để tách namespace). Không cache entity JPA (lazy proxy, đồ thị lớn, class hay đổi).
- **`max-size` chỉ áp dụng Caffeine**: chuyển từ Caffeine sang Redis thì giới hạn size biến mất — kiểm soát bộ nhớ Redis bằng TTL/eviction policy phía Redis.
- **AUTO chọn theo bean, không theo classpath**: có `spring-boot-starter-data-redis` nhưng connection factory bị tắt/không tạo được bean thì vẫn rơi về Caffeine. Muốn chắc chắn Redis, đặt `provider: redis` để fail-fast.
- **`provider: redis` thiếu Redis → context fail** lúc refresh (do `@EnableCaching` không tìm được `CacheManager`) — chủ đích, nhưng dễ bất ngờ trong test slice.
- **Key SpEL nên đi qua `CacheKeys.join`** để được validate (chặn blank/whitespace/`::`) — string tự nối dễ sinh key bẩn phá quy ước `<prefix>::<cacheName>::<key>`.
- **Caffeine là per-instance**: nhiều replica thì mỗi pod một cache riêng, không có invalidation chéo — dữ liệu cần nhất quán giữa các replica phải dùng Redis.
- Serializer dùng `ObjectMapper` **riêng** của `RedisCacheConfigurations` — customize `ObjectMapper` toàn cục của Boot không ảnh hưởng tới cache.
