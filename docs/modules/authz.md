# javalibs-authz

> Phân quyền **per-resource kiểu YouTrack**: `Permission` gộp thành `Role`, `Role` được `Grant` cho `User`/`Group` trong một `Scope` (toàn cục hoặc một resource cụ thể như `project:42`). Đánh giá thuần cộng dồn (không có deny), `@RequirePermission` + `PermissionChecker` để chặn ở controller/service, cache Caffeine tùy chọn, JPA mặc định sẵn schema + `AuthzManagementService`.

## Artifacts

| Artifact (`groupId: io.javalibs`) | Nội dung | Phụ thuộc chính |
|---|---|---|
| `javalibs-authz-core` | Java thuần, **không phụ thuộc Spring**: `Scope`, `Subject`, `SubjectType`, `ResolvedGrant`, SPI `GrantResolver`/`GroupMembershipResolver`, `PermissionEvaluator`, `PermissionCatalog`, `AuthzCacheInvalidator`, `UnknownPermissionException` | — |
| `javalibs-authz-spring` | Tích hợp Spring: `PermissionChecker`, `@RequirePermission` + `RequirePermissionInterceptor`, `CachingGrantResolver`/`CachingGroupMembershipResolver`/`CachingAuthzInvalidator` (Caffeine) | core, `javalibs-security-spring` (dùng `UserContextHolder`), `spring-webmvc`; optional: `caffeine` |
| `javalibs-authz-jpa` | Persistence JPA mặc định: entity `authz_*`, Flyway migration, repository, `JpaGrantResolver`/`JpaGroupMembershipResolver`, `AuthzManagementService`, **tự đăng ký `AuthzJpaAutoConfiguration` của chính nó** (xem lưu ý bên dưới), và (khi có `javalibs-security-issuer` trên classpath) `JpaCredentialsStore`/`JpaRefreshTokenStore` | core, `spring-data-jpa`; optional: `javalibs-security-issuer`, `spring-boot-autoconfigure`, `flyway-core` |
| `javalibs-authz-spring-boot-autoconfigure` | `AuthzProperties` (`javalibs.authz.*`) + `AuthzAutoConfiguration` (evaluator, checker, cache) + `AuthzWebMvcAutoConfiguration` (đăng ký interceptor `@RequirePermission`) | spring module, `spring-boot-autoconfigure`; optional: `caffeine` |
| `javalibs-authz-spring-boot-starter` | Không có code — gom core + spring + autoconfigure + `caffeine`. Ghép với `javalibs-authz-jpa` để có persistence mặc định | — |

**Lưu ý quan trọng về vị trí `AuthzJpaAutoConfiguration`**: class này nằm trong package `io.javalibs.authz.jpa.autoconfigure` **bên trong `javalibs-authz-jpa`**, tự đăng ký qua `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` của chính module đó — **không** nằm trong `javalibs-authz-spring-boot-autoconfigure`. Lý do: tránh vòng phụ thuộc reactor (`autoconfigure` → `jpa` → `starter` → `autoconfigure`, vì `javalibs-authz-jpa` cần `javalibs-authz-spring-boot-starter` ở test scope cho bài E2E). Hệ quả với người dùng: **chỉ cần thêm `javalibs-authz-jpa` vào classpath là đủ để có entity/repository/resolver/Flyway/`AuthzManagementService`/issuer store mặc định** — không cần khai báo thêm gì từ module autoconfigure trung tâm. `AuthzAutoConfiguration` (ở module autoconfigure) khai báo chạy **sau** `AuthzJpaAutoConfiguration` (`afterName`, tham chiếu qua tên lớp đầy đủ để không cần compile-time reference) để tiêu thụ đúng các bean `GrantResolver`/`GroupMembershipResolver` mà nó tạo ra.

## Khi nào dùng / không dùng

**Dùng khi:**

- Cần phân quyền theo resource cụ thể (project, tenant, document...) chứ không chỉ role toàn cục kiểu `ROLE_ADMIN` của `javalibs-security`.
- Mô hình quyền kiểu YouTrack/Jira: một role gồm nhiều permission, gán role cho user/group trong một scope, đánh giá cộng dồn (có bất kỳ grant nào chứa quyền → được phép).
- Muốn có sẵn schema + API quản trị (tạo role, gán/thu hồi grant) mà không phải tự thiết kế bảng — dùng `javalibs-authz-jpa`.
- Cần chặn quyền khai báo (annotation trên controller) **và** chặn theo chương trình (trong service) bằng cùng một API.

**Không dùng khi:**

