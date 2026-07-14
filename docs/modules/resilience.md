# javalibs-resilience

> Tích hợp Resilience4j với default của platform: registry Circuit Breaker / Retry / Rate Limiter cấu hình sẵn qua `javalibs.resilience.*` và interceptor ngắt mạch cho RestClient — chống cascading failure giữa các microservice.

## Artifacts

Module có **3 submodule — không có core** (không có contract thuần Java riêng; API chính là của Resilience4j).

| artifactId | Packaging | Mô tả |
|---|---|---|
| `javalibs-resilience` | pom | Parent gộp 3 submodule |
| `javalibs-resilience-spring` | jar | `CircuitBreakingClientHttpRequestInterceptor` bảo vệ HTTP call outbound (RestClient/RestTemplate). Phụ thuộc `resilience4j-circuitbreaker`, `spring-web`, `slf4j-api`. Package `io.javalibs.resilience.spring` |
| `javalibs-resilience-spring-boot-autoconfigure` | jar | `ResilienceAutoConfiguration` (3 registry), `ResilienceRestAutoConfiguration` (customizer opt-in) + `ResilienceProperties` (namespace `javalibs.resilience.*`). Các module resilience4j đều *optional* — registry nào có class nấy mới được tạo. Package `io.javalibs.resilience.autoconfigure` |
| `javalibs-resilience-spring-boot-starter` | jar | Starter: spring + autoconfigure + **`resilience4j-circuitbreaker` + `resilience4j-retry` + `resilience4j-ratelimiter`** (core, không kèm module Spring Boot của resilience4j) |

## Khi nào dùng / không dùng

**Dùng khi:**

- Service gọi service khác và cần circuit breaker để một downstream chết không kéo sập cả chuỗi (thread pile-up → cascading failure).
- Muốn mọi breaker/retry/rate limiter trong hệ thống kế thừa **cùng một bộ default** thay vì mỗi service tự chỉnh.
- Muốn bảo vệ tự động mọi `RestClient.Builder` do Boot cung cấp chỉ bằng một property.

**Không dùng khi:**

- Muốn style annotation `@CircuitBreaker`/`@Retry` của resilience4j — starter này **không kèm** `resilience4j-spring-boot3` + AOP; phải tự thêm (xem Ghi đè & mở rộng), khi đó auto-config của resilience4j đảm nhiệm và bean ở đây tự lùi.
- Chỉ cần retry HTTP đơn giản với backoff — [javalibs-datahub](datahub.md) đã có `RetryingClientHttpRequestInterceptor` nhẹ hơn (không cần resilience4j).
- Cần bulkhead/time-limiter — module chỉ cấu hình sẵn 3 registry (circuit breaker, retry, rate limiter); các module resilience4j khác dùng trực tiếp.

## Các thành phần chính

### `CircuitBreakingClientHttpRequestInterceptor` (`javalibs-resilience-spring`)

`ClientHttpRequestInterceptor` bọc mỗi HTTP call outbound bằng một `CircuitBreaker` của Resilience4j. Constructor: `(CircuitBreaker circuitBreaker)` — null ném `NullPointerException`.

Luồng `intercept(request, body, execution)`:

1. `circuitBreaker.tryAcquirePermission()` — **không được cấp phép (mạch OPEN)** → log WARN và ném ngay `IOException("Circuit breaker '<name>' is open; request to <uri> was not attempted")` — **không hề chạm vào backend**, cho downstream thời gian hồi phục và không giam thread của service mình.
2. Được cấp phép → `execution.execute(...)`, đo thời gian bằng nanoTime:
   - Response **5xx** → `circuitBreaker.onError(elapsed, ...)` với `IOException("HTTP <status> from <uri>")` — **5xx tính là failure** dù không có exception; response vẫn được trả cho caller.
   - Response khác → `onSuccess(elapsed, ...)`.
   - **`IOException` hoặc `RuntimeException`** từ call → `onError(...)` rồi rethrow — lỗi I/O cũng tính failure.

