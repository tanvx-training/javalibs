# javalibs-resilience-spring-boot-starter

Chống chịu lỗi (fault tolerance) chuẩn hóa với **Resilience4j**: Circuit Breaker, Retry, Rate Limiter — ngăn một service bị nghẽn kéo sập cả chuỗi gọi (cascading failure).

## Cài đặt

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-resilience-spring-boot-starter</artifactId>
</dependency>
```

Auto-configuration cung cấp sẵn `CircuitBreakerRegistry`, `RetryRegistry`, `RateLimiterRegistry` với **default của platform** — mọi circuit breaker tạo từ registry đều kế thừa:

```yaml
javalibs:
  resilience:
    circuit-breaker:
      failure-rate-threshold: 50      # % lỗi để ngắt mạch
      sliding-window-size: 20
      minimum-number-of-calls: 10
      wait-duration-in-open-state: 30s
    retry:
      max-attempts: 3
      wait-duration: 500ms            # backoff nhân đôi mỗi lần
    rest:
      enabled: true                   # bảo vệ mọi RestClient.Builder bằng circuit breaker
```

## Sử dụng

**Bảo vệ REST call tự động** — bật `javalibs.resilience.rest.enabled=true`: mọi `RestClient.Builder` do Boot cung cấp được gắn `CircuitBreakingClientHttpRequestInterceptor` (lỗi I/O và 5xx tính là failure; khi mạch OPEN, request fail ngay lập tức mà không chạm vào service đích).

**Lập trình trực tiếp:**

```java
CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("payment-service");
Supplier<PaymentResult> protectedCall =
    CircuitBreaker.decorateSupplier(breaker, () -> paymentClient.charge(request));
```

**Kết hợp với retry của javalibs-datahub:** đặt circuit breaker gần "dây" nhất (interceptor cuối) để mỗi lần retry được ghi nhận riêng.

**Dùng annotation `@CircuitBreaker`/`@Retry`?** Thêm `resilience4j-spring-boot3` + `spring-boot-starter-aop` vào service — registry của resilience4j sẽ thay thế registry mặc định của javalibs (các bean ở đây đều `@ConditionalOnMissingBean`).
