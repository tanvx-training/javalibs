# javalibs-resilience

Tích hợp Resilience4j với cấu hình mặc định của platform, chống cascading failure giữa các microservices.

| Submodule | Vai trò |
|---|---|
| `javalibs-resilience-spring` | `CircuitBreakingClientHttpRequestInterceptor` cho RestClient/RestTemplate |
| `javalibs-resilience-spring-boot-autoconfigure` | Registry CB/Retry/RateLimiter với default từ `javalibs.resilience.*`; customizer opt-in cho RestClient |
| `javalibs-resilience-spring-boot-starter` | Điểm chạm của client (kèm resilience4j core) |

Thuộc tính: xem README của starter. Mọi bean `@ConditionalOnMissingBean` — tương thích với `resilience4j-spring-boot3` nếu team muốn dùng annotation.
