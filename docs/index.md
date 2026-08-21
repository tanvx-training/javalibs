# Tài liệu javalibs

Bộ thư viện dùng chung chuẩn hóa cách xây dựng microservices với **Spring Boot 3.5.x / Java 21**.

## Bắt đầu

| Tài liệu | Nội dung |
|---|---|
| [Getting Started](getting-started.md) | Dựng một microservice hoàn chỉnh với javalibs trong 15 phút |
| [Cấu trúc dự án](project-structure.md) | Bố cục thư mục/package chuẩn cho service mới: DDD+CQRS hoặc layered gọn, chiều phụ thuộc, checklist khởi tạo |
| [Kiến trúc](architecture.md) | Mô hình 4 tầng core → spring → autoconfigure → starter, nguyên tắc thiết kế, cách auto-configuration hoạt động |
| [Tham chiếu cấu hình](configuration-reference.md) | Bảng tra cứu **toàn bộ** thuộc tính `javalibs.*` của mọi module |
| [Cookbook](cookbook.md) | Công thức cho các tình huống thực tế: outbox, revoke token, Keycloak, search API, error catalog... |
| [Vận hành](operations.md) | Build, versioning, publish lên Nexus/Artifactory, CI/CD, nâng cấp Spring Boot |
| [Đóng góp](contributing.md) | Quy tắc thêm module mới, checklist review |

## Tham chiếu module

### Nền tảng (Foundation)
| Module | Tài liệu | Vai trò |
|---|---|---|
| `javalibs-dependencies` | [Kiến trúc § BOM](architecture.md#bom--quản-lý-version) | BOM chốt version mọi artifact |
| `javalibs-internal` | [internal.md](modules/internal.md) | Tiện ích thuần Java: Strings, Checks, Dates, Ids |
| `javalibs-spring` | [spring.md](modules/spring.md) | Mở rộng Spring core: context tĩnh, chẩn đoán bean chậm |
| `javalibs-test` | [test.md](modules/test.md) | Test support: Testcontainers PostgreSQL, TestData, Jsons |

### Kiến trúc lõi & nghiệp vụ
| Module | Tài liệu | Vai trò |
|---|---|---|
| `javalibs-ddd` | [ddd.md](modules/ddd.md) | AggregateRoot, DomainEvent, BusinessRule — building blocks DDD |
| `javalibs-cqrs` | [cqrs.md](modules/cqrs.md) | Command/Query bus in-memory |

### Giao tiếp & tích hợp
| Module | Tài liệu | Vai trò |
|---|---|---|
| `javalibs-web` | [web.md](modules/web.md) | Chuẩn JSON lỗi, GlobalExceptionHandler, PageResponse, request logging, CORS |
| `javalibs-security` | [security.md](modules/security.md) | JWT stateless, UserContext, @RequireRole, revocation, OAuth2/OIDC, phát hành token (`javalibs-security-issuer`) |
| `javalibs-authz` | [authz.md](modules/authz.md) | Phân quyền per-resource kiểu YouTrack: Permission → Role → Grant → Scope, `@RequirePermission`, `PermissionChecker` |
| `javalibs-datahub` | [datahub.md](modules/datahub.md) | EventEnvelope, Kafka publisher, **Transactional Outbox**, idempotent consumer |
| `javalibs-search` | [search.md](modules/search.md) | HTTP params → JPA Specifications (dynamic filtering) |
| `javalibs-openapi` | [openapi.md](modules/openapi.md) | Swagger/OpenAPI chuẩn hóa, mã lỗi toàn cục trong docs |

### Hạ tầng & giám sát
| Module | Tài liệu | Vai trò |
|---|---|---|
| `javalibs-observability` | [observability.md](modules/observability.md) | Correlation ID, MDC propagation, metric tags, tracing |
| `javalibs-logging` | [logging.md](modules/logging.md) | Log JSON có cấu trúc, che dữ liệu nhạy cảm, access log HTTP |
| `javalibs-cache` | [cache.md](modules/cache.md) | Redis/Caffeine: key convention, JSON serialize, TTL theo cache |
| `javalibs-resilience` | [resilience.md](modules/resilience.md) | Circuit Breaker, Retry, Rate Limiter (Resilience4j) |
| `javalibs-persistence` | [persistence.md](modules/persistence.md) | Flyway conventions an toàn cho production |

## Ma trận tính năng ↔ starter

| Bạn cần | Thêm starter | Bật thêm |
|---|---|---|
| REST API chuẩn lỗi/phân trang | `javalibs-web-spring-boot-starter` | — |
| Xác thực JWT | `javalibs-security-spring-boot-starter` | `javalibs.security.jwt.secret` |
| Thu hồi token | (như trên) | `javalibs.security.blacklist.mode=redis` |
| Đăng nhập qua Keycloak | (như trên) + `spring-boot-starter-oauth2-resource-server` | `javalibs.security.mode=oauth2-resource-server` |
| Tự phát hành token (login/refresh/logout) | (như trên) + `javalibs-security-issuer` | `javalibs.security.issuer.*` + `/auth/**` trong `permit-all` |
| Phân quyền per-resource (project, tenant...) | `javalibs-authz-spring-boot-starter` + `javalibs-authz-jpa` | Khai báo `PermissionCatalog`, dùng `@RequirePermission`/`PermissionChecker` |
| Bắn event Kafka an toàn | `javalibs-datahub-spring-boot-starter` | `javalibs.datahub.outbox.enabled=true` + migration |
| Tìm kiếm động | `javalibs-search-spring-boot-starter` | — |
| Cache | `javalibs-cache-spring-boot-starter` | `@EnableCaching` (+ starter-data-redis nếu dùng Redis) |
| Chống cascading failure | `javalibs-resilience-spring-boot-starter` | `javalibs.resilience.rest.enabled=true` |
| Swagger docs | `javalibs-openapi-spring-boot-starter` | — |
| Migration DB | `javalibs-persistence-spring-boot-starter` | `spring.flyway.*` chuẩn Boot |
| Log/metrics/tracing | `javalibs-observability-spring-boot-starter` | — |
| Integration test | `javalibs-test` + `javalibs-security-test` (scope `test`) | Docker |
