# javalibs-cache-spring-boot-starter

Starter chuẩn hóa caching cho microservices: **Redis** (JSON serialization, key convention, TTL theo từng cache) với **Caffeine** làm fallback in-memory.

## Cài đặt

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-cache-spring-boot-starter</artifactId>
</dependency>

<!-- Muốn dùng Redis? Chỉ cần thêm: -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

Ứng dụng phải bật caching bằng `@EnableCaching` — auto-configuration chỉ kích hoạt khi đó.

## Cấu hình

```yaml
spring:
  application:
    name: orders-service   # trở thành key prefix mặc định
  data:
    redis:
      host: redis.internal

javalibs:
  cache:
    provider: auto          # auto | redis | caffeine
    default-ttl: 10m
    caches:                 # TTL/size riêng theo tên cache
      orders:
        ttl: 30s
      customers:
        ttl: 1h
        max-size: 5000      # max-size chỉ áp dụng cho Caffeine
```

## Quy ước key

Key Redis đầy đủ có dạng `<prefix>::<cacheName>::<key>`, ví dụ `orders-service::orders::by-customer:42`. Xây phần key bằng `CacheKeys.join(...)`:

```java
@Cacheable(cacheNames = "orders",
           key = "T(io.javalibs.cache.CacheKeys).join('by-customer', #customerId)")
public List<OrderDto> findByCustomer(String customerId) { ... }
```

## Lưu ý serialize (quan trọng)

Giá trị cache được serialize bằng JSON **kèm tên class** (`@class`) để deserialize không cần cấu hình type cho từng cache. Đổi tên/di chuyển class DTO đã cache ⇒ entry cũ không đọc được — hãy **flush cache khi refactor DTO**, và chỉ cache các DTO nhỏ chuyên dụng, không cache entity JPA.