- Chỉ cần phân quyền role toàn cục đơn giản (không có khái niệm "trong project nào") — `@RequireRole` của [javalibs-security](security.md) là đủ và nhẹ hơn.
- Cần deny rule (từ chối tường minh, ưu tiên hơn allow) — v1 chỉ hỗ trợ cộng dồn thuần túy, xem [Giới hạn v1](#giới-hạn-v1).
- Cần nested group (group chứa group) — chỉ hỗ trợ thành viên trực tiếp.

Module này **chỉ đánh giá quyền** (authorization); xác thực danh tính (đăng nhập, JWT) vẫn do [javalibs-security](security.md) + [javalibs-security-issuer](security.md#phát-hành-token-issuer) đảm nhiệm — `PermissionChecker` đọc `userId` hiện tại từ `UserContextHolder`.

## Mô hình khái niệm

```
Permission (chuỗi, vd "issue.read")
    │  gộp nhiều permission thành
    ▼
Role (vd "issue-viewer" = {issue.read, issue.update})
    │  Grant role cho Subject (User hoặc Group), trong một Scope
    ▼
Grant = (Subject, Role, Scope)
    Scope = GLOBAL                       — áp dụng mọi resource
          | (scopeType, scopeId)         — áp dụng đúng 1 resource, vd ("project", "P1")
```

Đánh giá quyền (`PermissionEvaluator.hasPermission(userId, permission, scope)`): user có quyền `permission` trong `scope` khi **tồn tại ít nhất một** grant của chính user hoặc của một group user thuộc về, sao cho grant đó **là GLOBAL hoặc đúng bằng `scope` được hỏi**, và role của grant đó chứa `permission`. Không có khái niệm ưu tiên/deny — mọi grant khớp đều cộng dồn quyền (`ResolvedGrant.appliesTo(Scope)`).

Ví dụ kiểu YouTrack: user Alice được gán role `issue-viewer` (permission `issue.read`) trong scope `("project", "P1")`. Alice đọc được issue của project `P1` (`hasPermission("alice", "issue.read", Scope.of("project", "P1"))` → `true`) nhưng **không** đọc được issue của project `P2` (scope khác, không có grant khớp) — trừ khi Alice còn có một grant khác ở scope `GLOBAL` chứa `issue.read`, hoặc grant đó đến từ một group Alice là thành viên.

## Quickstart

### 1. Thêm dependency

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-authz-spring-boot-starter</artifactId>
</dependency>
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-authz-jpa</artifactId>
</dependency>
```

(Version quản lý bởi BOM `javalibs-dependencies` — xem [Kiến trúc](../architecture.md). `javalibs-authz-jpa` tự động kích hoạt `AuthzJpaAutoConfiguration` như giải thích ở trên — không cần thêm gì khác để có schema + resolver mặc định.)

### 2. Khai báo `PermissionCatalog` (khuyến nghị)

```java
@Configuration
public class AuthzCatalogConfig {

    @Bean
    public PermissionCatalog permissionCatalog() {
        return new PermissionCatalog(Set.of(
                "issue.read", "issue.update", "issue.delete", "project.admin"));
    }
}
```

Khi có bean này, `AuthzManagementService.createRole(...)` và `validateRolesAgainstCatalog()` (chạy tự động lúc khởi động) fail-fast với `UnknownPermissionException` nếu role tham chiếu permission code chưa đăng ký — bắt lỗi gõ sai ngay lúc build/deploy thay vì lúc đánh giá quyền.

### 3. Tạo role/grant qua `AuthzManagementService`

```java
@Service
public class AuthzBootstrapService {

    private final AuthzManagementService authz;
    private final PasswordHasher passwordHasher;

    public AuthzBootstrapService(AuthzManagementService authz, PasswordHasher passwordHasher) {
        this.authz = authz;
        this.passwordHasher = passwordHasher;
    }

    public void seed() {
        UUID userId = authz.createUser("alice", "alice@example.com", "Alice",
                passwordHasher.hash("s3cret"), true);

        authz.createRole("issue-viewer", "Issue Viewer", null, Set.of("issue.read"));
        authz.grantRole(Subject.user(userId.toString()), "issue-viewer",
                Scope.of("project", "P1"));
    }
}
```

(`PasswordHasher` đến từ `javalibs-security-issuer` — xem [Phát hành token (issuer)](security.md#phát-hành-token-issuer); dùng khi bạn tự quản lý user/mật khẩu qua bảng `authz_user`.)

### 4. `@RequirePermission` trên controller

```java
import io.javalibs.authz.spring.RequirePermission;

@RestController
@RequestMapping("/api/projects/{projectId}/issues")
public class IssueController {

    @GetMapping
    @RequirePermission(value = "issue.read", scopeType = "project", scopeIdParam = "projectId")
    public List<IssueDto> list(@PathVariable String projectId) { ... }

    @RequirePermission("project.admin")   // scope GLOBAL — không cần scopeType/scopeIdParam
    @DeleteMapping
    public void deleteAll(@PathVariable String projectId) { ... }
}
```

`scopeIdParam` phải khớp tên path variable (`@PathVariable`) mang scope id; interceptor đọc từ `HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE` của request. Vi phạm ném `AccessDeniedException` → 403 (hoặc 401 nếu chưa xác thực), theo đúng cơ chế JSON lỗi của [javalibs-security](security.md).

### 5. `PermissionChecker` trong service

```java
import io.javalibs.authz.Scope;
import io.javalibs.authz.spring.PermissionChecker;

@Service
public class IssueService {

    private final PermissionChecker permissionChecker;

    public IssueService(PermissionChecker permissionChecker) {
        this.permissionChecker = permissionChecker;
    }

    public void close(String projectId, String issueId) {
        permissionChecker.require("issue.update", Scope.of("project", projectId)); // caller hiện tại (UserContextHolder)
        // ...
    }

    public boolean canDelete(String userId, String projectId) {
        return permissionChecker.check(userId, "issue.delete", Scope.of("project", projectId));
    }
}
```

## Cấu hình

Toàn bộ thuộc tính namespace `javalibs.authz.*` (bind vào `AuthzProperties`):

| Thuộc tính | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `javalibs.authz.enabled` | boolean | `true` | Bật/tắt `AuthzAutoConfiguration` + `AuthzWebMvcAutoConfiguration` (evaluator, checker, interceptor `@RequirePermission`) |
| `javalibs.authz.cache.enabled` | boolean | `true` | Bọc `GrantResolver`/`GroupMembershipResolver` bằng cache Caffeine (cần `caffeine` trên classpath) |
| `javalibs.authz.cache.ttl` | Duration | `60s` | TTL cache (expire-after-write) — cận trên an toàn khi mutation xảy ra ở instance khác |
| `javalibs.authz.cache.max-size` | long | `10000` | Số subject tối đa giữ trong mỗi cache |
| `javalibs.authz.jpa.enabled` | boolean | `true` | Bật `AuthzJpaAutoConfiguration` (entity scan, repository, `JpaGrantResolver`/`JpaGroupMembershipResolver`, `AuthzManagementService`) khi `javalibs-authz-jpa` có trên classpath |
| `javalibs.authz.jpa.apply-migrations` | boolean | `true` | Tự thêm `classpath:db/migration/javalibs-authz` vào danh sách location của Flyway |

### Bẫy khi retrofit vào service đã có Flyway

Hai migration `V1__authz_init.sql` và `V2__authz_refresh_token.sql` của `javalibs-authz-jpa` dùng version số nguyên nhỏ (`V1`, `V2`). Với một service **mới dựng từ đầu** (greenfield), điều này không sao — chúng là hai migration đầu tiên chạy.

Nhưng nếu bạn **retrofit** `javalibs-authz-jpa` vào một service **đã tồn tại**, và service đó đi theo convention timestamp-version của [javalibs-persistence](persistence.md) (`V<yyyyMMddHHmm>__<mo_ta>.sql`, xem [Quy ước migration của platform](persistence.md#quy-ước-migration-của-platform)) — nghĩa là DB đã áp các migration mang version dạng `V202607141030`... — thì `V1`/`V2` của authz sẽ có version **nhỏ hơn** version cao nhất đã áp trên DB. Với `javalibs.persistence.flyway.out-of-order=false` (mặc định của platform, xem [persistence.md § Cấu hình](persistence.md#cấu-hình)), Flyway sẽ **fail ngay lúc khởi động** vì coi đây là migration bị bỏ sót thay vì migration mới.

**Cách retrofit an toàn:**

1. Đặt `javalibs.authz.jpa.apply-migrations=false` để `AuthzJpaAutoConfiguration` không tự thêm `classpath:db/migration/javalibs-authz` vào location của Flyway.
2. Copy hai file `V1__authz_init.sql` và `V2__authz_refresh_token.sql` (nằm trong `javalibs-authz-jpa` ở `db/migration/javalibs-authz/`) vào thư mục migration riêng của app (`src/main/resources/db/migration/`), đổi tên theo version timestamp tại thời điểm merge — ví dụ `V202607181200__authz_init.sql`, `V202607181201__authz_refresh_token.sql`. Nội dung SQL giữ nguyên.
3. Từ đó `javalibs-authz-jpa` chỉ cung cấp entity/repository/resolver/`AuthzManagementService`; phần schema do chính app quản lý cùng các migration khác của nó, tuân theo lịch sử tuyến tính đã có.

Service greenfield (chưa có Flyway, hoặc mới bắt đầu với version thấp) có thể giữ mặc định `apply-migrations=true` và dùng thẳng `V1`/`V2` có sẵn.

## Cache: TTL + evict chủ động

`CachingGrantResolver`/`CachingGroupMembershipResolver` (Caffeine, `expireAfterWrite`) cache theo `Subject`/`userId`, được `AuthzAutoConfiguration` bọc quanh `GrantResolver`/`GroupMembershipResolver` khi `javalibs.authz.cache.enabled=true` (mặc định) và `caffeine` có trên classpath — đánh dấu `@Primary` nên `PermissionEvaluator` luôn dùng bản đã cache.

- **Evict chủ động cùng instance**: mọi mutation qua `AuthzManagementService` (`grantRole`, `revokeGrant`, `addUserToGroup`, `removeUserFromGroup`) gọi `AuthzCacheInvalidator` (implementation `CachingAuthzInvalidator`, tự động wire khi cả hai cache tồn tại) để evict ngay subject/user liên quan — quyền thay đổi có hiệu lực **ngay lập tức** trên instance vừa thực hiện mutation.
- **TTL là chốt an toàn multi-instance**: `AuthzCacheInvalidator` chỉ evict cache **local** của instance xử lý request ghi; các instance khác (không có cơ chế pub/sub đồng bộ cache) vẫn phục vụ cache cũ tối đa `javalibs.authz.cache.ttl` (mặc định 60s) sau khi mutation xảy ra ở nơi khác. Coi TTL là độ trễ tối đa chấp nhận được cho việc phân quyền lan truyền toàn hệ thống khi scale ngang; giảm `ttl` nếu nghiệp vụ cần thu hồi quyền nhanh hơn, đánh đổi với tần suất load lại từ `GrantResolver` gốc.

## SPI: tự implement `GrantResolver`/`GroupMembershipResolver`

Khi ứng dụng có schema user/role/group riêng (không dùng `javalibs-authz-jpa`), implement hai SPI của `javalibs-authz-core` và khai báo bean cùng loại — bean JPA mặc định (nếu có) tự lùi bước nhờ `@ConditionalOnMissingBean(GrantResolver.class)`/`@ConditionalOnMissingBean(GroupMembershipResolver.class)`:

```java
@Component
public class LegacyGrantResolver implements GrantResolver {

    private final LegacyPermissionRepository repository;

    public LegacyGrantResolver(LegacyPermissionRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ResolvedGrant> resolveGrants(Subject subject) {
        return repository.findGrantsFor(subject.type(), subject.id()).stream()
                .map(row -> new ResolvedGrant(
                        row.isGlobal() ? Scope.GLOBAL : Scope.of(row.scopeType(), row.scopeId()),
                        row.permissions()))
                .toList();
    }
}

@Component
public class LegacyGroupMembershipResolver implements GroupMembershipResolver {

    @Override
    public Set<String> groupsOf(String userId) {
        return legacyGroupRepository.findGroupIdsOfUser(userId);
    }
}
```

Cả hai là `@FunctionalInterface` nên cũng dùng được lambda/method reference cho trường hợp đơn giản. Implementation phải an toàn khi gọi đồng thời (được gọi trên mọi request, hoặc mỗi khi cache miss nếu `javalibs.authz.cache.enabled=true`). `AuthzManagementService` (nếu vẫn dùng qua `javalibs-authz-jpa` cho phần ghi) không bắt buộc phải đi cùng resolver tự viết — có thể trộn: tự viết read path, giữ nguyên write path JPA, hoặc ngược lại tự viết toàn bộ.

## Giới hạn v1

1. **Không nested group**: `GroupMembershipResolver.groupsOf(userId)` chỉ trả về thành viên **trực tiếp** — group chứa group không được hỗ trợ; user không "kế thừa" quyền qua group cha của group mình thuộc về.
2. **Không có deny rule**: `PermissionEvaluator` thuần cộng dồn (additive-only) — không có cách nào để một grant tường minh "từ chối" một quyền đã được cấp bởi grant khác. Muốn thu hồi quyền, phải xóa grant tương ứng (`AuthzManagementService.revokeGrant`).
3. **Không có rate-limit tích hợp**: module không giới hạn tần suất gọi `PermissionChecker`/`AuthzManagementService`. Cần chống lạm dụng endpoint quản trị (tạo role/grant hàng loạt) hoặc bảo vệ endpoint `/auth/*` khỏi brute-force → dùng [javalibs-resilience](resilience.md) (rate limiter Resilience4j) ở tầng gọi.