**Thứ tự interceptor khi kết hợp retry** (vd retry của [javalibs-datahub](datahub.md)): đăng ký circuit breaker **gần "dây" nhất — cuối chain** (retry bọc ngoài, breaker bọc trong) để **từng lần retry được breaker ghi nhận riêng**; đặt ngược lại thì N lần retry chỉ tính 1 outcome, breaker mở chậm hơn thực tế.

### `ResilienceAutoConfiguration` (`javalibs-resilience-spring-boot-autoconfigure`)

Cung cấp 3 registry seed bằng default platform — mọi breaker/retry/limiter tạo từ registry đều kế thừa:

- `@AutoConfiguration(afterName = {...CircuitBreakerAutoConfiguration, ...RetryAutoConfiguration, ...RateLimiterAutoConfiguration})` — chạy **sau** các auto-config của `resilience4j-spring-boot3` (tham chiếu theo tên nên không cần dependency): nếu team thêm resilience4j-spring-boot3 để dùng annotation, registry của resilience4j được tạo trước và bean ở đây lùi.
- 3 nested `@Configuration(proxyBeanMethods = false)`, mỗi cái `@ConditionalOnClass` trên registry tương ứng (module resilience4j nào vắng mặt thì phần đó tắt) và bean đều `@ConditionalOnMissingBean`:

| Bean | Kiểu | Dựng từ |
|---|---|---|
| `circuitBreakerRegistry` | `CircuitBreakerRegistry` | `CircuitBreakerConfig.custom()` với 7 giá trị `circuit-breaker.*` |
| `retryRegistry` | `RetryRegistry` | `RetryConfig.custom().maxAttempts(...).intervalFunction(IntervalFunction.ofExponentialBackoff(waitDuration, multiplier))` |
| `rateLimiterRegistry` | `RateLimiterRegistry` | `RateLimiterConfig.custom()` với 3 giá trị `rate-limiter.*` |

### `ResilienceRestAutoConfiguration` (`javalibs-resilience-spring-boot-autoconfigure`)

Bảo vệ HTTP outbound, **opt-in** bằng `javalibs.resilience.rest.enabled=true`:

- `@AutoConfiguration(after = ResilienceAutoConfiguration)`; `@ConditionalOnClass(RestClient, RestClientCustomizer, CircuitBreakerRegistry)`.
- Bean `javalibsCircuitBreakingRestClientCustomizer` (`RestClientCustomizer`, `@ConditionalOnBean(CircuitBreakerRegistry)`, `@ConditionalOnMissingBean(name = "javalibsCircuitBreakingRestClientCustomizer")`): tạo **một** breaker tên `javalibs.resilience.rest.circuit-breaker-name` (mặc định `rest-client`) từ registry chung rồi gắn `CircuitBreakingClientHttpRequestInterceptor` vào **mọi `RestClient.Builder` do Boot auto-configure** (customizer chỉ áp vào builder Boot cung cấp, không áp vào builder tự `RestClient.builder()`).

## Cấu hình

Toàn bộ property bind vào `ResilienceProperties` (`javalibs.resilience.*`). Đây là **default cho registry** — breaker/retry/limiter tạo từ registry kế thừa:

