# Thiết kế: Hệ thống xác thực & phân quyền kiểu YouTrack cho javalibs

**Ngày:** 2026-07-17
**Trạng thái:** Đã duyệt thiết kế, chờ lập kế hoạch triển khai

## 1. Mục tiêu & phạm vi

Xây dựng trong javalibs một hệ thống xác thực (phát hành token) và phân quyền
(authorization) theo mô hình YouTrack, dạng thư viện tái sử dụng để mọi
microservice trong hệ sinh thái nhúng được:

- **Phân quyền**: Permission (nguyên tử) → Role (gói permission) → gán cho User
  hoặc Group → theo scope Global hoặc per-resource (tổng quát hóa "project"
  thành cặp `scopeType + scopeId`).
- **Xác thực**: phát hành token — login username/password, access + refresh
  token có rotation, logout/revoke. Phần *validate* token giữ nguyên ở
  `javalibs-security` hiện có (issue-vs-validate split).
- **Lưu trữ**: SPI thuần Java trong core + module JPA cung cấp implementation
  mặc định (entity, Flyway migration, repository) — app dùng ngay hoặc tự
  implement SPI.
- **Đánh giá quyền**: server-side qua resolver + cache; JWT chỉ chứa identity.
- **API cho developer**: annotation `@RequirePermission` + bean
  `PermissionChecker` programmatic.

**Ngoài phạm vi phiên bản đầu:** 2FA, xác minh email, reset password, nested
groups, deny-rule, rate-limit chống brute-force (app tự ghép
`javalibs-resilience`), controller quản trị role/grant (app tự expose REST
trên `AuthzManagementService`).

## 2. Bố cục module

### 2.1. Family mới `javalibs-authz`

```
javalibs-authz/
├── javalibs-authz-core                      # Java thuần, 0 Spring
│   ├── Scope (GLOBAL | scopeType + scopeId)
│   ├── RoleDefinition, PermissionGrant, Subject (USER|GROUP)
│   ├── PermissionEvaluator                  # engine đánh giá quyền hiệu dụng
│   ├── PermissionCatalog                    # optional: app đăng ký danh sách code hợp lệ
│   └── SPI: GrantResolver, GroupMembershipResolver
├── javalibs-authz-spring
│   ├── @RequirePermission + RequirePermissionInterceptor
│   ├── PermissionChecker (bean programmatic: check()/require())
│   └── CachingGrantResolver / CachingGroupMembershipResolver
│       (decorator trên Spring Cache abstraction)
├── javalibs-authz-jpa                       # implementation mặc định
│   ├── Entity + Flyway migration (prefix authz_)
│   ├── JpaGrantResolver, JpaGroupMembershipResolver
│   ├── AuthzManagementService (CRUD role, grant, group)
│   └── Adapter implement SPI của security-issuer (CredentialsStore,
│       RefreshTokenStore) — dependency optional
├── javalibs-authz-spring-boot-autoconfigure
└── javalibs-authz-spring-boot-starter
```

### 2.2. Module mới trong family security: `javalibs-security-issuer`

- `AuthenticationService` — login / refresh / logout.
- `TokenIssuer` — ký access token (HS256/RS256) + sinh refresh token opaque.
- `PasswordHasher` — BCrypt qua `DelegatingPasswordEncoder`
  (spring-security-crypto).
- SPI: `CredentialsStore` (tìm user + password hash + enabled),
  `RefreshTokenStore` (lưu/rotate/revoke theo hash).
- REST endpoint sẵn dùng `/auth/login`, `/auth/refresh`, `/auth/logout` —
  autoconfigure khi issuer trên classpath, tắt/đổi base-path qua
  `javalibs.security.issuer.endpoints.*`.
- Autoconfiguration đặt trong `javalibs-security-spring-boot-autoconfigure`
  hiện có (dependency optional + `@ConditionalOnClass`).

### 2.3. Chiều phụ thuộc

```
authz-spring  ──►  security-spring      (đọc UserContext từ UserContextHolder)
authz-jpa     ──►  security-issuer      (implement CredentialsStore/RefreshTokenStore, optional)
security-issuer ─► security-core        (JwtValidationConfig, TokenBlacklist, UserContext)
```

Security là tầng thấp hơn authz; không có phụ thuộc vòng. Service chỉ check
quyền nhúng `javalibs-authz-spring-boot-starter`; auth-service phát token
nhúng thêm `javalibs-security-issuer`.

## 3. Mô hình phân quyền (ngữ nghĩa)

Thuần cộng (additive) — **không có deny**, giống YouTrack:

- **Permission**: chuỗi code do app định nghĩa (`issue.update`,
  `project.admin`…). Lib không cứng danh sách. App có thể đăng ký
  `PermissionCatalog`; khi đó role chứa code lạ sẽ fail-fast lúc khởi động.
- **Role**: key duy nhất + tập permission codes.
- **Scope**: `GLOBAL` hoặc `(scopeType, scopeId)` — vd `("project", "42")`,
  `("organization", "acme")`.
- **Grant**: `(subjectType USER|GROUP, subjectId) + roleKey + scope`.
- **Quy tắc**: user có quyền P tại scope S ⟺ tồn tại grant cho chính user
  *hoặc* một group chứa user, role của grant chứa P, và scope của grant là
  `GLOBAL` *hoặc* bằng đúng S. Grant global phủ mọi scope. Group không lồng
  nhau.

## 4. Schema DB (`javalibs-authz-jpa`, Flyway)