| Thuộc tính | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `javalibs.resilience.circuit-breaker.failure-rate-threshold` | float (%) | `50` | Tỷ lệ lỗi (%) để mạch OPEN |
| `javalibs.resilience.circuit-breaker.slow-call-rate-threshold` | float (%) | `100` | Tỷ lệ call chậm (%) để mạch OPEN |
| `javalibs.resilience.circuit-breaker.slow-call-duration-threshold` | Duration | `2s` | Call lâu hơn ngưỡng này tính là chậm |
| `javalibs.resilience.circuit-breaker.sliding-window-size` | int | `20` | Kích thước cửa sổ trượt tính tỷ lệ |
| `javalibs.resilience.circuit-breaker.minimum-number-of-calls` | int | `10` | Số call tối thiểu trước khi tính tỷ lệ |
| `javalibs.resilience.circuit-breaker.wait-duration-in-open-state` | Duration | `30s` | Thời gian ở OPEN trước khi thăm dò lại (HALF_OPEN) |
| `javalibs.resilience.circuit-breaker.permitted-number-of-calls-in-half-open-state` | int | `5` | Số call thăm dò được phép ở HALF_OPEN |
| `javalibs.resilience.retry.max-attempts` | int | `3` | Tổng số lần thử, kể cả lần đầu |
| `javalibs.resilience.retry.wait-duration` | Duration | `500ms` | Thời gian chờ ban đầu giữa các lần thử |
| `javalibs.resilience.retry.exponential-backoff-multiplier` | double | `2.0` | Hệ số nhân backoff sau mỗi lần thử |
| `javalibs.resilience.rate-limiter.limit-for-period` | int | `50` | Số permit mỗi chu kỳ refresh |
| `javalibs.resilience.rate-limiter.limit-refresh-period` | Duration | `1s` | Chu kỳ refresh |
| `javalibs.resilience.rate-limiter.timeout-duration` | Duration | `0` | Thời gian call chờ permit (0 = fail fast) |
| `javalibs.resilience.rest.enabled` | boolean | `false` | **Opt-in**: gắn circuit breaker vào mọi `RestClient.Builder` do Boot cung cấp |
| `javalibs.resilience.rest.circuit-breaker-name` | String | `rest-client` | Tên breaker dùng cho RestClient (lấy/tạo từ registry chung) |

## Hướng dẫn sử dụng

### Dependency

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-resilience-spring-boot-starter</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### `application.yml`

```yaml
javalibs:
  resilience:
    circuit-breaker:
      failure-rate-threshold: 50
      sliding-window-size: 20
      minimum-number-of-calls: 10
      wait-duration-in-open-state: 30s
    retry:
      max-attempts: 3
      wait-duration: 500ms          # backoff nhân đôi: 500ms → 1s → ...
    rest:
      enabled: true                 # bảo vệ mọi RestClient.Builder của Boot
      circuit-breaker-name: rest-client
```

### Ví dụ 1 — `decorateSupplier` bảo vệ một call bất kỳ

```java
@Service
public class PaymentService {

  private final CircuitBreaker breaker;

  public PaymentService(CircuitBreakerRegistry registry) {
    // breaker tạo từ registry kế thừa default platform (failure-rate 50%, window 20...)
    this.breaker = registry.circuitBreaker("payment-service");
  }

  public PaymentResult charge(ChargeRequest request) {
    Supplier<PaymentResult> protectedCall =
        CircuitBreaker.decorateSupplier(breaker, () -> paymentClient.charge(request));
    return protectedCall.get();   // mạch OPEN → ném CallNotPermittedException ngay
  }
}
```

### Ví dụ 2 — breaker riêng cho từng backend từ registry chung

Một breaker cho mỗi downstream để backend này sập không mở mạch của backend kia:

```java
@Configuration
public class DownstreamClientsConfig {

  @Bean
  public RestClient inventoryClient(RestClient.Builder builder,
                                    CircuitBreakerRegistry registry) {
    return builder.baseUrl("http://inventory-service")
        .requestInterceptor(new CircuitBreakingClientHttpRequestInterceptor(
            registry.circuitBreaker("inventory-service")))
        .build();
  }

  @Bean
  public RestClient shippingClient(RestClient.Builder builder,
                                   CircuitBreakerRegistry registry) {
    return builder.baseUrl("http://shipping-service")
        .requestInterceptor(new CircuitBreakingClientHttpRequestInterceptor(
            registry.circuitBreaker("shipping-service")))
        .build();
  }
}
```

`registry.circuitBreaker("<tên>")` là get-or-create: gọi lại cùng tên trả về cùng instance, state chia sẻ trong toàn service.

### Ví dụ 3 — kết hợp retry + circuit breaker đúng thứ tự

```java
// Retry (javalibs-datahub) bọc NGOÀI, breaker bọc TRONG (cuối chain — gần dây nhất)
RestClient client = RestClient.builder()
    .baseUrl("http://inventory-service")
    .requestInterceptor(new RetryingClientHttpRequestInterceptor(3, Duration.ofMillis(200)))
    .requestInterceptor(new CircuitBreakingClientHttpRequestInterceptor(
        registry.circuitBreaker("inventory-service")))
    .build();
```

## Ghi đè & mở rộng