| Bảng | Cột chính |
|---|---|
| `authz_user` | id UUID PK, username unique, email, password_hash, display_name, enabled, created_at, updated_at |
| `authz_group` | id, name unique, description |
| `authz_group_member` | group_id FK, user_id FK, PK tổ hợp |
| `authz_role` | id, key unique, name, description |
| `authz_role_permission` | role_id FK, permission (varchar), PK tổ hợp |
| `authz_role_grant` | id, subject_type (USER hoặc GROUP), subject_id, role_id FK, scope_type NULL, scope_id NULL, unique (subject_type, subject_id, role_id, scope_type, scope_id); cả hai scope NULL = global |
| `authz_refresh_token` | id, user_id FK, token_hash (SHA-256, unique), family_id, expires_at, revoked_at NULL, rotated_to NULL, created_at |

Migration đặt theo convention `javalibs-persistence` (clean-disabled, naming
validation). Index: `authz_role_grant(subject_type, subject_id)`,
`authz_group_member(user_id)`, `authz_refresh_token(token_hash)`.

## 5. Luồng runtime

### 5.1. Check quyền

1. Filter hiện có của `javalibs-security` validate JWT → `UserContext`.
2. `@RequirePermission(value = "issue.update", scopeType = "project",
   scopeIdParam = "projectId")` — interceptor lấy scopeId từ path variable
   cùng tên; không khai báo scope → check GLOBAL.
3. Interceptor gọi `PermissionChecker.require(userId, permission, scope)`.
4. `PermissionEvaluator` (core): groups của user (GroupMembershipResolver) +
   grants của user ∪ groups (GrantResolver) → quyền hiệu dụng cho scope.
5. Từ chối → `AccessDeniedException` → 403 JSON chuẩn qua
   `RestAccessDeniedHandler` hiện có.

### 5.2. Cache

- Decorator caching trên Spring Cache abstraction — Caffeine hoặc Redis (qua
  `javalibs-cache`) tùy app cấu hình.
- Key theo subject (userId cho membership; subject cho grants). TTL cấu hình,
  mặc định 60 giây.
- `AuthzManagementService` evict chủ động khi grant/membership thay đổi;
  giữa nhiều instance, TTL là chốt an toàn cuối.

### 5.3. Login / refresh / logout

- **Login**: `CredentialsStore.findByUsername` → BCrypt verify → phát access
  JWT (TTL mặc định 15 phút, claims: sub, username, email, jti, iat/exp,
  issuer) + refresh token opaque 256-bit (TTL mặc định 30 ngày, DB lưu
  SHA-256 hash, gắn `family_id` theo phiên).
- **Refresh + rotation**: lookup theo hash, kiểm tra chưa hết hạn/chưa revoke
  → đánh dấu `rotated_to`, phát cặp mới cùng `family_id`. **Reuse
  detection**: dùng lại token đã rotate → revoke toàn bộ family → 401.
- **Logout**: revoke refresh token hiện tại + đưa `jti` access token vào
  `TokenBlacklist` sẵn có.
- Khóa ký/issuer dùng chung namespace cấu hình `javalibs.security.*` với
  validator để service khác validate được ngay token do issuer phát.

## 6. Xử lý lỗi

Theo chuẩn JSON lỗi `javalibs-web`:

| Tình huống | Kết quả |
|---|---|
| Sai credentials, user disabled | 401, thông điệp chung "invalid credentials" (không lộ user có tồn tại) |
| Refresh token hết hạn / revoked / không tồn tại | 401 |
| Reuse refresh token đã rotate | 401 + revoke cả family |
| Thiếu permission | 403 qua `RestAccessDeniedHandler` |
| Role chứa permission code ngoài `PermissionCatalog` (nếu app khai báo) | Fail-fast lúc khởi động app |

## 7. Cấu hình (dự kiến)

```yaml
javalibs:
  security:
    issuer:
      enabled: true
      access-token-ttl: 15m
      refresh-token-ttl: 30d
      endpoints:
        enabled: true
        base-path: /auth
  authz:
    enabled: true
    cache:
      enabled: true
      ttl: 60s
```

Mọi bean đều `@ConditionalOnMissingBean`; mọi dependency bên thứ ba trong
autoconfigure đều `<optional>true</optional>` theo chuẩn javalibs.

## 8. Testing

- **authz-core**: unit test thuần Java cho ngữ nghĩa evaluator (global phủ
  scoped, hợp quyền user ∪ groups, scope không khớp, không deny).
- **authz-spring**: interceptor resolve scope từ path variable, thiếu path
  variable, `PermissionChecker.check/require`, caching decorator.
- **authz-jpa**: integration qua `javalibs-test` (Testcontainers PostgreSQL):
  migration, resolver, `AuthzManagementService`, adapter cho issuer SPI.
- **security-issuer**: unit test rotation + reuse detection + password
  verify; integration test tròn vòng: login → gọi API `@RequirePermission`
  → refresh → logout → access token cũ bị blacklist chặn.
- **Autoconfigure**: `ApplicationContextRunner` test bật/tắt theo property,
  override bean.

## 9. Tài liệu

- `docs/modules/authz.md` mới (tiếng Việt, theo format docs/modules hiện có).
- Cập nhật `docs/modules/security.md` cho phần issuer.
- Cookbook: recipe "dựng auth-service + phân quyền per-project kiểu YouTrack".
- Cập nhật bảng module trong `README.md` + `javalibs-dependencies` BOM.