- **Registry riêng**: khai báo bean `CircuitBreakerRegistry`/`RetryRegistry`/`RateLimiterRegistry` — bean của thư viện đều `@ConditionalOnMissingBean` nên lùi.
- **Style annotation** (`@CircuitBreaker`, `@Retry`, `@RateLimiter`): tự thêm vào service

  ```xml
  <dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
  </dependency>
  ```

  Nhờ `afterName`, auto-config của resilience4j chạy trước và registry của nó (cấu hình qua `resilience4j.*`) thay thế registry mặc định của javalibs — hai cách cấu hình không trộn lẫn.
- **Customizer REST riêng**: khai báo bean tên `javalibsCircuitBreakingRestClientCustomizer` để thay hành vi gắn interceptor (vd breaker theo host).
- **Tuning một breaker cụ thể khác default**: `registry.circuitBreaker("name", customConfig)` với `CircuitBreakerConfig.from(registry.getDefaultConfig())...` — default vẫn từ platform, chỉ override phần cần.

## Testing

- **Auto-configuration** (cách module tự test trong `ResilienceAutoConfigurationTest`): `ApplicationContextRunner` + `AutoConfigurations.of(ResilienceAutoConfiguration, ResilienceRestAutoConfiguration)`; assert `getDefaultConfig()` của từng registry; test back-off bằng cách thêm user bean registry; test opt-in REST bằng `withPropertyValues("javalibs.resilience.rest.enabled=true")` rồi assert `RestClientCustomizer` tồn tại và breaker tên `rest-client` xuất hiện trong registry.
- **Interceptor** (`CircuitBreakingClientHttpRequestInterceptorTest`): không cần Spring context — `CircuitBreaker.of("test", CircuitBreakerConfig.custom().slidingWindowSize(2).minimumNumberOfCalls(2)...)` cho breaker mở nhanh, `MockClientHttpRequest`/`MockClientHttpResponse` + lambda `ClientHttpRequestExecution` giả backend; assert metrics (`getNumberOfSuccessfulCalls`) và short-circuit (backend không được gọi khi OPEN).
- Đơn giản hóa test trạng thái: `breaker.transitionToOpenState()` của resilience4j để ép mạch mở.

## Lưu ý & bẫy thường gặp

- **`rest.enabled` là opt-in** (mặc định `false`) — thêm starter không tự bảo vệ gì cả cho tới khi bật property hoặc tự gắn interceptor.
- **Customizer áp một breaker chung `rest-client` cho MỌI RestClient của Boot**: một downstream sập có thể mở mạch chặn cả các downstream khỏe. Nhiều backend quan trọng → tự gắn interceptor với breaker riêng từng backend (Ví dụ 2) thay vì dùng customizer chung.
- **`RestClientCustomizer` không áp vào builder tự tạo**: `RestClient.builder()` gọi thẳng static (kể cả `datahubRestClientBuilder` của [javalibs-datahub](datahub.md) — được tạo thủ công) không đi qua customizer; muốn bảo vệ phải tự `requestInterceptor(...)`.
- **Thứ tự retry/breaker**: breaker phải nằm **cuối chain** (gần dây nhất); ngược lại tỷ lệ lỗi bị "gộp" theo nhóm retry và mạch mở chậm.
- **Interceptor ném `IOException` khi mạch OPEN** (không phải `CallNotPermittedException`) — code bắt lỗi HTTP client xử lý như lỗi kết nối thông thường; còn `decorateSupplier` thuần resilience4j thì ném `CallNotPermittedException`.
- **5xx tính failure nhưng response vẫn trả về caller** — interceptor không nuốt response lỗi; đừng nhầm là đã được "xử lý".
- **Breaker cần đủ mẫu mới mở**: dưới `minimum-number-of-calls` (mặc định 10) thì không bao giờ OPEN — trong test phải hạ ngưỡng xuống.
- **Registry là get-or-create theo tên**: gõ nhầm tên breaker tạo breaker mới im lặng với default riêng — quy ước tên theo tên service downstream và đặt hằng.
- Starter chỉ kèm 3 core (circuitbreaker/retry/ratelimiter) — bulkhead, time-limiter hoặc micrometer binding của resilience4j phải tự thêm.
