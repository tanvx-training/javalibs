# Kế hoạch triển khai: Hệ thống xác thực & phân quyền kiểu YouTrack

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Xây family `javalibs-authz` (engine phân quyền Permission → Role → Grant theo scope global/per-resource) và module `javalibs-security-issuer` (login, access+refresh token có rotation, logout) theo spec `docs/superpowers/specs/2026-07-17-auth-permission-system-design.md`.

**Architecture:** `javalibs-authz` theo chuỗi core → spring → jpa → autoconfigure → starter; core thuần Java định nghĩa SPI (`GrantResolver`, `GroupMembershipResolver`) và `PermissionEvaluator`; jpa cung cấp implementation mặc định (entity + Flyway + repository); đánh giá quyền server-side, cache Caffeine TTL. `javalibs-security-issuer` nằm trong family security, tái dùng `JwtValidationConfig`/`TokenBlacklist`/`UserContext` của `javalibs-security-core`.

**Tech Stack:** Java 21, Spring Boot 3.5.3, jjwt 0.12.6, spring-security-crypto (BCrypt), Caffeine (optional), Flyway + PostgreSQL, Testcontainers qua `javalibs-test`.

## Global Constraints

- groupId `io.javalibs`, version `1.0.0-SNAPSHOT`, parent POM kế thừa `javalibs-root` (family con kế thừa POM family, xem `javalibs-security/pom.xml`).
- Java 21, `spring-boot.version` 3.5.3, `jjwt.version` 0.12.6 (đã chốt trong root pom — không thêm property version mới trừ khi task yêu cầu).
- Module `*-core` KHÔNG phụ thuộc Spring.
- Trong `*-spring-boot-autoconfigure`: mọi bean `@ConditionalOnMissingBean`, mọi dependency tích hợp bên thứ ba `<optional>true</optional>`, đăng ký qua `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- Namespace cấu hình: `javalibs.authz.*` và `javalibs.security.issuer.*`.
- Mọi public API phải có Javadoc đầy đủ (tiếng Anh) theo phong cách `javalibs-security` hiện có — Javadoc trong plan này là bản rút gọn, executor viết đầy đủ (mô tả, `@param`, `@return`, `@throws`).
- JSON lỗi theo shape hiện có: `{"timestamp","status","code","message"}` (xem `RestAuthenticationEntryPoint`).
- Chạy lệnh Maven từ root worktree: `/Users/tanvx/Dev/javalibs-root/.claude/worktrees/auth-permission-system-0bea2a`.
- Test JPA/integration cần Docker (Testcontainers PostgreSQL qua `javalibs-test`).
- Commit message tiếng Việt, prefix conventional (`feat:`, `test:`, `docs:`, `chore:`), kết thúc bằng dòng `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.

---

### Task 1: Scaffold family `javalibs-authz` + đăng ký root POM và BOM

**Files:**
- Create: `javalibs-authz/pom.xml`
- Create: `javalibs-authz/javalibs-authz-core/pom.xml`
- Create: `javalibs-authz/javalibs-authz-spring/pom.xml`
- Create: `javalibs-authz/javalibs-authz-jpa/pom.xml`
- Create: `javalibs-authz/javalibs-authz-spring-boot-autoconfigure/pom.xml`
- Create: `javalibs-authz/javalibs-authz-spring-boot-starter/pom.xml`
- Modify: `pom.xml` (root — thêm module `javalibs-authz` sau `javalibs-security`)
- Modify: `javalibs-dependencies/pom.xml` (thêm 5 artifact authz vào `dependencyManagement`, theo đúng format các entry hiện có)

**Interfaces:**
- Produces: 5 artifact Maven `javalibs-authz-core`, `javalibs-authz-spring`, `javalibs-authz-jpa`, `javalibs-authz-spring-boot-autoconfigure`, `javalibs-authz-spring-boot-starter` build được, các task sau thêm code vào đó.

- [ ] **Step 1: Tạo POM family** — `javalibs-authz/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>io.javalibs</groupId>
    <artifactId>javalibs-root</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>..</relativePath>
  </parent>

  <artifactId>javalibs-authz</artifactId>
  <packaging>pom</packaging>

  <name>javalibs :: authz</name>
  <description>
    YouTrack-style authorization for javalibs based services: permissions bundled into roles,
    granted to users or groups either globally or per resource scope (project, organization...).
    Split into a Spring-free evaluation core, Spring integration (@RequirePermission,
    PermissionChecker, caching), a default JPA persistence implementation, Spring Boot
    auto-configuration and a one-dependency starter.
  </description>

  <modules>
    <module>javalibs-authz-core</module>
    <module>javalibs-authz-spring</module>
    <module>javalibs-authz-jpa</module>
    <module>javalibs-authz-spring-boot-autoconfigure</module>
    <module>javalibs-authz-spring-boot-starter</module>
  </modules>
</project>
```

- [ ] **Step 2: POM `javalibs-authz-core`** (thuần Java — chỉ slf4j + test deps, theo mẫu `javalibs-security-core/pom.xml` bỏ jjwt):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.javalibs</groupId>
    <artifactId>javalibs-authz</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>..</relativePath>
  </parent>
  <artifactId>javalibs-authz-core</artifactId>
  <name>javalibs :: authz :: core</name>
  <description>
    Framework-agnostic authorization core: Scope, Subject, ResolvedGrant, the GrantResolver /
    GroupMembershipResolver SPIs, the PermissionEvaluator engine and the PermissionCatalog.
    Pure Java, no Spring dependency.
  </description>
  <dependencies>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-api</artifactId>
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.assertj</groupId>
      <artifactId>assertj-core</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 3: POM `javalibs-authz-spring`**:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.javalibs</groupId>
    <artifactId>javalibs-authz</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>..</relativePath>
  </parent>
  <artifactId>javalibs-authz-spring</artifactId>
  <name>javalibs :: authz :: spring</name>
  <description>
    Spring integration for the javalibs authz core: @RequirePermission interceptor,
    the programmatic PermissionChecker and Caffeine-backed caching resolver decorators.
  </description>
  <dependencies>
    <dependency>
      <groupId>io.javalibs</groupId>
      <artifactId>javalibs-authz-core</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>io.javalibs</groupId>
      <artifactId>javalibs-security-spring</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-webmvc</artifactId>
    </dependency>
    <dependency>
      <groupId>jakarta.servlet</groupId>
      <artifactId>jakarta.servlet-api</artifactId>
      <scope>provided</scope>
    </dependency>
    <!-- Optional: caching decorators back off when Caffeine is absent -->
    <dependency>
      <groupId>com.github.ben-manes.caffeine</groupId>
      <artifactId>caffeine</artifactId>
      <optional>true</optional>
    </dependency>
    <!-- Test -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.security</groupId>
      <artifactId>spring-security-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 4: POM `javalibs-authz-jpa`**:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.javalibs</groupId>
    <artifactId>javalibs-authz</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>..</relativePath>
  </parent>
  <artifactId>javalibs-authz-jpa</artifactId>
  <name>javalibs :: authz :: jpa</name>
  <description>
    Default JPA persistence for javalibs authz: authz_* entities, Flyway migrations,
    Spring Data repositories, the JPA-backed resolver SPIs, the AuthzManagementService
    and (optionally, when javalibs-security-issuer is present) the issuer store adapters.
  </description>
  <dependencies>
    <dependency>
      <groupId>io.javalibs</groupId>
      <artifactId>javalibs-authz-core</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>org.springframework.data</groupId>
      <artifactId>spring-data-jpa</artifactId>
    </dependency>
    <dependency>
      <groupId>jakarta.persistence</groupId>
      <artifactId>jakarta.persistence-api</artifactId>
    </dependency>
    <!-- Optional: issuer store adapters back off when the issuer module is absent -->
    <dependency>
      <groupId>io.javalibs</groupId>
      <artifactId>javalibs-security-issuer</artifactId>
      <version>${project.version}</version>
      <optional>true</optional>
    </dependency>
    <!-- Test: full JPA stack + Testcontainers PostgreSQL -->
    <dependency>
      <groupId>io.javalibs</groupId>
      <artifactId>javalibs-test</artifactId>
      <version>${project.version}</version>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-core</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-database-postgresql</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

Lưu ý: dependency optional `javalibs-security-issuer` chỉ thêm được sau Task 12 — ở Task 1 hãy **bỏ block đó ra** (nếu để vào build sẽ fail vì artifact chưa tồn tại); Task 17 sẽ thêm lại.

- [ ] **Step 5: POM `javalibs-authz-spring-boot-autoconfigure`**:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.javalibs</groupId>
    <artifactId>javalibs-authz</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>..</relativePath>
  </parent>
  <artifactId>javalibs-authz-spring-boot-autoconfigure</artifactId>
  <name>javalibs :: authz :: spring-boot-autoconfigure</name>
  <description>
    Spring Boot auto-configuration for javalibs authz: javalibs.authz.* configuration
    properties, PermissionEvaluator / PermissionChecker / caching beans, Web MVC
    registration of the @RequirePermission interceptor and the optional JPA wiring.
  </description>
  <dependencies>
    <dependency>
      <groupId>io.javalibs</groupId>
      <artifactId>javalibs-authz-spring</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-autoconfigure</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-configuration-processor</artifactId>
      <optional>true</optional>
    </dependency>
    <dependency>
      <groupId>jakarta.servlet</groupId>
      <artifactId>jakarta.servlet-api</artifactId>
      <scope>provided</scope>
    </dependency>
    <!-- Optional integrations -->
    <dependency>
      <groupId>io.javalibs</groupId>
      <artifactId>javalibs-authz-jpa</artifactId>
      <version>${project.version}</version>
      <optional>true</optional>
    </dependency>
    <dependency>
      <groupId>org.springframework.data</groupId>
      <artifactId>spring-data-jpa</artifactId>
      <optional>true</optional>
    </dependency>
    <dependency>
      <groupId>com.github.ben-manes.caffeine</groupId>
      <artifactId>caffeine</artifactId>
      <optional>true</optional>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-core</artifactId>
      <optional>true</optional>
    </dependency>
    <!-- Test -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 6: POM `javalibs-authz-spring-boot-starter`** (không code, chỉ gom dependency):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.javalibs</groupId>
    <artifactId>javalibs-authz</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>..</relativePath>
  </parent>
  <artifactId>javalibs-authz-spring-boot-starter</artifactId>
  <name>javalibs :: authz :: spring-boot-starter</name>
  <description>
    Spring Boot starter for javalibs authz. Add this single dependency to get the
    @RequirePermission interceptor, PermissionChecker and Caffeine caching
    auto-configured. Pair with javalibs-authz-jpa for the default persistence.
    Contains no code of its own.
  </description>
  <dependencies>
    <dependency>
      <groupId>io.javalibs</groupId>
      <artifactId>javalibs-authz-core</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>io.javalibs</groupId>
      <artifactId>javalibs-authz-spring</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>io.javalibs</groupId>
      <artifactId>javalibs-authz-spring-boot-autoconfigure</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>com.github.ben-manes.caffeine</groupId>
      <artifactId>caffeine</artifactId>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 7: Đăng ký root + BOM.** Trong root `pom.xml` thêm `<module>javalibs-authz</module>` ngay sau `<module>javalibs-security</module>`. Trong `javalibs-dependencies/pom.xml` thêm 5 entry (theo format entry security hiện có, mỗi entry `<groupId>io.javalibs</groupId>`, `<version>${project.version}</version>`): `javalibs-authz-core`, `javalibs-authz-spring`, `javalibs-authz-jpa`, `javalibs-authz-spring-boot-autoconfigure`, `javalibs-authz-spring-boot-starter`.

- [ ] **Step 8: Verify build.**

Run: `mvn -q install -pl javalibs-authz -am -DskipTests`
Expected: BUILD SUCCESS (các module rỗng compile ok).

- [ ] **Step 9: Commit**

```bash
git add pom.xml javalibs-dependencies/pom.xml javalibs-authz
git commit -m "chore: scaffold family javalibs-authz (core/spring/jpa/autoconfigure/starter)"
```

---

### Task 2: authz-core — `Scope`, `SubjectType`, `Subject`

**Files:**
- Create: `javalibs-authz/javalibs-authz-core/src/main/java/io/javalibs/authz/Scope.java`
- Create: `javalibs-authz/javalibs-authz-core/src/main/java/io/javalibs/authz/SubjectType.java`
- Create: `javalibs-authz/javalibs-authz-core/src/main/java/io/javalibs/authz/Subject.java`
- Test: `javalibs-authz/javalibs-authz-core/src/test/java/io/javalibs/authz/ScopeTest.java`
- Test: `javalibs-authz/javalibs-authz-core/src/test/java/io/javalibs/authz/SubjectTest.java`

**Interfaces:**
- Produces: `Scope.GLOBAL`, `Scope.of(String type, String id)`, `scope.isGlobal()`, record accessors `type()`/`id()`; `Subject.user(String id)`, `Subject.group(String id)`, accessors `type()` (SubjectType) / `id()`; enum `SubjectType { USER, GROUP }`.

- [ ] **Step 1: Viết test fail**

```java
package io.javalibs.authz;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ScopeTest {

    @Test
    void globalScopeHasNoTypeAndNoId() {
        assertThat(Scope.GLOBAL.isGlobal()).isTrue();
        assertThat(Scope.GLOBAL.type()).isNull();
        assertThat(Scope.GLOBAL.id()).isNull();
    }

    @Test
    void ofCreatesResourceScope() {
        Scope scope = Scope.of("project", "42");
        assertThat(scope.isGlobal()).isFalse();
        assertThat(scope.type()).isEqualTo("project");
        assertThat(scope.id()).isEqualTo("42");
        assertThat(scope).isEqualTo(Scope.of("project", "42"));
        assertThat(scope).isNotEqualTo(Scope.of("project", "43"));
    }

    @Test
    void rejectsPartialScope() {
        assertThatIllegalArgumentException().isThrownBy(() -> Scope.of("project", null));
        assertThatIllegalArgumentException().isThrownBy(() -> Scope.of(null, "42"));
        assertThatIllegalArgumentException().isThrownBy(() -> Scope.of(" ", "42"));
        assertThatIllegalArgumentException().isThrownBy(() -> Scope.of("project", " "));
    }
}
```

```java
package io.javalibs.authz;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SubjectTest {

    @Test
    void createsUserAndGroupSubjects() {
        assertThat(Subject.user("u1")).isEqualTo(new Subject(SubjectType.USER, "u1"));
        assertThat(Subject.group("g1")).isEqualTo(new Subject(SubjectType.GROUP, "g1"));
    }

    @Test
    void rejectsBlankId() {
        assertThatIllegalArgumentException().isThrownBy(() -> Subject.user(" "));
        assertThatIllegalArgumentException().isThrownBy(() -> Subject.group(null));
    }
}
```

- [ ] **Step 2: Chạy test, xác nhận fail**

Run: `mvn -q test -pl javalibs-authz/javalibs-authz-core`
Expected: COMPILATION ERROR (Scope/Subject chưa tồn tại).

- [ ] **Step 3: Implement**

```java
package io.javalibs.authz;

/**
 * The scope a permission grant applies to: either {@link #GLOBAL} (the whole system) or a
 * concrete resource identified by a scope type and id, e.g. {@code ("project", "42")}.
 */
public record Scope(String type, String id) {

    /** Grant applies everywhere. */
    public static final Scope GLOBAL = new Scope(null, null);

    public Scope {
        boolean bothNull = type == null && id == null;
        boolean bothSet = hasText(type) && hasText(id);
        if (!bothNull && !bothSet) {
            throw new IllegalArgumentException(
                    "A scope is either GLOBAL (no type, no id) or a full (type, id) pair; got type="
                            + type + ", id=" + id);
        }
    }

    /** Creates a resource scope; both arguments must be non-blank. */
    public static Scope of(String type, String id) {
        return new Scope(type, id);
    }

    /** Returns whether this is the global scope. */
    public boolean isGlobal() {
        return type == null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
```

```java
package io.javalibs.authz;

/** The kind of subject a role can be granted to. */
public enum SubjectType {
    USER,
    GROUP
}
```

```java
package io.javalibs.authz;

import java.util.Objects;

/**
 * The receiver of a role grant: a user or a group, identified by a stable string id
 * (for users the JWT {@code sub} claim, for groups the group id).
 */
public record Subject(SubjectType type, String id) {

    public Subject {
        Objects.requireNonNull(type, "type must not be null");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Subject id must not be null or blank");
        }
    }

    /** Creates a user subject. */
    public static Subject user(String userId) {
        return new Subject(SubjectType.USER, userId);
    }

    /** Creates a group subject. */
    public static Subject group(String groupId) {
        return new Subject(SubjectType.GROUP, groupId);
    }
}
```

- [ ] **Step 4: Chạy test, xác nhận pass**

Run: `mvn -q test -pl javalibs-authz/javalibs-authz-core`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add javalibs-authz/javalibs-authz-core
git commit -m "feat(authz): Scope và Subject cho mô hình grant theo phạm vi"
```

---

### Task 3: authz-core — `ResolvedGrant`, SPI resolver và `PermissionEvaluator`

**Files:**
- Create: `javalibs-authz/javalibs-authz-core/src/main/java/io/javalibs/authz/ResolvedGrant.java`
- Create: `javalibs-authz/javalibs-authz-core/src/main/java/io/javalibs/authz/GrantResolver.java`
- Create: `javalibs-authz/javalibs-authz-core/src/main/java/io/javalibs/authz/GroupMembershipResolver.java`
- Create: `javalibs-authz/javalibs-authz-core/src/main/java/io/javalibs/authz/PermissionEvaluator.java`
- Test: `javalibs-authz/javalibs-authz-core/src/test/java/io/javalibs/authz/PermissionEvaluatorTest.java`

**Interfaces:**
- Consumes: `Scope`, `Subject` (Task 2).
- Produces:
  - `record ResolvedGrant(Scope scope, Set<String> permissions)`
  - `interface GrantResolver { List<ResolvedGrant> resolveGrants(Subject subject); }`
  - `interface GroupMembershipResolver { Set<String> groupsOf(String userId); }`
  - `class PermissionEvaluator { PermissionEvaluator(GrantResolver, GroupMembershipResolver); boolean hasPermission(String userId, String permission, Scope scope); Set<String> effectivePermissions(String userId, Scope scope); }`

- [ ] **Step 1: Viết test fail** — kiểm tra đủ ngữ nghĩa spec mục 3:

```java
package io.javalibs.authz;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionEvaluatorTest {

    private static final Scope PROJECT_42 = Scope.of("project", "42");
    private static final Scope PROJECT_43 = Scope.of("project", "43");

    private PermissionEvaluator evaluator(Map<Subject, List<ResolvedGrant>> grants,
            Map<String, Set<String>> groups) {
        return new PermissionEvaluator(
                subject -> grants.getOrDefault(subject, List.of()),
                userId -> groups.getOrDefault(userId, Set.of()));
    }

    @Test
    void directScopedGrantMatchesOnlyItsScope() {
        PermissionEvaluator evaluator = evaluator(
                Map.of(Subject.user("u1"),
                        List.of(new ResolvedGrant(PROJECT_42, Set.of("issue.read")))),
                Map.of());
        assertThat(evaluator.hasPermission("u1", "issue.read", PROJECT_42)).isTrue();
        assertThat(evaluator.hasPermission("u1", "issue.read", PROJECT_43)).isFalse();
        assertThat(evaluator.hasPermission("u1", "issue.read", Scope.GLOBAL)).isFalse();
        assertThat(evaluator.hasPermission("u1", "issue.update", PROJECT_42)).isFalse();
    }

    @Test
    void globalGrantCoversEveryScope() {
        PermissionEvaluator evaluator = evaluator(
                Map.of(Subject.user("u1"),
                        List.of(new ResolvedGrant(Scope.GLOBAL, Set.of("issue.read")))),
                Map.of());
        assertThat(evaluator.hasPermission("u1", "issue.read", Scope.GLOBAL)).isTrue();
        assertThat(evaluator.hasPermission("u1", "issue.read", PROJECT_42)).isTrue();
        assertThat(evaluator.hasPermission("u1", "issue.read", PROJECT_43)).isTrue();
    }

    @Test
    void groupGrantsAreUnionedWithUserGrants() {
        PermissionEvaluator evaluator = evaluator(
                Map.of(
                        Subject.user("u1"),
                        List.of(new ResolvedGrant(PROJECT_42, Set.of("issue.read"))),
                        Subject.group("g1"),
                        List.of(new ResolvedGrant(PROJECT_42, Set.of("issue.update")))),
                Map.of("u1", Set.of("g1")));
        assertThat(evaluator.effectivePermissions("u1", PROJECT_42))
                .containsExactlyInAnyOrder("issue.read", "issue.update");
    }

    @Test
    void userWithoutGrantsHasNoPermissions() {
        PermissionEvaluator evaluator = evaluator(Map.of(), Map.of());
        assertThat(evaluator.hasPermission("nobody", "issue.read", Scope.GLOBAL)).isFalse();
        assertThat(evaluator.effectivePermissions("nobody", PROJECT_42)).isEmpty();
    }
}
```

- [ ] **Step 2: Chạy test, xác nhận fail**

Run: `mvn -q test -pl javalibs-authz/javalibs-authz-core`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement**

```java
package io.javalibs.authz;

import java.util.Objects;
import java.util.Set;

/**
 * A role grant already joined with its role's permissions: "these permissions apply in
 * this scope". Produced by a {@link GrantResolver}.
 */
public record ResolvedGrant(Scope scope, Set<String> permissions) {

    public ResolvedGrant {
        Objects.requireNonNull(scope, "scope must not be null");
        permissions = (permissions == null) ? Set.of() : Set.copyOf(permissions);
    }

    /** Returns whether this grant applies to the requested scope. */
    public boolean appliesTo(Scope requested) {
        return scope.isGlobal() || scope.equals(requested);
    }
}
```

```java
package io.javalibs.authz;

import java.util.List;

/**
 * SPI: loads every grant of a single subject, with the role's permissions already joined in.
 * Implementations must be safe to call concurrently. javalibs-authz-jpa ships the default
 * database-backed implementation; applications with their own schema implement this instead.
 */
@FunctionalInterface
public interface GrantResolver {

    /** Returns all grants of the subject, never {@code null}. */
    List<ResolvedGrant> resolveGrants(Subject subject);
}
```

```java
package io.javalibs.authz;

import java.util.Set;

/**
 * SPI: resolves the group ids a user belongs to (direct membership only — groups do not nest).
 */
@FunctionalInterface
public interface GroupMembershipResolver {

    /** Returns the ids of the groups the user is a direct member of, never {@code null}. */
    Set<String> groupsOf(String userId);
}
```

```java
package io.javalibs.authz;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Evaluates effective permissions, YouTrack-style and purely additive (no deny rules):
 * a user has permission P in scope S when any grant of the user itself or of a group the
 * user belongs to carries P and is either global or exactly scoped to S.
 */
public final class PermissionEvaluator {

    private final GrantResolver grantResolver;
    private final GroupMembershipResolver groupMembershipResolver;

    public PermissionEvaluator(GrantResolver grantResolver,
            GroupMembershipResolver groupMembershipResolver) {
        this.grantResolver = Objects.requireNonNull(grantResolver, "grantResolver must not be null");
        this.groupMembershipResolver = Objects.requireNonNull(
                groupMembershipResolver, "groupMembershipResolver must not be null");
    }

    /** Returns whether the user holds the permission in the given scope. */
    public boolean hasPermission(String userId, String permission, Scope scope) {
        Objects.requireNonNull(permission, "permission must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        for (Subject subject : subjectsOf(userId)) {
            for (ResolvedGrant grant : grantResolver.resolveGrants(subject)) {
                if (grant.appliesTo(scope) && grant.permissions().contains(permission)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Returns every permission the user holds in the given scope. */
    public Set<String> effectivePermissions(String userId, Scope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        Set<String> permissions = new LinkedHashSet<>();
        for (Subject subject : subjectsOf(userId)) {
            for (ResolvedGrant grant : grantResolver.resolveGrants(subject)) {
                if (grant.appliesTo(scope)) {
                    permissions.addAll(grant.permissions());
                }
            }
        }
        return permissions;
    }

    private Set<Subject> subjectsOf(String userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Set<Subject> subjects = new LinkedHashSet<>();
        subjects.add(Subject.user(userId));
        for (String groupId : groupMembershipResolver.groupsOf(userId)) {
            subjects.add(Subject.group(groupId));
        }
        return subjects;
    }
}
```

- [ ] **Step 4: Chạy test, xác nhận pass**

Run: `mvn -q test -pl javalibs-authz/javalibs-authz-core`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add javalibs-authz/javalibs-authz-core
git commit -m "feat(authz): PermissionEvaluator với SPI GrantResolver/GroupMembershipResolver"
```

---

### Task 4: authz-core — `PermissionCatalog`

**Files:**
- Create: `javalibs-authz/javalibs-authz-core/src/main/java/io/javalibs/authz/PermissionCatalog.java`
- Create: `javalibs-authz/javalibs-authz-core/src/main/java/io/javalibs/authz/UnknownPermissionException.java`
- Test: `javalibs-authz/javalibs-authz-core/src/test/java/io/javalibs/authz/PermissionCatalogTest.java`

**Interfaces:**
- Produces: `class PermissionCatalog { PermissionCatalog(Collection<String> codes); Set<String> codes(); boolean contains(String code); void requireKnown(Collection<String> codes) throws UnknownPermissionException; }`; `class UnknownPermissionException extends RuntimeException`.

- [ ] **Step 1: Viết test fail**

```java
package io.javalibs.authz;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PermissionCatalogTest {

    @Test
    void knowsRegisteredCodes() {
        PermissionCatalog catalog = new PermissionCatalog(Set.of("issue.read", "issue.update"));
        assertThat(catalog.contains("issue.read")).isTrue();
        assertThat(catalog.contains("issue.delete")).isFalse();
        assertThat(catalog.codes()).containsExactlyInAnyOrder("issue.read", "issue.update");
    }

    @Test
    void requireKnownThrowsListingUnknownCodes() {
        PermissionCatalog catalog = new PermissionCatalog(Set.of("issue.read"));
        catalog.requireKnown(List.of("issue.read"));
        assertThatExceptionOfType(UnknownPermissionException.class)
                .isThrownBy(() -> catalog.requireKnown(List.of("issue.read", "bogus.perm")))
                .withMessageContaining("bogus.perm");
    }

    @Test
    void rejectsEmptyCatalogAndBlankCodes() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PermissionCatalog(Set.of()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PermissionCatalog(Set.of(" ")));
    }
}
```

- [ ] **Step 2: Chạy test, xác nhận fail**

Run: `mvn -q test -pl javalibs-authz/javalibs-authz-core`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement**

```java
package io.javalibs.authz;

/** Thrown when a permission code is not part of the application's {@link PermissionCatalog}. */
public class UnknownPermissionException extends RuntimeException {

    public UnknownPermissionException(String message) {
        super(message);
    }
}
```

```java
package io.javalibs.authz;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Optional catalog of every permission code the application defines. When the application
 * exposes one as a bean, writes through AuthzManagementService fail fast on unknown codes
 * instead of silently storing typos.
 */
public final class PermissionCatalog {

    private final Set<String> codes;

    public PermissionCatalog(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            throw new IllegalArgumentException("PermissionCatalog requires at least one code");
        }
        Set<String> copy = new LinkedHashSet<>();
        for (String code : codes) {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("Permission codes must not be null or blank");
            }
            copy.add(code.trim());
        }
        this.codes = Set.copyOf(copy);
    }

    /** Returns all registered codes, unmodifiable. */
    public Set<String> codes() {
        return codes;
    }

    /** Returns whether the code is registered. */
    public boolean contains(String code) {
        return code != null && codes.contains(code);
    }

    /**
     * Validates every given code, throwing an {@link UnknownPermissionException} naming the
     * offending codes when at least one is not registered.
     */
    public void requireKnown(Collection<String> candidates) {
        Set<String> unknown = new LinkedHashSet<>();
        for (String candidate : candidates) {
            if (!contains(candidate)) {
                unknown.add(candidate);
            }
        }
        if (!unknown.isEmpty()) {
            throw new UnknownPermissionException(
                    "Unknown permission codes " + unknown + ". Registered codes: " + codes
                            + ". Add them to the PermissionCatalog bean or fix the typo.");
        }
    }
}
```

- [ ] **Step 4: Chạy test, xác nhận pass**

Run: `mvn -q test -pl javalibs-authz/javalibs-authz-core`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add javalibs-authz/javalibs-authz-core
git commit -m "feat(authz): PermissionCatalog fail-fast cho permission code lạ"
```

---

### Task 5: authz-spring — `PermissionChecker`

**Files:**
- Create: `javalibs-authz/javalibs-authz-spring/src/main/java/io/javalibs/authz/spring/PermissionChecker.java`
- Test: `javalibs-authz/javalibs-authz-spring/src/test/java/io/javalibs/authz/spring/PermissionCheckerTest.java`

**Interfaces:**
- Consumes: `PermissionEvaluator`, `Scope` (Task 3); `UserContextHolder`, `UserContextAuthenticationToken` từ `javalibs-security-spring`; `org.springframework.security.access.AccessDeniedException`.
- Produces: `class PermissionChecker { PermissionChecker(PermissionEvaluator); boolean check(String permission, Scope scope); boolean check(String userId, String permission, Scope scope); void require(String permission, Scope scope); void require(String userId, String permission, Scope scope); }` — các overload không có `userId` lấy user hiện tại từ `UserContextHolder`; `check` trả `false` khi chưa đăng nhập, `require` ném `AccessDeniedException`.

- [ ] **Step 1: Viết test fail**

```java
package io.javalibs.authz.spring;

import io.javalibs.authz.PermissionEvaluator;
import io.javalibs.authz.ResolvedGrant;
import io.javalibs.authz.Scope;
import io.javalibs.authz.Subject;
import io.javalibs.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class PermissionCheckerTest {

    private static final Scope PROJECT_42 = Scope.of("project", "42");

    private final PermissionChecker checker = new PermissionChecker(new PermissionEvaluator(
            subject -> subject.equals(Subject.user("u1"))
                    ? List.of(new ResolvedGrant(PROJECT_42, Set.of("issue.read")))
                    : List.of(),
            userId -> Set.of()));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String userId) {
        UserContext user = UserContext.builder().userId(userId).build();
        SecurityContextHolder.getContext().setAuthentication(
                new io.javalibs.security.spring.UserContextAuthenticationToken(user));
    }

    @Test
    void checksExplicitUserId() {
        assertThat(checker.check("u1", "issue.read", PROJECT_42)).isTrue();
        assertThat(checker.check("u2", "issue.read", PROJECT_42)).isFalse();
    }

    @Test
    void usesCurrentUserWhenNoUserIdGiven() {
        authenticateAs("u1");
        assertThat(checker.check("issue.read", PROJECT_42)).isTrue();
        checker.require("issue.read", PROJECT_42);
    }

    @Test
    void checkIsFalseAndRequireThrowsWhenUnauthenticated() {
        assertThat(checker.check("issue.read", PROJECT_42)).isFalse();
        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> checker.require("issue.read", PROJECT_42));
    }

    @Test
    void requireThrowsWhenPermissionMissing() {
        authenticateAs("u1");
        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> checker.require("issue.delete", PROJECT_42))
                .withMessageContaining("issue.delete");
    }
}
```

Lưu ý: nếu constructor `UserContextAuthenticationToken` khác chữ ký (kiểm tra file `javalibs-security-spring/.../UserContextAuthenticationToken.java` trước), chỉnh helper `authenticateAs` theo constructor thật.

- [ ] **Step 2: Chạy test, xác nhận fail**

Run: `mvn -q test -pl javalibs-authz/javalibs-authz-spring`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement**

```java
package io.javalibs.authz.spring;

import io.javalibs.authz.PermissionEvaluator;
import io.javalibs.authz.Scope;
import io.javalibs.security.UserContext;
import io.javalibs.security.spring.UserContextHolder;
import org.springframework.security.access.AccessDeniedException;

import java.util.Objects;
import java.util.Optional;

/**
 * Programmatic permission checks for services and controllers. The no-{@code userId}
 * overloads resolve the current caller from {@link UserContextHolder}; {@code check}
 * returns {@code false} for anonymous callers while {@code require} throws
 * {@link AccessDeniedException} (translated to 403/401 by the security layer).
 */
public class PermissionChecker {

    private final PermissionEvaluator evaluator;

    public PermissionChecker(PermissionEvaluator evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator must not be null");
    }

    /** Returns whether the current caller holds the permission in the scope. */
    public boolean check(String permission, Scope scope) {
        Optional<UserContext> user = UserContextHolder.current();
        return user.isPresent() && check(user.get().userId(), permission, scope);
    }

    /** Returns whether the given user holds the permission in the scope. */
    public boolean check(String userId, String permission, Scope scope) {
        return evaluator.hasPermission(userId, permission, scope);
    }

    /** Asserts the current caller holds the permission in the scope. */
    public void require(String permission, Scope scope) {
        UserContext user = UserContextHolder.current().orElseThrow(
                () -> new AccessDeniedException(
                        "Access denied: authentication is required to access this resource"));
        require(user.userId(), permission, scope);
    }

    /** Asserts the given user holds the permission in the scope. */
    public void require(String userId, String permission, Scope scope) {
        if (!check(userId, permission, scope)) {
            throw new AccessDeniedException("Access denied: caller does not have permission '"
                    + permission + "' in scope " + describe(scope));
        }
    }

    private static String describe(Scope scope) {
        return scope.isGlobal() ? "GLOBAL" : scope.type() + ":" + scope.id();
    }
}
```

- [ ] **Step 4: Chạy test, xác nhận pass**

Run: `mvn -q test -pl javalibs-authz/javalibs-authz-spring`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add javalibs-authz/javalibs-authz-spring
git commit -m "feat(authz): PermissionChecker programmatic gắn với UserContextHolder"
```

---

### Task 6: authz-spring — `@RequirePermission` + interceptor

**Files:**
- Create: `javalibs-authz/javalibs-authz-spring/src/main/java/io/javalibs/authz/spring/RequirePermission.java`
- Create: `javalibs-authz/javalibs-authz-spring/src/main/java/io/javalibs/authz/spring/RequirePermissionInterceptor.java`
- Test: `javalibs-authz/javalibs-authz-spring/src/test/java/io/javalibs/authz/spring/RequirePermissionInterceptorTest.java`

**Interfaces:**
- Consumes: `PermissionChecker` (Task 5), `Scope`.
- Produces: annotation `@RequirePermission(value, scopeType default "", scopeIdParam default "")` (METHOD + TYPE); `class RequirePermissionInterceptor implements HandlerInterceptor { RequirePermissionInterceptor(PermissionChecker); }`. Scope resolve từ path variable (request attribute `HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE`); không khai `scopeType` → check GLOBAL; khai `scopeType` mà thiếu path variable → `IllegalStateException` (lỗi cấu hình, 500).

- [ ] **Step 1: Viết test fail**

```java
package io.javalibs.authz.spring;

import io.javalibs.authz.PermissionEvaluator;
import io.javalibs.authz.ResolvedGrant;
import io.javalibs.authz.Scope;
import io.javalibs.authz.Subject;
import io.javalibs.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class RequirePermissionInterceptorTest {

    static class TestController {
        @RequirePermission(value = "issue.read", scopeType = "project", scopeIdParam = "projectId")
        public void scoped() { }

        @RequirePermission("system.admin")
        public void global() { }

        public void unannotated() { }
    }

    private final RequirePermissionInterceptor interceptor = new RequirePermissionInterceptor(
            new PermissionChecker(new PermissionEvaluator(
                    subject -> subject.equals(Subject.user("u1"))
                            ? List.of(new ResolvedGrant(Scope.of("project", "42"),
                                    Set.of("issue.read")))
                            : List.of(),
                    userId -> Set.of())));

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new io.javalibs.security.spring.UserContextAuthenticationToken(
                        UserContext.builder().userId("u1").build()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private HandlerMethod handler(String methodName) throws Exception {
        return new HandlerMethod(new TestController(),
                TestController.class.getMethod(methodName));
    }

    @Test
    void allowsWhenScopedPermissionGranted() throws Exception {
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                Map.of("projectId", "42"));
        assertThat(interceptor.preHandle(request, response, handler("scoped"))).isTrue();
    }

    @Test
    void deniesWhenScopeDiffers() throws Exception {
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                Map.of("projectId", "43"));
        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> interceptor.preHandle(request, response, handler("scoped")));
    }

    @Test
    void deniesGlobalPermissionUserDoesNotHold() throws Exception {
        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> interceptor.preHandle(request, response, handler("global")));
    }

    @Test
    void failsFastWhenScopeIdPathVariableMissing() throws Exception {
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of());
        assertThatIllegalStateException()
                .isThrownBy(() -> interceptor.preHandle(request, response, handler("scoped")))
                .withMessageContaining("projectId");
    }

    @Test
    void allowsUnannotatedHandlerAndNonHandlerMethod() throws Exception {
        assertThat(interceptor.preHandle(request, response, handler("unannotated"))).isTrue();
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }
}
```

- [ ] **Step 2: Chạy test, xác nhận fail**

Run: `mvn -q test -pl javalibs-authz/javalibs-authz-spring`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement**

```java
package io.javalibs.authz.spring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the permission required to invoke a controller method (or every method of a
 * controller class). Method-level annotations take precedence over class-level ones.
 *
 * <pre>{@code
 * @RequirePermission("system.admin")                       // global scope
 * @RequirePermission(value = "issue.read",
 *         scopeType = "project", scopeIdParam = "projectId") // scope from path variable
 * }</pre>
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /** The required permission code. */
    String value();

    /** Scope type of the check; empty (default) means the global scope. */
    String scopeType() default "";

    /** Name of the path variable carrying the scope id; required when scopeType is set. */
    String scopeIdParam() default "";
}
```

```java
package io.javalibs.authz.spring;

import io.javalibs.authz.Scope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.Objects;

/**
 * {@link HandlerInterceptor} enforcing {@link RequirePermission @RequirePermission} on
 * controller handler methods and classes, mirroring the RequireRoleInterceptor of
 * javalibs-security. Violations raise Spring Security's AccessDeniedException (403, or
 * 401 for anonymous callers).
 */
public class RequirePermissionInterceptor implements HandlerInterceptor {

    private final PermissionChecker permissionChecker;

    public RequirePermissionInterceptor(PermissionChecker permissionChecker) {
        this.permissionChecker = Objects.requireNonNull(
                permissionChecker, "permissionChecker must not be null");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
            Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequirePermission annotation = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(
                    handlerMethod.getBeanType(), RequirePermission.class);
        }
        if (annotation == null) {
            return true;
        }
        permissionChecker.require(annotation.value(), resolveScope(request, annotation));
        return true;
    }

    private static Scope resolveScope(HttpServletRequest request, RequirePermission annotation) {
        if (annotation.scopeType().isEmpty()) {
            return Scope.GLOBAL;
        }
        if (annotation.scopeIdParam().isEmpty()) {
            throw new IllegalStateException("@RequirePermission(\"" + annotation.value()
                    + "\") declares scopeType='" + annotation.scopeType()
                    + "' but no scopeIdParam. Declare the path variable name holding the scope id.");
        }
        Object rawVariables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        Object scopeId = (rawVariables instanceof Map<?, ?> variables)
                ? variables.get(annotation.scopeIdParam())
                : null;
        if (scopeId == null || scopeId.toString().isBlank()) {
            throw new IllegalStateException("@RequirePermission(\"" + annotation.value()
                    + "\") expects path variable '" + annotation.scopeIdParam()
                    + "' but the request has no such URI template variable. "
                    + "Check the @GetMapping/@PostMapping path.");
        }
        return Scope.of(annotation.scopeType(), scopeId.toString());
    }
}
```

- [ ] **Step 4: Chạy test, xác nhận pass**

Run: `mvn -q test -pl javalibs-authz/javalibs-authz-spring`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add javalibs-authz/javalibs-authz-spring
git commit -m "feat(authz): @RequirePermission interceptor với scope từ path variable"
```

---

### Task 7: authz-spring — caching Caffeine + `AuthzCacheInvalidator`

**Files:**
- Create: `javalibs-authz/javalibs-authz-core/src/main/java/io/javalibs/authz/AuthzCacheInvalidator.java`
- Create: `javalibs-authz/javalibs-authz-spring/src/main/java/io/javalibs/authz/spring/CachingGrantResolver.java`
- Create: `javalibs-authz/javalibs-authz-spring/src/main/java/io/javalibs/authz/spring/CachingGroupMembershipResolver.java`
- Create: `javalibs-authz/javalibs-authz-spring/src/main/java/io/javalibs/authz/spring/CachingAuthzInvalidator.java`
- Test: `javalibs-authz/javalibs-authz-spring/src/test/java/io/javalibs/authz/spring/CachingResolversTest.java`

**Interfaces:**
- Consumes: `GrantResolver`, `GroupMembershipResolver`, `Subject`, `ResolvedGrant` (Task 3).
- Produces:
  - core: `interface AuthzCacheInvalidator { void evictSubject(Subject subject); void evictUser(String userId); void evictAll(); }`
  - `class CachingGrantResolver implements GrantResolver { CachingGrantResolver(GrantResolver delegate, Duration ttl, long maxSize); void evictSubject(Subject); void evictAll(); }`
  - `class CachingGroupMembershipResolver implements GroupMembershipResolver { CachingGroupMembershipResolver(GroupMembershipResolver delegate, Duration ttl, long maxSize); void evictUser(String); void evictAll(); }`
  - `class CachingAuthzInvalidator implements AuthzCacheInvalidator { CachingAuthzInvalidator(CachingGrantResolver, CachingGroupMembershipResolver); }` — `evictUser` evict cả membership lẫn grant của `Subject.user(userId)`.

- [ ] **Step 1: Viết test fail**

```java
package io.javalibs.authz.spring;

import io.javalibs.authz.ResolvedGrant;
import io.javalibs.authz.Scope;
import io.javalibs.authz.Subject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CachingResolversTest {

    @Test
    void grantResolverCachesPerSubjectAndEvicts() {
        AtomicInteger calls = new AtomicInteger();
        CachingGrantResolver resolver = new CachingGrantResolver(subject -> {
            calls.incrementAndGet();
            return List.of(new ResolvedGrant(Scope.GLOBAL, Set.of("p")));
        }, Duration.ofMinutes(1), 100);

        Subject u1 = Subject.user("u1");
        resolver.resolveGrants(u1);
        resolver.resolveGrants(u1);
        assertThat(calls.get()).isEqualTo(1);

        resolver.evictSubject(u1);
        resolver.resolveGrants(u1);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void groupResolverCachesPerUserAndEvicts() {
        AtomicInteger calls = new AtomicInteger();
        CachingGroupMembershipResolver resolver = new CachingGroupMembershipResolver(userId -> {
            calls.incrementAndGet();
            return Set.of("g1");
        }, Duration.ofMinutes(1), 100);

        resolver.groupsOf("u1");
        resolver.groupsOf("u1");
        assertThat(calls.get()).isEqualTo(1);

        resolver.evictUser("u1");
        resolver.groupsOf("u1");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void invalidatorEvictsUserFromBothCaches() {
        AtomicInteger grantCalls = new AtomicInteger();
        AtomicInteger groupCalls = new AtomicInteger();
        CachingGrantResolver grants = new CachingGrantResolver(subject -> {
            grantCalls.incrementAndGet();
            return List.of();
        }, Duration.ofMinutes(1), 100);
        CachingGroupMembershipResolver groups = new CachingGroupMembershipResolver(userId -> {
            groupCalls.incrementAndGet();
            return Set.of();
        }, Duration.ofMinutes(1), 100);
        CachingAuthzInvalidator invalidator = new CachingAuthzInvalidator(grants, groups);

        grants.resolveGrants(Subject.user("u1"));
        groups.groupsOf("u1");
        invalidator.evictUser("u1");
        grants.resolveGrants(Subject.user("u1"));
        groups.groupsOf("u1");

        assertThat(grantCalls.get()).isEqualTo(2);
        assertThat(groupCalls.get()).isEqualTo(2);
    }
}
```

- [ ] **Step 2: Chạy test, xác nhận fail**

Run: `mvn -q test -pl javalibs-authz/javalibs-authz-spring`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement**

`AuthzCacheInvalidator` (đặt ở **core** để authz-jpa dùng được mà không phụ thuộc authz-spring):

```java
package io.javalibs.authz;

/**
 * Eviction hook for authorization caches. AuthzManagementService calls this after every
 * grant / membership mutation so permission changes take effect immediately on the local
 * instance; across instances the cache TTL is the safety net.
 */
public interface AuthzCacheInvalidator {

    /** Evicts every cached grant of the subject. */
    void evictSubject(Subject subject);

    /** Evicts the user's cached group memberships and user-level grants. */
    void evictUser(String userId);

    /** Clears all authorization caches. */
    void evictAll();
}
```

```java
package io.javalibs.authz.spring;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.javalibs.authz.GrantResolver;
import io.javalibs.authz.ResolvedGrant;
import io.javalibs.authz.Subject;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Caffeine-backed decorator caching {@link GrantResolver#resolveGrants(Subject)} per subject
 * with a fixed TTL (expire-after-write).
 */
public final class CachingGrantResolver implements GrantResolver {

    private final GrantResolver delegate;
    private final Cache<Subject, List<ResolvedGrant>> cache;

    public CachingGrantResolver(GrantResolver delegate, Duration ttl, long maxSize) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maxSize)
                .build();
    }

    @Override
    public List<ResolvedGrant> resolveGrants(Subject subject) {
        return cache.get(subject, delegate::resolveGrants);
    }

    /** Evicts the cached grants of the subject. */
    public void evictSubject(Subject subject) {
        cache.invalidate(subject);
    }

    /** Clears the cache. */
    public void evictAll() {
        cache.invalidateAll();
    }
}
```

```java
package io.javalibs.authz.spring;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.javalibs.authz.GroupMembershipResolver;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * Caffeine-backed decorator caching {@link GroupMembershipResolver#groupsOf(String)} per user
 * with a fixed TTL (expire-after-write).
 */
public final class CachingGroupMembershipResolver implements GroupMembershipResolver {

    private final GroupMembershipResolver delegate;
    private final Cache<String, Set<String>> cache;

    public CachingGroupMembershipResolver(GroupMembershipResolver delegate, Duration ttl,
            long maxSize) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maxSize)
                .build();
    }

    @Override
    public Set<String> groupsOf(String userId) {
        return cache.get(userId, delegate::groupsOf);
    }

    /** Evicts the cached memberships of the user. */
    public void evictUser(String userId) {
        cache.invalidate(userId);
    }

    /** Clears the cache. */
    public void evictAll() {
        cache.invalidateAll();
    }
}
```

```java
package io.javalibs.authz.spring;

import io.javalibs.authz.AuthzCacheInvalidator;
import io.javalibs.authz.Subject;
import io.javalibs.authz.SubjectType;

import java.util.Objects;

/** {@link AuthzCacheInvalidator} evicting both Caffeine-backed resolver caches. */
public final class CachingAuthzInvalidator implements AuthzCacheInvalidator {

    private final CachingGrantResolver grantResolver;
    private final CachingGroupMembershipResolver groupMembershipResolver;

    public CachingAuthzInvalidator(CachingGrantResolver grantResolver,
            CachingGroupMembershipResolver groupMembershipResolver) {
        this.grantResolver = Objects.requireNonNull(grantResolver);
        this.groupMembershipResolver = Objects.requireNonNull(groupMembershipResolver);
    }

    @Override
    public void evictSubject(Subject subject) {
        grantResolver.evictSubject(subject);
        if (subject.type() == SubjectType.USER) {
            groupMembershipResolver.evictUser(subject.id());
        }
    }

    @Override
    public void evictUser(String userId) {
        grantResolver.evictSubject(Subject.user(userId));
        groupMembershipResolver.evictUser(userId);
    }

    @Override
    public void evictAll() {
        grantResolver.evictAll();
        groupMembershipResolver.evictAll();
    }
}
```

- [ ] **Step 4: Chạy test, xác nhận pass**

Run: `mvn -q test -pl javalibs-authz/javalibs-authz-core,javalibs-authz/javalibs-authz-spring`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add javalibs-authz/javalibs-authz-core javalibs-authz/javalibs-authz-spring
git commit -m "feat(authz): cache Caffeine cho resolver + AuthzCacheInvalidator"
```

---

### Task 8: authz-jpa — entity, Flyway migration, repository

**Files:**
- Create: `javalibs-authz/javalibs-authz-jpa/src/main/resources/db/migration/javalibs-authz/V1__authz_init.sql`
- Create: `javalibs-authz/javalibs-authz-jpa/src/main/java/io/javalibs/authz/jpa/AuthzUserEntity.java`
- Create: `javalibs-authz/javalibs-authz-jpa/src/main/java/io/javalibs/authz/jpa/AuthzGroupEntity.java`
- Create: `javalibs-authz/javalibs-authz-jpa/src/main/java/io/javalibs/authz/jpa/AuthzGroupMemberEntity.java`
- Create: `javalibs-authz/javalibs-authz-jpa/src/main/java/io/javalibs/authz/jpa/AuthzRoleEntity.java`
- Create: `javalibs-authz/javalibs-authz-jpa/src/main/java/io/javalibs/authz/jpa/AuthzRoleGrantEntity.java`
- Create: `javalibs-authz/javalibs-authz-jpa/src/main/java/io/javalibs/authz/jpa/AuthzUserRepository.java`
- Create: `javalibs-authz/javalibs-authz-jpa/src/main/java/io/javalibs/authz/jpa/AuthzGroupRepository.java`
- Create: `javalibs-authz/javalibs-authz-jpa/src/main/java/io/javalibs/authz/jpa/AuthzGroupMemberRepository.java`
- Create: `javalibs-authz/javalibs-authz-jpa/src/main/java/io/javalibs/authz/jpa/AuthzRoleRepository.java`
- Create: `javalibs-authz/javalibs-authz-jpa/src/main/java/io/javalibs/authz/jpa/AuthzRoleGrantRepository.java`
- Create (test app): `javalibs-authz/javalibs-authz-jpa/src/test/java/io/javalibs/authz/jpa/AuthzJpaTestApplication.java`
- Create (test config): `javalibs-authz/javalibs-authz-jpa/src/test/resources/application-test.yml`
- Test: `javalibs-authz/javalibs-authz-jpa/src/test/java/io/javalibs/authz/jpa/AuthzJpaSchemaIT.java`

**Interfaces:**
- Produces: 5 entity + 5 repository. Quan trọng cho task sau:
  - `AuthzRoleGrantRepository.findWithRoleBySubject(SubjectType subjectType, String subjectId)` trả `List<AuthzRoleGrantEntity>` (join fetch role + permissions).
  - `AuthzGroupMemberRepository.findGroupIdsByUserId(UUID userId)` trả `List<UUID>`.
  - `AuthzUserRepository.findByUsername(String)` trả `Optional<AuthzUserEntity>`.
  - `AuthzRoleRepository.findByRoleKey(String)` trả `Optional<AuthzRoleEntity>`.
  - Quy ước scope trong DB: cột `scope_type`/`scope_id` `NOT NULL DEFAULT ''` — chuỗi rỗng nghĩa là GLOBAL (tránh phụ thuộc `UNIQUE NULLS NOT DISTINCT` của PG15+).

- [ ] **Step 1: Viết migration** — `V1__authz_init.sql`:

```sql
CREATE TABLE authz_user (
    id            UUID PRIMARY KEY,
    username      VARCHAR(150) NOT NULL UNIQUE,
    email         VARCHAR(320),
    password_hash VARCHAR(200) NOT NULL,
    display_name  VARCHAR(200),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE authz_group (
    id          UUID PRIMARY KEY,
    name        VARCHAR(150) NOT NULL UNIQUE,
    description VARCHAR(500)
);

CREATE TABLE authz_group_member (
    id       UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES authz_group (id) ON DELETE CASCADE,
    user_id  UUID NOT NULL REFERENCES authz_user (id) ON DELETE CASCADE,
    CONSTRAINT uq_authz_group_member UNIQUE (group_id, user_id)
);
CREATE INDEX idx_authz_group_member_user ON authz_group_member (user_id);

CREATE TABLE authz_role (
    id          UUID PRIMARY KEY,
    role_key    VARCHAR(150) NOT NULL UNIQUE,
    name        VARCHAR(200) NOT NULL,
    description VARCHAR(500)
);

CREATE TABLE authz_role_permission (
    role_id    UUID         NOT NULL REFERENCES authz_role (id) ON DELETE CASCADE,
    permission VARCHAR(150) NOT NULL,
    PRIMARY KEY (role_id, permission)
);

-- scope_type/scope_id = '' (chuỗi rỗng) nghĩa là GLOBAL; tránh NULL để UNIQUE hoạt động
CREATE TABLE authz_role_grant (
    id           UUID PRIMARY KEY,
    subject_type VARCHAR(10)  NOT NULL,
    subject_id   VARCHAR(64)  NOT NULL,
    role_id      UUID         NOT NULL REFERENCES authz_role (id) ON DELETE CASCADE,
    scope_type   VARCHAR(100) NOT NULL DEFAULT '',
    scope_id     VARCHAR(100) NOT NULL DEFAULT '',
    CONSTRAINT chk_authz_grant_scope CHECK ((scope_type = '') = (scope_id = '')),
    CONSTRAINT uq_authz_role_grant UNIQUE (subject_type, subject_id, role_id, scope_type, scope_id)
);
CREATE INDEX idx_authz_role_grant_subject ON authz_role_grant (subject_type, subject_id);
```

- [ ] **Step 2: Viết entity.** Tất cả dùng field access, getter/setter thường (theo phong cách codebase — không Lombok):

```java
package io.javalibs.authz.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** JPA mapping of the {@code authz_user} table. */
@Entity
@Table(name = "authz_user")
public class AuthzUserEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 150)
    private String username;

    @Column(length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 200)
    private String passwordHash;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    // getters/setters cho mọi field (executor sinh đủ)
}
```

```java
package io.javalibs.authz.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.util.UUID;

/** JPA mapping of the {@code authz_group} table. */
@Entity
@Table(name = "authz_group")
public class AuthzGroupEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    // getters/setters
}
```

```java
package io.javalibs.authz.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/** JPA mapping of the {@code authz_group_member} table (direct user-group membership). */
@Entity
@Table(name = "authz_group_member",
        uniqueConstraints = @UniqueConstraint(name = "uq_authz_group_member",
                columnNames = {"group_id", "user_id"}))
public class AuthzGroupMemberEntity {

    @Id
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    // getters/setters
}
```

```java
package io.javalibs.authz.jpa;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** JPA mapping of the {@code authz_role} table with its {@code authz_role_permission} codes. */
@Entity
@Table(name = "authz_role")
public class AuthzRoleEntity {

    @Id
    private UUID id;

    @Column(name = "role_key", nullable = false, unique = true, length = 150)
    private String roleKey;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String description;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "authz_role_permission",
            joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission", nullable = false, length = 150)
    private Set<String> permissions = new LinkedHashSet<>();

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    // getters/setters
}
```

```java
package io.javalibs.authz.jpa;

import io.javalibs.authz.SubjectType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/**
 * JPA mapping of the {@code authz_role_grant} table. An empty {@code scopeType}/{@code scopeId}
 * pair means the grant is global (the columns are NOT NULL with '' as the global sentinel so
 * the unique constraint applies to global grants too).
 */
@Entity
@Table(name = "authz_role_grant",
        uniqueConstraints = @UniqueConstraint(name = "uq_authz_role_grant",
                columnNames = {"subject_type", "subject_id", "role_id", "scope_type", "scope_id"}))
public class AuthzRoleGrantEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 10)
    private SubjectType subjectType;

    @Column(name = "subject_id", nullable = false, length = 64)
    private String subjectId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private AuthzRoleEntity role;

    @Column(name = "scope_type", nullable = false, length = 100)
    private String scopeType = "";

    @Column(name = "scope_id", nullable = false, length = 100)
    private String scopeId = "";

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    // getters/setters
}
```

- [ ] **Step 3: Viết repository**

```java
package io.javalibs.authz.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link AuthzUserEntity}. */
public interface AuthzUserRepository extends JpaRepository<AuthzUserEntity, UUID> {

    Optional<AuthzUserEntity> findByUsername(String username);
}
```

```java
package io.javalibs.authz.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link AuthzGroupEntity}. */
public interface AuthzGroupRepository extends JpaRepository<AuthzGroupEntity, UUID> {

    Optional<AuthzGroupEntity> findByName(String name);
}
```

```java
package io.javalibs.authz.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** Spring Data repository for {@link AuthzGroupMemberEntity}. */
public interface AuthzGroupMemberRepository extends JpaRepository<AuthzGroupMemberEntity, UUID> {

    @Query("select m.groupId from AuthzGroupMemberEntity m where m.userId = :userId")
    List<UUID> findGroupIdsByUserId(@Param("userId") UUID userId);

    void deleteByGroupIdAndUserId(UUID groupId, UUID userId);

    boolean existsByGroupIdAndUserId(UUID groupId, UUID userId);
}
```

```java
package io.javalibs.authz.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link AuthzRoleEntity}. */
public interface AuthzRoleRepository extends JpaRepository<AuthzRoleEntity, UUID> {

    Optional<AuthzRoleEntity> findByRoleKey(String roleKey);

    @Query("select distinct r from AuthzRoleEntity r left join fetch r.permissions")
    List<AuthzRoleEntity> findAllWithPermissions();
}
```

```java
package io.javalibs.authz.jpa;

import io.javalibs.authz.SubjectType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link AuthzRoleGrantEntity}. */
public interface AuthzRoleGrantRepository extends JpaRepository<AuthzRoleGrantEntity, UUID> {

    @Query("select distinct g from AuthzRoleGrantEntity g "
            + "join fetch g.role r left join fetch r.permissions "
            + "where g.subjectType = :subjectType and g.subjectId = :subjectId")
    List<AuthzRoleGrantEntity> findWithRoleBySubject(
            @Param("subjectType") SubjectType subjectType,
            @Param("subjectId") String subjectId);

    Optional<AuthzRoleGrantEntity> findBySubjectTypeAndSubjectIdAndRoleIdAndScopeTypeAndScopeId(
            SubjectType subjectType, String subjectId, UUID roleId, String scopeType,
            String scopeId);
}
```

- [ ] **Step 4: Test app + cấu hình test**

`AuthzJpaTestApplication.java` (src/test):

```java
package io.javalibs.authz.jpa;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Minimal Boot app so the module's entities/repositories can be integration-tested. */
@SpringBootApplication
@EntityScan(basePackageClasses = AuthzUserEntity.class)
@EnableJpaRepositories(basePackageClasses = AuthzUserRepository.class)
public class AuthzJpaTestApplication {
}
```

`application-test.yml` (src/test/resources):

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    locations: classpath:db/migration/javalibs-authz
```

- [ ] **Step 5: Viết integration test fail**

```java
package io.javalibs.authz.jpa;

import io.javalibs.authz.SubjectType;
import io.javalibs.test.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AuthzJpaSchemaIT extends BaseIntegrationTest {

    @Autowired AuthzUserRepository users;
    @Autowired AuthzRoleRepository roles;
    @Autowired AuthzRoleGrantRepository grants;

    @Test
    void persistsUserRoleAndGrantRoundTrip() {
        AuthzUserEntity user = new AuthzUserEntity();
        user.setUsername("alice");
        user.setPasswordHash("{bcrypt}x");
        users.save(user);

        AuthzRoleEntity role = new AuthzRoleEntity();
        role.setRoleKey("issue-viewer");
        role.setName("Issue viewer");
        role.setPermissions(Set.of("issue.read"));
        roles.save(role);

        AuthzRoleGrantEntity grant = new AuthzRoleGrantEntity();
        grant.setSubjectType(SubjectType.USER);
        grant.setSubjectId(user.getId().toString());
        grant.setRole(role);
        grant.setScopeType("project");
        grant.setScopeId("42");
        grants.save(grant);

        var loaded = grants.findWithRoleBySubject(SubjectType.USER, user.getId().toString());
        assertThat(loaded).hasSize(1);
        assertThat(loaded.getFirst().getRole().getPermissions()).containsExactly("issue.read");
        assertThat(users.findByUsername("alice")).isPresent();
    }
}
```

- [ ] **Step 6: Chạy IT (cần Docker), xác nhận fail rồi pass**

Run: `mvn -q verify -pl javalibs-authz/javalibs-authz-jpa`
Expected lần đầu: FAIL nếu entity/migration lệch nhau (ddl-auto=validate bắt lỗi); sửa đến khi PASS. Nếu Hibernate validate báo lệch kiểu cột timestamp, chỉnh kiểu cột trong migration theo thông báo lỗi (giữ ý nghĩa UTC).

- [ ] **Step 7: Commit**

```bash
git add javalibs-authz/javalibs-authz-jpa
git commit -m "feat(authz): schema authz_* + entity + repository với Flyway migration"
```

---

### Task 9: authz-jpa — `JpaGrantResolver` + `JpaGroupMembershipResolver`

**Files:**
- Create: `javalibs-authz/javalibs-authz-jpa/src/main/java/io/javalibs/authz/jpa/JpaGrantResolver.java`
- Create: `javalibs-authz/javalibs-authz-jpa/src/main/java/io/javalibs/authz/jpa/JpaGroupMembershipResolver.java`
- Test: `javalibs-authz/javalibs-authz-jpa/src/test/java/io/javalibs/authz/jpa/JpaResolversIT.java`

**Interfaces:**
- Consumes: repository Task 8; `GrantResolver`, `GroupMembershipResolver`, `ResolvedGrant`, `Scope`, `Subject` (Task 3).
- Produces: `class JpaGrantResolver implements GrantResolver { JpaGrantResolver(AuthzRoleGrantRepository); }`; `class JpaGroupMembershipResolver implements GroupMembershipResolver { JpaGroupMembershipResolver(AuthzGroupMemberRepository); }` — `groupsOf` với userId không phải UUID trả về tập rỗng (token từ IdP ngoài có thể mang sub không phải UUID).

- [ ] **Step 1: Viết integration test fail**

```java
package io.javalibs.authz.jpa;

import io.javalibs.authz.Scope;
import io.javalibs.authz.Subject;
import io.javalibs.authz.SubjectType;
import io.javalibs.test.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JpaResolversIT extends BaseIntegrationTest {

    @Autowired AuthzUserRepository users;
    @Autowired AuthzGroupRepository groups;
    @Autowired AuthzGroupMemberRepository members;
    @Autowired AuthzRoleRepository roles;
    @Autowired AuthzRoleGrantRepository grants;

    private AuthzUserEntity newUser(String username) {
        AuthzUserEntity user = new AuthzUserEntity();
        user.setUsername(username);
        user.setPasswordHash("{bcrypt}x");
        return users.save(user);
    }

    private AuthzRoleEntity newRole(String key, Set<String> permissions) {
        AuthzRoleEntity role = new AuthzRoleEntity();
        role.setRoleKey(key);
        role.setName(key);
        role.setPermissions(permissions);
        return roles.save(role);
    }

    private void grant(SubjectType type, String subjectId, AuthzRoleEntity role,
            String scopeType, String scopeId) {
        AuthzRoleGrantEntity grant = new AuthzRoleGrantEntity();
        grant.setSubjectType(type);
        grant.setSubjectId(subjectId);
        grant.setRole(role);
        grant.setScopeType(scopeType);
        grant.setScopeId(scopeId);
        grants.save(grant);
    }

    @Test
    void resolvesScopedAndGlobalGrants() {
        AuthzUserEntity user = newUser("res-user");
        AuthzRoleEntity viewer = newRole("res-viewer", Set.of("issue.read"));
        AuthzRoleEntity admin = newRole("res-admin", Set.of("system.admin"));
        grant(SubjectType.USER, user.getId().toString(), viewer, "project", "42");
        grant(SubjectType.USER, user.getId().toString(), admin, "", "");

        var resolved = new JpaGrantResolver(grants)
                .resolveGrants(Subject.user(user.getId().toString()));

        assertThat(resolved).hasSize(2);
        assertThat(resolved).anySatisfy(g -> {
            assertThat(g.scope()).isEqualTo(Scope.of("project", "42"));
            assertThat(g.permissions()).containsExactly("issue.read");
        });
        assertThat(resolved).anySatisfy(g -> {
            assertThat(g.scope()).isEqualTo(Scope.GLOBAL);
            assertThat(g.permissions()).containsExactly("system.admin");
        });
    }

    @Test
    void resolvesGroupMemberships() {
        AuthzUserEntity user = newUser("mem-user");
        AuthzGroupEntity group = new AuthzGroupEntity();
        group.setName("mem-group");
        groups.save(group);
        AuthzGroupMemberEntity member = new AuthzGroupMemberEntity();
        member.setGroupId(group.getId());
        member.setUserId(user.getId());
        members.save(member);

        var resolver = new JpaGroupMembershipResolver(members);
        assertThat(resolver.groupsOf(user.getId().toString()))
                .containsExactly(group.getId().toString());
        assertThat(resolver.groupsOf("not-a-uuid")).isEmpty();
    }
}
```

- [ ] **Step 2: Chạy, xác nhận fail**

Run: `mvn -q verify -pl javalibs-authz/javalibs-authz-jpa`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement**

```java
package io.javalibs.authz.jpa;

import io.javalibs.authz.GrantResolver;
import io.javalibs.authz.ResolvedGrant;
import io.javalibs.authz.Scope;
import io.javalibs.authz.Subject;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Default database-backed {@link GrantResolver} reading the {@code authz_role_grant} table. */
public class JpaGrantResolver implements GrantResolver {

    private final AuthzRoleGrantRepository grantRepository;

    public JpaGrantResolver(AuthzRoleGrantRepository grantRepository) {
        this.grantRepository = Objects.requireNonNull(grantRepository);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResolvedGrant> resolveGrants(Subject subject) {
        return grantRepository.findWithRoleBySubject(subject.type(), subject.id()).stream()
                .map(grant -> new ResolvedGrant(
                        toScope(grant),
                        Set.copyOf(grant.getRole().getPermissions())))
                .toList();
    }

    private static Scope toScope(AuthzRoleGrantEntity grant) {
        return grant.getScopeType().isEmpty()
                ? Scope.GLOBAL
                : Scope.of(grant.getScopeType(), grant.getScopeId());
    }
}
```

```java
package io.javalibs.authz.jpa;

import io.javalibs.authz.GroupMembershipResolver;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Default database-backed {@link GroupMembershipResolver}. User ids that are not UUIDs
 * (e.g. subjects minted by an external IdP that have no local user row) resolve to no groups.
 */
public class JpaGroupMembershipResolver implements GroupMembershipResolver {

    private final AuthzGroupMemberRepository memberRepository;

    public JpaGroupMembershipResolver(AuthzGroupMemberRepository memberRepository) {
        this.memberRepository = Objects.requireNonNull(memberRepository);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> groupsOf(String userId) {
        UUID uuid;
        try {
            uuid = UUID.fromString(userId);
        } catch (IllegalArgumentException ex) {
            return Set.of();
        }
        Set<String> groupIds = new LinkedHashSet<>();
        for (UUID groupId : memberRepository.findGroupIdsByUserId(uuid)) {
            groupIds.add(groupId.toString());
        }
        return groupIds;
    }
}
```

- [ ] **Step 4: Chạy, xác nhận pass**

Run: `mvn -q verify -pl javalibs-authz/javalibs-authz-jpa`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add javalibs-authz/javalibs-authz-jpa
git commit -m "feat(authz): JpaGrantResolver và JpaGroupMembershipResolver mặc định"
```

---

### Task 10: authz-jpa — `AuthzManagementService`

**Files:**
- Create: `javalibs-authz/javalibs-authz-jpa/src/main/java/io/javalibs/authz/jpa/AuthzManagementService.java`
- Test: `javalibs-authz/javalibs-authz-jpa/src/test/java/io/javalibs/authz/jpa/AuthzManagementServiceIT.java`

**Interfaces:**
- Consumes: repository Task 8; `PermissionCatalog`, `UnknownPermissionException` (Task 4); `AuthzCacheInvalidator`, `Subject`, `Scope` (Task 3/7).
- Produces: `class AuthzManagementService` với constructor `(AuthzUserRepository, AuthzGroupRepository, AuthzGroupMemberRepository, AuthzRoleRepository, AuthzRoleGrantRepository, PermissionCatalog catalogOrNull, AuthzCacheInvalidator invalidatorOrNull)` và các method:
  - `UUID createUser(String username, String email, String displayName, String passwordHash, boolean enabled)`
  - `UUID createGroup(String name, String description)`
  - `void addUserToGroup(UUID userId, UUID groupId)` / `void removeUserFromGroup(UUID userId, UUID groupId)`
  - `UUID createRole(String roleKey, String name, String description, Set<String> permissions)` — validate catalog nếu có
  - `void grantRole(Subject subject, String roleKey, Scope scope)` — idempotent (đã có grant thì bỏ qua)
  - `void revokeGrant(Subject subject, String roleKey, Scope scope)`
  - `void validateRolesAgainstCatalog()` — dùng ở startup (Task 11)
  - Quy tắc: mutation grant → `invalidator.evictSubject(subject)`; mutation membership → `invalidator.evictUser(userId)`; role không tồn tại khi grant → `IllegalArgumentException`.

- [ ] **Step 1: Viết integration test fail**

```java
package io.javalibs.authz.jpa;

import io.javalibs.authz.PermissionCatalog;
import io.javalibs.authz.PermissionEvaluator;
import io.javalibs.authz.Scope;
import io.javalibs.authz.Subject;
import io.javalibs.authz.UnknownPermissionException;
import io.javalibs.test.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class AuthzManagementServiceIT extends BaseIntegrationTest {

    @Autowired AuthzUserRepository users;
    @Autowired AuthzGroupRepository groups;
    @Autowired AuthzGroupMemberRepository members;
    @Autowired AuthzRoleRepository roles;
    @Autowired AuthzRoleGrantRepository grants;

    AuthzManagementService service;
    PermissionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        service = new AuthzManagementService(users, groups, members, roles, grants,
                new PermissionCatalog(Set.of("issue.read", "issue.update")), null);
        evaluator = new PermissionEvaluator(
                new JpaGrantResolver(grants), new JpaGroupMembershipResolver(members));
    }

    @Test
    void endToEndGrantFlow() {
        UUID userId = service.createUser("mgmt-alice", "a@x.io", "Alice", "{bcrypt}x", true);
        UUID groupId = service.createGroup("mgmt-devs", "Developers");
        service.addUserToGroup(userId, groupId);
        service.createRole("mgmt-editor", "Editor", null, Set.of("issue.update"));
        service.grantRole(Subject.group(groupId.toString()), "mgmt-editor",
                Scope.of("project", "42"));

        assertThat(evaluator.hasPermission(userId.toString(), "issue.update",
                Scope.of("project", "42"))).isTrue();
        assertThat(evaluator.hasPermission(userId.toString(), "issue.update",
                Scope.of("project", "43"))).isFalse();

        service.revokeGrant(Subject.group(groupId.toString()), "mgmt-editor",
                Scope.of("project", "42"));
        assertThat(evaluator.hasPermission(userId.toString(), "issue.update",
                Scope.of("project", "42"))).isFalse();
    }

    @Test
    void grantRoleIsIdempotent() {
        UUID userId = service.createUser("mgmt-bob", null, null, "{bcrypt}x", true);
        service.createRole("mgmt-viewer", "Viewer", null, Set.of("issue.read"));
        service.grantRole(Subject.user(userId.toString()), "mgmt-viewer", Scope.GLOBAL);
        service.grantRole(Subject.user(userId.toString()), "mgmt-viewer", Scope.GLOBAL);
        assertThat(grants.findWithRoleBySubject(io.javalibs.authz.SubjectType.USER,
                userId.toString())).hasSize(1);
    }

    @Test
    void createRoleRejectsUnknownPermission() {
        assertThatExceptionOfType(UnknownPermissionException.class)
                .isThrownBy(() -> service.createRole("mgmt-bad", "Bad", null,
                        Set.of("bogus.perm")));
    }
}
```

- [ ] **Step 2: Chạy, xác nhận fail**

Run: `mvn -q verify -pl javalibs-authz/javalibs-authz-jpa`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement**

```java
package io.javalibs.authz.jpa;

import io.javalibs.authz.AuthzCacheInvalidator;
import io.javalibs.authz.PermissionCatalog;
import io.javalibs.authz.Scope;
import io.javalibs.authz.Subject;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Administrative write API over the authz_* tables: users, groups, roles and grants.
 * When a {@link PermissionCatalog} is supplied every role write is validated against it;
 * when an {@link AuthzCacheInvalidator} is supplied the relevant cache entries are evicted
 * after each mutation. Expose REST endpoints over this service in the application as needed.
 */
public class AuthzManagementService {

    private final AuthzUserRepository userRepository;
    private final AuthzGroupRepository groupRepository;
    private final AuthzGroupMemberRepository memberRepository;
    private final AuthzRoleRepository roleRepository;
    private final AuthzRoleGrantRepository grantRepository;
    private final PermissionCatalog permissionCatalog;   // nullable
    private final AuthzCacheInvalidator cacheInvalidator; // nullable

    public AuthzManagementService(AuthzUserRepository userRepository,
            AuthzGroupRepository groupRepository,
            AuthzGroupMemberRepository memberRepository,
            AuthzRoleRepository roleRepository,
            AuthzRoleGrantRepository grantRepository,
            PermissionCatalog permissionCatalog,
            AuthzCacheInvalidator cacheInvalidator) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.groupRepository = Objects.requireNonNull(groupRepository);
        this.memberRepository = Objects.requireNonNull(memberRepository);
        this.roleRepository = Objects.requireNonNull(roleRepository);
        this.grantRepository = Objects.requireNonNull(grantRepository);
        this.permissionCatalog = permissionCatalog;
        this.cacheInvalidator = cacheInvalidator;
    }

    /** Creates a user; the caller supplies the already-hashed password. */
    @Transactional
    public UUID createUser(String username, String email, String displayName,
            String passwordHash, boolean enabled) {
        AuthzUserEntity user = new AuthzUserEntity();
        user.setUsername(username);
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordHash);
        user.setEnabled(enabled);
        return userRepository.save(user).getId();
    }

    /** Creates a group. */
    @Transactional
    public UUID createGroup(String name, String description) {
        AuthzGroupEntity group = new AuthzGroupEntity();
        group.setName(name);
        group.setDescription(description);
        return groupRepository.save(group).getId();
    }

    /** Adds a user to a group (no-op when already a member). */
    @Transactional
    public void addUserToGroup(UUID userId, UUID groupId) {
        if (!memberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            AuthzGroupMemberEntity member = new AuthzGroupMemberEntity();
            member.setGroupId(groupId);
            member.setUserId(userId);
            memberRepository.save(member);
        }
        evictUser(userId);
    }

    /** Removes a user from a group. */
    @Transactional
    public void removeUserFromGroup(UUID userId, UUID groupId) {
        memberRepository.deleteByGroupIdAndUserId(groupId, userId);
        evictUser(userId);
    }

    /** Creates a role bundling the given permission codes. */
    @Transactional
    public UUID createRole(String roleKey, String name, String description,
            Set<String> permissions) {
        if (permissionCatalog != null) {
            permissionCatalog.requireKnown(permissions);
        }
        AuthzRoleEntity role = new AuthzRoleEntity();
        role.setRoleKey(roleKey);
        role.setName(name);
        role.setDescription(description);
        role.setPermissions(permissions);
        return roleRepository.save(role).getId();
    }

    /** Grants the role to the subject in the scope; idempotent. */
    @Transactional
    public void grantRole(Subject subject, String roleKey, Scope scope) {
        AuthzRoleEntity role = requireRole(roleKey);
        String scopeType = scope.isGlobal() ? "" : scope.type();
        String scopeId = scope.isGlobal() ? "" : scope.id();
        boolean exists = grantRepository
                .findBySubjectTypeAndSubjectIdAndRoleIdAndScopeTypeAndScopeId(
                        subject.type(), subject.id(), role.getId(), scopeType, scopeId)
                .isPresent();
        if (!exists) {
            AuthzRoleGrantEntity grant = new AuthzRoleGrantEntity();
            grant.setSubjectType(subject.type());
            grant.setSubjectId(subject.id());
            grant.setRole(role);
            grant.setScopeType(scopeType);
            grant.setScopeId(scopeId);
            grantRepository.save(grant);
        }
        evictSubject(subject);
    }

    /** Revokes the role grant of the subject in the scope (no-op when absent). */
    @Transactional
    public void revokeGrant(Subject subject, String roleKey, Scope scope) {
        AuthzRoleEntity role = requireRole(roleKey);
        String scopeType = scope.isGlobal() ? "" : scope.type();
        String scopeId = scope.isGlobal() ? "" : scope.id();
        grantRepository.findBySubjectTypeAndSubjectIdAndRoleIdAndScopeTypeAndScopeId(
                        subject.type(), subject.id(), role.getId(), scopeType, scopeId)
                .ifPresent(grantRepository::delete);
        evictSubject(subject);
    }

    /** Validates every stored role against the catalog; call at startup. */
    @Transactional(readOnly = true)
    public void validateRolesAgainstCatalog() {
        if (permissionCatalog == null) {
            return;
        }
        for (AuthzRoleEntity role : roleRepository.findAllWithPermissions()) {
            permissionCatalog.requireKnown(role.getPermissions());
        }
    }

    private AuthzRoleEntity requireRole(String roleKey) {
        return roleRepository.findByRoleKey(roleKey).orElseThrow(
                () -> new IllegalArgumentException("Unknown role key '" + roleKey
                        + "'. Create the role before granting it."));
    }

    private void evictSubject(Subject subject) {
        if (cacheInvalidator != null) {
            cacheInvalidator.evictSubject(subject);
        }
    }

    private void evictUser(UUID userId) {
        if (cacheInvalidator != null) {
            cacheInvalidator.evictUser(userId.toString());
        }
    }
}
```

- [ ] **Step 4: Chạy, xác nhận pass**

Run: `mvn -q verify -pl javalibs-authz/javalibs-authz-jpa`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add javalibs-authz/javalibs-authz-jpa
git commit -m "feat(authz): AuthzManagementService quản trị role/grant/group"
```

---

### Task 11: authz autoconfigure + starter

**Files:**
- Create: `javalibs-authz/javalibs-authz-spring-boot-autoconfigure/src/main/java/io/javalibs/authz/autoconfigure/AuthzProperties.java`
- Create: `javalibs-authz/javalibs-authz-spring-boot-autoconfigure/src/main/java/io/javalibs/authz/autoconfigure/AuthzJpaAutoConfiguration.java`
- Create: `javalibs-authz/javalibs-authz-spring-boot-autoconfigure/src/main/java/io/javalibs/authz/autoconfigure/AuthzAutoConfiguration.java`
- Create: `javalibs-authz/javalibs-authz-spring-boot-autoconfigure/src/main/java/io/javalibs/authz/autoconfigure/AuthzWebMvcAutoConfiguration.java`
- Create: `javalibs-authz/javalibs-authz-spring-boot-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Test: `javalibs-authz/javalibs-authz-spring-boot-autoconfigure/src/test/java/io/javalibs/authz/autoconfigure/AuthzAutoConfigurationTest.java`

**Interfaces:**
- Consumes: mọi type từ Task 3–10.
- Produces: properties `javalibs.authz.enabled` (true), `javalibs.authz.cache.enabled` (true), `javalibs.authz.cache.ttl` (60s), `javalibs.authz.cache.max-size` (10000), `javalibs.authz.jpa.enabled` (true), `javalibs.authz.jpa.apply-migrations` (true). Bean: caching decorators (`@Primary`), `PermissionEvaluator`, `PermissionChecker`, `WebMvcConfigurer` đăng ký `RequirePermissionInterceptor`, JPA wiring (`JpaGrantResolver`, `JpaGroupMembershipResolver`, `AuthzManagementService`, `FlywayConfigurationCustomizer` thêm location `classpath:db/migration/javalibs-authz`) và `InitializingBean` gọi `validateRolesAgainstCatalog()` khi có `PermissionCatalog`.

- [ ] **Step 1: Viết test fail** (`ApplicationContextRunner`):

```java
package io.javalibs.authz.autoconfigure;

import io.javalibs.authz.GrantResolver;
import io.javalibs.authz.GroupMembershipResolver;
import io.javalibs.authz.PermissionEvaluator;
import io.javalibs.authz.spring.CachingGrantResolver;
import io.javalibs.authz.spring.PermissionChecker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AuthzAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AuthzAutoConfiguration.class));

    @Configuration(proxyBeanMethods = false)
    static class ResolverConfiguration {
        @Bean
        GrantResolver grantResolver() {
            return subject -> List.of();
        }
        @Bean
        GroupMembershipResolver groupMembershipResolver() {
            return userId -> Set.of();
        }
    }

    @Test
    void createsEvaluatorCheckerAndCachingDecoratorsWhenResolversPresent() {
        runner.withUserConfiguration(ResolverConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(PermissionEvaluator.class);
            assertThat(context).hasSingleBean(PermissionChecker.class);
            assertThat(context).hasSingleBean(CachingGrantResolver.class);
        });
    }

    @Test
    void skipsCachingWhenDisabled() {
        runner.withUserConfiguration(ResolverConfiguration.class)
                .withPropertyValues("javalibs.authz.cache.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(PermissionEvaluator.class);
                    assertThat(context).doesNotHaveBean(CachingGrantResolver.class);
                });
    }

    @Test
    void backsOffEntirelyWhenDisabled() {
        runner.withUserConfiguration(ResolverConfiguration.class)
                .withPropertyValues("javalibs.authz.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(PermissionEvaluator.class));
    }

    @Test
    void backsOffWhenNoResolversDefined() {
        runner.run(context -> assertThat(context).doesNotHaveBean(PermissionEvaluator.class));
    }
}
```

- [ ] **Step 2: Chạy, xác nhận fail**

Run: `mvn -q test -pl javalibs-authz/javalibs-authz-spring-boot-autoconfigure`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement**

`AuthzProperties.java` — JavaBean `@ConfigurationProperties("javalibs.authz")` theo phong cách `SecurityProperties` (field + getter/setter + Javadoc từng property):

```java
package io.javalibs.authz.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Configuration properties for javalibs authz ({@code javalibs.authz.*}). */
@ConfigurationProperties("javalibs.authz")
public class AuthzProperties {

    /** Whether the authz auto-configuration is active. */
    private boolean enabled = true;

    private final Cache cache = new Cache();

    private final Jpa jpa = new Jpa();

    // getters/setters/getCache()/getJpa()

    /** Resolver caching settings ({@code javalibs.authz.cache.*}). */
    public static class Cache {
        /** Whether resolver results are cached (requires Caffeine on the classpath). */
        private boolean enabled = true;
        /** Time-to-live of cached grants/memberships; the upper bound for stale permissions across instances. */
        private Duration ttl = Duration.ofSeconds(60);
        /** Maximum number of subjects kept per cache. */
        private long maxSize = 10_000;
        // getters/setters
    }

    /** Default JPA persistence settings ({@code javalibs.authz.jpa.*}). */
    public static class Jpa {
        /** Whether the javalibs-authz-jpa beans are auto-configured when the module is present. */
        private boolean enabled = true;
        /** Whether the bundled Flyway migration location is appended automatically. */
        private boolean applyMigrations = true;
        // getters/setters
    }
}
```

`AuthzAutoConfiguration.java`:

```java
package io.javalibs.authz.autoconfigure;

import io.javalibs.authz.AuthzCacheInvalidator;
import io.javalibs.authz.GrantResolver;
import io.javalibs.authz.GroupMembershipResolver;
import io.javalibs.authz.PermissionEvaluator;
import io.javalibs.authz.spring.CachingAuthzInvalidator;
import io.javalibs.authz.spring.CachingGrantResolver;
import io.javalibs.authz.spring.CachingGroupMembershipResolver;
import io.javalibs.authz.spring.PermissionChecker;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Auto-configuration for the javalibs authz engine. Backs off when no
 * {@link GrantResolver}/{@link GroupMembershipResolver} beans exist (typically provided by
 * {@link AuthzJpaAutoConfiguration} or the application itself).
 */
@AutoConfiguration(after = AuthzJpaAutoConfiguration.class)
@ConditionalOnProperty(prefix = "javalibs.authz", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(AuthzProperties.class)
public class AuthzAutoConfiguration {

    /** Caffeine-backed caching decorators, marked primary so the evaluator uses them. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Caffeine.class)
    @ConditionalOnProperty(prefix = "javalibs.authz.cache", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    static class CachingConfiguration {

        @Bean
        @Primary
        @ConditionalOnBean(GrantResolver.class)
        @ConditionalOnMissingBean(CachingGrantResolver.class)
        CachingGrantResolver javalibsCachingGrantResolver(GrantResolver delegate,
                AuthzProperties properties) {
            return new CachingGrantResolver(delegate,
                    properties.getCache().getTtl(), properties.getCache().getMaxSize());
        }

        @Bean
        @Primary
        @ConditionalOnBean(GroupMembershipResolver.class)
        @ConditionalOnMissingBean(CachingGroupMembershipResolver.class)
        CachingGroupMembershipResolver javalibsCachingGroupMembershipResolver(
                GroupMembershipResolver delegate, AuthzProperties properties) {
            return new CachingGroupMembershipResolver(delegate,
                    properties.getCache().getTtl(), properties.getCache().getMaxSize());
        }

        @Bean
        @ConditionalOnBean({CachingGrantResolver.class, CachingGroupMembershipResolver.class})
        @ConditionalOnMissingBean(AuthzCacheInvalidator.class)
        CachingAuthzInvalidator javalibsAuthzCacheInvalidator(CachingGrantResolver grants,
                CachingGroupMembershipResolver groups) {
            return new CachingAuthzInvalidator(grants, groups);
        }
    }

    @Bean
    @ConditionalOnBean({GrantResolver.class, GroupMembershipResolver.class})
    @ConditionalOnMissingBean
    public PermissionEvaluator javalibsPermissionEvaluator(GrantResolver grantResolver,
            GroupMembershipResolver groupMembershipResolver) {
        return new PermissionEvaluator(grantResolver, groupMembershipResolver);
    }

    @Bean
    @ConditionalOnBean(PermissionEvaluator.class)
    @ConditionalOnMissingBean
    public PermissionChecker javalibsPermissionChecker(PermissionEvaluator evaluator) {
        return new PermissionChecker(evaluator);
    }
}
```

`AuthzJpaAutoConfiguration.java`:

```java
package io.javalibs.authz.autoconfigure;

import io.javalibs.authz.AuthzCacheInvalidator;
import io.javalibs.authz.PermissionCatalog;
import io.javalibs.authz.jpa.AuthzGroupMemberRepository;
import io.javalibs.authz.jpa.AuthzGroupRepository;
import io.javalibs.authz.jpa.AuthzManagementService;
import io.javalibs.authz.jpa.AuthzRoleGrantRepository;
import io.javalibs.authz.jpa.AuthzRoleRepository;
import io.javalibs.authz.jpa.AuthzUserEntity;
import io.javalibs.authz.jpa.AuthzUserRepository;
import io.javalibs.authz.jpa.JpaGrantResolver;
import io.javalibs.authz.jpa.JpaGroupMembershipResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Auto-configuration wiring the default JPA persistence of javalibs-authz: entity scanning,
 * repositories, the JPA resolvers, the management service and the bundled Flyway migrations.
 * Backs off when javalibs-authz-jpa or Spring Data JPA is not on the classpath.
 */
@AutoConfiguration(before = org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration.class,
        after = org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class)
@ConditionalOnClass({JpaGrantResolver.class,
        org.springframework.data.jpa.repository.JpaRepository.class})
@ConditionalOnProperty(prefix = "javalibs.authz", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@ConditionalOnProperty(prefix = "javalibs.authz.jpa", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(AuthzProperties.class)
@EntityScan(basePackageClasses = AuthzUserEntity.class)
@EnableJpaRepositories(basePackageClasses = AuthzUserRepository.class)
public class AuthzJpaAutoConfiguration {

    /** Appends the bundled migration location so Flyway creates the authz_* tables. */
    @Bean
    @ConditionalOnClass(org.flywaydb.core.Flyway.class)
    @ConditionalOnProperty(prefix = "javalibs.authz.jpa", name = "apply-migrations",
            havingValue = "true", matchIfMissing = true)
    public FlywayConfigurationCustomizer javalibsAuthzFlywayCustomizer() {
        return configuration -> {
            List<String> locations = new ArrayList<>(Arrays.stream(configuration.getLocations())
                    .map(Object::toString).toList());
            String authzLocation = "classpath:db/migration/javalibs-authz";
            if (!locations.contains(authzLocation)) {
                locations.add(authzLocation);
                configuration.locations(locations.toArray(String[]::new));
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(io.javalibs.authz.GrantResolver.class)
    public JpaGrantResolver javalibsJpaGrantResolver(AuthzRoleGrantRepository grantRepository) {
        return new JpaGrantResolver(grantRepository);
    }

    @Bean
    @ConditionalOnMissingBean(io.javalibs.authz.GroupMembershipResolver.class)
    public JpaGroupMembershipResolver javalibsJpaGroupMembershipResolver(
            AuthzGroupMemberRepository memberRepository) {
        return new JpaGroupMembershipResolver(memberRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthzManagementService javalibsAuthzManagementService(
            AuthzUserRepository users, AuthzGroupRepository groups,
            AuthzGroupMemberRepository members, AuthzRoleRepository roles,
            AuthzRoleGrantRepository grants,
            ObjectProvider<PermissionCatalog> catalog,
            ObjectProvider<AuthzCacheInvalidator> invalidator) {
        AuthzManagementService service = new AuthzManagementService(users, groups, members,
                roles, grants, catalog.getIfAvailable(), invalidator.getIfAvailable());
        // Fail fast at startup when stored roles reference unknown permission codes.
        if (catalog.getIfAvailable() != null) {
            service.validateRolesAgainstCatalog();
        }
        return service;
    }
}
```

Lưu ý cho executor: `AuthzCacheInvalidator` được tạo **sau** `AuthzManagementService` trong đồ thị bean (invalidator phụ thuộc caching decorator → delegate JPA). Dùng `ObjectProvider` như trên là đúng — nhưng vì `ObjectProvider.getIfAvailable()` gọi tại thời điểm khởi tạo service sẽ trả null nếu invalidator chưa init, hãy đổi sang giữ `ObjectProvider` trong field của `AuthzManagementService` **hoặc** đơn giản hơn: truyền `invalidator.getIfAvailable()` là chấp nhận được vì `@AutoConfiguration(after=...)` bảo đảm CachingConfiguration đã đăng ký; nếu test Task 18 phát hiện evict không chạy, chuyển constructor của `AuthzManagementService` sang nhận `Supplier<AuthzCacheInvalidator>` và truyền `invalidator::getIfAvailable`.

`AuthzWebMvcAutoConfiguration.java`:

```java
package io.javalibs.authz.autoconfigure;

import io.javalibs.authz.spring.PermissionChecker;
import io.javalibs.authz.spring.RequirePermissionInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers the @RequirePermission interceptor with Spring MVC. */
@AutoConfiguration(after = AuthzAutoConfiguration.class)
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "javalibs.authz", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@ConditionalOnBean(PermissionChecker.class)
public class AuthzWebMvcAutoConfiguration {

    @Bean
    public WebMvcConfigurer javalibsAuthzWebMvcConfigurer(PermissionChecker permissionChecker) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new RequirePermissionInterceptor(permissionChecker));
            }
        };
    }
}
```

File imports `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
io.javalibs.authz.autoconfigure.AuthzJpaAutoConfiguration
io.javalibs.authz.autoconfigure.AuthzAutoConfiguration
io.javalibs.authz.autoconfigure.AuthzWebMvcAutoConfiguration
```

- [ ] **Step 4: Chạy, xác nhận pass**

Run: `mvn -q test -pl javalibs-authz/javalibs-authz-spring-boot-autoconfigure`
Expected: PASS. (Hai `@ConditionalOnProperty` stack trên một class là hợp lệ — `JavalibsSecurityAutoConfiguration` hiện có đã dùng đúng pattern này.)

- [ ] **Step 5: Build starter + install toàn family**

Run: `mvn -q install -pl javalibs-authz -amd -DskipTests`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add javalibs-authz
git commit -m "feat(authz): autoconfigure + starter cho javalibs-authz"
```

---

### Task 12: security-issuer — scaffold module + `PasswordHasher`

**Files:**
- Create: `javalibs-security/javalibs-security-issuer/pom.xml`
- Create: `javalibs-security/javalibs-security-issuer/src/main/java/io/javalibs/security/issuer/PasswordHasher.java`
- Modify: `javalibs-security/pom.xml` (thêm `<module>javalibs-security-issuer</module>`)
- Modify: `javalibs-dependencies/pom.xml` (thêm entry `javalibs-security-issuer`)
- Test: `javalibs-security/javalibs-security-issuer/src/test/java/io/javalibs/security/issuer/PasswordHasherTest.java`

**Interfaces:**
- Produces: artifact `javalibs-security-issuer`; `class PasswordHasher { PasswordHasher(); PasswordHasher(PasswordEncoder); String hash(String raw); boolean matches(String raw, String hash); }` — mặc định `PasswordEncoderFactories.createDelegatingPasswordEncoder()` (hash dạng `{bcrypt}...`).

- [ ] **Step 1: POM module**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.javalibs</groupId>
    <artifactId>javalibs-security</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>..</relativePath>
  </parent>
  <artifactId>javalibs-security-issuer</artifactId>
  <name>javalibs :: security :: issuer</name>
  <description>
    Token issuing counterpart to the javalibs-security validation core: username/password
    login, JWT access tokens, opaque rotating refresh tokens with reuse detection, logout
    via the shared TokenBlacklist, and ready-to-use /auth REST endpoints.
  </description>
  <dependencies>
    <dependency>
      <groupId>io.javalibs</groupId>
      <artifactId>javalibs-security-core</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>org.springframework.security</groupId>
      <artifactId>spring-security-crypto</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-web</artifactId>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-api</artifactId>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-impl</artifactId>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-jackson</artifactId>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-api</artifactId>
    </dependency>
    <!-- Test -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 2: Đăng ký module + BOM.** Thêm `<module>javalibs-security-issuer</module>` vào `javalibs-security/pom.xml` (sau `javalibs-security-spring-boot-starter`). Thêm entry BOM `javalibs-security-issuer` vào `javalibs-dependencies/pom.xml` cạnh các entry security.

- [ ] **Step 3: Viết test fail**

```java
package io.javalibs.security.issuer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashesAndVerifies() {
        String hash = hasher.hash("s3cret");
        assertThat(hash).startsWith("{bcrypt}");
        assertThat(hasher.matches("s3cret", hash)).isTrue();
        assertThat(hasher.matches("wrong", hash)).isFalse();
    }

    @Test
    void producesDifferentHashesForSamePassword() {
        assertThat(hasher.hash("s3cret")).isNotEqualTo(hasher.hash("s3cret"));
    }
}
```

- [ ] **Step 4: Chạy, xác nhận fail**

Run: `mvn -q test -pl javalibs-security/javalibs-security-issuer`
Expected: COMPILATION ERROR.

- [ ] **Step 5: Implement**

```java
package io.javalibs.security.issuer;

import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Objects;

/**
 * Hashes and verifies user passwords. Defaults to Spring Security's delegating encoder
 * (BCrypt, {@code {bcrypt}...} prefixed hashes) so the algorithm can be upgraded later
 * without invalidating stored hashes.
 */
public final class PasswordHasher {

    private final PasswordEncoder encoder;

    public PasswordHasher() {
        this(PasswordEncoderFactories.createDelegatingPasswordEncoder());
    }

    public PasswordHasher(PasswordEncoder encoder) {
        this.encoder = Objects.requireNonNull(encoder, "encoder must not be null");
    }

    /** Hashes a raw password. */
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    /** Verifies a raw password against a stored hash. */
    public boolean matches(String rawPassword, String storedHash) {
        return encoder.matches(rawPassword, storedHash);
    }
}
```

- [ ] **Step 6: Chạy pass rồi commit**

Run: `mvn -q test -pl javalibs-security/javalibs-security-issuer`
Expected: PASS.

```bash
git add javalibs-security javalibs-dependencies/pom.xml
git commit -m "feat(security): scaffold javalibs-security-issuer + PasswordHasher"
```

---

### Task 13: security-issuer — `JwtIssuerConfig`, `TokenIssuer`, `RefreshTokens`

**Files:**
- Create: `javalibs-security/javalibs-security-issuer/src/main/java/io/javalibs/security/issuer/JwtIssuerConfig.java`
- Create: `javalibs-security/javalibs-security-issuer/src/main/java/io/javalibs/security/issuer/TokenIssuer.java`
- Create: `javalibs-security/javalibs-security-issuer/src/main/java/io/javalibs/security/issuer/RefreshTokens.java`
- Test: `javalibs-security/javalibs-security-issuer/src/test/java/io/javalibs/security/issuer/TokenIssuerTest.java`
- Test: `javalibs-security/javalibs-security-issuer/src/test/java/io/javalibs/security/issuer/RefreshTokensTest.java`

**Interfaces:**
- Consumes: `JwtTokenValidator`, `JwtValidationConfig`, `UserContext`, `TokenBlacklist.TOKEN_ID_ATTRIBUTE` từ `javalibs-security-core` (để test round-trip).
- Produces:
  - `record JwtIssuerConfig(String hmacSecret, String rsaPrivateKeyPem, String issuer, String audience, Duration accessTokenTtl, Duration refreshTokenTtl, String usernameClaim, String emailClaim)` + builder; default: accessTokenTtl 15m, refreshTokenTtl 30d, usernameClaim `preferred_username`, emailClaim `email`; bắt buộc một trong hai loại khóa; HMAC secret ≥ 32 byte.
  - `class TokenIssuer { TokenIssuer(JwtIssuerConfig, Clock); IssuedToken issue(String userId, String username, String email); record IssuedToken(String token, String tokenId, Instant expiresAt) }` — claims: `sub`, `jti` (UUID), `iat`, `exp`, `iss`/`aud` khi cấu hình, username/email theo claim name cấu hình.
  - `final class RefreshTokens { static String generate(); static String hash(String token); }` — token 256-bit base64url không padding; hash SHA-256 hex (64 ký tự).

- [ ] **Step 1: Viết test fail**

```java
package io.javalibs.security.issuer;

import io.javalibs.security.JwtTokenValidator;
import io.javalibs.security.JwtValidationConfig;
import io.javalibs.security.TokenBlacklist;
import io.javalibs.security.UserContext;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TokenIssuerTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void issuedTokenValidatesWithSecurityCoreValidator() {
        JwtIssuerConfig config = JwtIssuerConfig.builder()
                .hmacSecret(SECRET)
                .issuer("https://auth.example.com")
                .build();
        TokenIssuer issuer = new TokenIssuer(config, Clock.systemUTC());

        TokenIssuer.IssuedToken issued = issuer.issue("u1", "alice", "alice@example.com");

        JwtTokenValidator validator = new JwtTokenValidator(JwtValidationConfig.builder()
                .hmacSecret(SECRET)
                .issuer("https://auth.example.com")
                .build());
        UserContext user = validator.validate(issued.token());
        assertThat(user.userId()).isEqualTo("u1");
        assertThat(user.username()).isEqualTo("alice");
        assertThat(user.email()).isEqualTo("alice@example.com");
        assertThat(user.attributes()).containsEntry(
                TokenBlacklist.TOKEN_ID_ATTRIBUTE, issued.tokenId());
    }

    @Test
    void appliesConfiguredTtl() {
        Instant now = Instant.parse("2026-07-17T00:00:00Z");
        JwtIssuerConfig config = JwtIssuerConfig.builder()
                .hmacSecret(SECRET)
                .accessTokenTtl(Duration.ofMinutes(5))
                .build();
        TokenIssuer issuer = new TokenIssuer(config, Clock.fixed(now, ZoneOffset.UTC));
        assertThat(issuer.issue("u1", null, null).expiresAt())
                .isEqualTo(now.plus(Duration.ofMinutes(5)));
    }

    @Test
    void configRejectsMissingKeysAndShortSecret() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> JwtIssuerConfig.builder().build());
        assertThatIllegalArgumentException()
                .isThrownBy(() -> JwtIssuerConfig.builder().hmacSecret("short").build());
    }
}
```

```java
package io.javalibs.security.issuer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokensTest {

    @Test
    void generatesUniqueUrlSafeTokens() {
        String token = RefreshTokens.generate();
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(RefreshTokens.generate()).isNotEqualTo(token);
    }

    @Test
    void hashIsDeterministicSha256Hex() {
        String token = RefreshTokens.generate();
        assertThat(RefreshTokens.hash(token))
                .hasSize(64)
                .isEqualTo(RefreshTokens.hash(token))
                .isNotEqualTo(RefreshTokens.hash(RefreshTokens.generate()));
    }
}
```

- [ ] **Step 2: Chạy, xác nhận fail**

Run: `mvn -q test -pl javalibs-security/javalibs-security-issuer`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement**

`JwtIssuerConfig.java` — record + builder theo phong cách `JwtValidationConfig` (canonical constructor validate + defaults):

```java
package io.javalibs.security.issuer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Immutable configuration for {@link TokenIssuer}. Mirror of JwtValidationConfig on the
 * issuing side: configure the same secret (HS256) or the private key matching the
 * validator's public key (RS256) so every javalibs service can validate issued tokens.
 */
public record JwtIssuerConfig(
        String hmacSecret,
        String rsaPrivateKeyPem,
        String issuer,
        String audience,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        String usernameClaim,
        String emailClaim) {

    public static final Duration DEFAULT_ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    public static final Duration DEFAULT_REFRESH_TOKEN_TTL = Duration.ofDays(30);
    public static final String DEFAULT_USERNAME_CLAIM = "preferred_username";
    public static final String DEFAULT_EMAIL_CLAIM = "email";
    private static final int MIN_HMAC_SECRET_BYTES = 32;

    public JwtIssuerConfig {
        if (isBlank(hmacSecret) && isBlank(rsaPrivateKeyPem)) {
            throw new IllegalArgumentException(
                    "JwtIssuerConfig requires at least one of hmacSecret or rsaPrivateKeyPem");
        }
        if (!isBlank(hmacSecret)
                && hmacSecret.getBytes(StandardCharsets.UTF_8).length < MIN_HMAC_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "The configured HMAC secret is shorter than 32 bytes (256 bits)");
        }
        accessTokenTtl = (accessTokenTtl == null) ? DEFAULT_ACCESS_TOKEN_TTL : accessTokenTtl;
        refreshTokenTtl = (refreshTokenTtl == null) ? DEFAULT_REFRESH_TOKEN_TTL : refreshTokenTtl;
        usernameClaim = isBlank(usernameClaim) ? DEFAULT_USERNAME_CLAIM : usernameClaim;
        emailClaim = isBlank(emailClaim) ? DEFAULT_EMAIL_CLAIM : emailClaim;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Mutable builder, mirroring JwtValidationConfig.Builder. */
    public static final class Builder {
        private String hmacSecret;
        private String rsaPrivateKeyPem;
        private String issuer;
        private String audience;
        private Duration accessTokenTtl = DEFAULT_ACCESS_TOKEN_TTL;
        private Duration refreshTokenTtl = DEFAULT_REFRESH_TOKEN_TTL;
        private String usernameClaim = DEFAULT_USERNAME_CLAIM;
        private String emailClaim = DEFAULT_EMAIL_CLAIM;

        // setter-method cho từng field (hmacSecret(...), rsaPrivateKeyPem(...),
        // issuer(...), audience(...), accessTokenTtl(...), refreshTokenTtl(...),
        // usernameClaim(...), emailClaim(...)) — mỗi method gán field rồi return this

        public JwtIssuerConfig build() {
            return new JwtIssuerConfig(hmacSecret, rsaPrivateKeyPem, issuer, audience,
                    accessTokenTtl, refreshTokenTtl, usernameClaim, emailClaim);
        }
    }
}
```

`TokenIssuer.java`:

```java
package io.javalibs.security.issuer;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

/**
 * Signs JWT access tokens (HS256 via the shared secret, or RS256 via a PKCS#8 private key)
 * carrying only identity claims: sub, jti, iat, exp, optional iss/aud, username and email.
 * Permissions are deliberately NOT embedded — they are evaluated server-side by javalibs-authz.
 */
public final class TokenIssuer {

    private static final String PEM_PRIVATE_KEY_BEGIN = "-----BEGIN PRIVATE KEY-----";
    private static final String PEM_PRIVATE_KEY_END = "-----END PRIVATE KEY-----";

    private final JwtIssuerConfig config;
    private final Clock clock;
    private final Key signingKey;

    public TokenIssuer(JwtIssuerConfig config, Clock clock) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.signingKey = buildSigningKey(config);
    }

    /** Issues a signed access token for the given identity. */
    public IssuedToken issue(String userId, String username, String email) {
        Objects.requireNonNull(userId, "userId must not be null");
        String tokenId = UUID.randomUUID().toString();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(config.accessTokenTtl());

        JwtBuilder builder = Jwts.builder()
                .subject(userId)
                .id(tokenId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt));
        if (config.issuer() != null && !config.issuer().isBlank()) {
            builder.issuer(config.issuer());
        }
        if (config.audience() != null && !config.audience().isBlank()) {
            builder.audience().add(config.audience()).and();
        }
        if (username != null) {
            builder.claim(config.usernameClaim(), username);
        }
        if (email != null) {
            builder.claim(config.emailClaim(), email);
        }
        if (signingKey instanceof SecretKey secretKey) {
            builder.signWith(secretKey, Jwts.SIG.HS256);
        } else {
            builder.signWith((java.security.PrivateKey) signingKey, Jwts.SIG.RS256);
        }
        return new IssuedToken(builder.compact(), tokenId, expiresAt);
    }

    private static Key buildSigningKey(JwtIssuerConfig config) {
        if (config.hmacSecret() != null && !config.hmacSecret().isBlank()) {
            return Keys.hmacShaKeyFor(config.hmacSecret().getBytes(StandardCharsets.UTF_8));
        }
        String base64 = config.rsaPrivateKeyPem()
                .replace(PEM_PRIVATE_KEY_BEGIN, "")
                .replace(PEM_PRIVATE_KEY_END, "")
                .replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Unable to parse the configured RSA private key (PKCS#8 PEM expected): "
                            + ex.getMessage(), ex);
        }
    }

    /** A freshly signed access token with its id and expiry. */
    public record IssuedToken(String token, String tokenId, Instant expiresAt) {
    }
}
```

`RefreshTokens.java`:

```java
package io.javalibs.security.issuer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Opaque refresh token utilities: 256-bit random url-safe tokens, stored only as their
 * SHA-256 hex hash so a database leak does not leak usable tokens.
 */
public final class RefreshTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private RefreshTokens() {
    }

    /** Generates a 256-bit random token, base64url without padding (43 chars). */
    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Returns the lowercase SHA-256 hex digest of the token (64 chars). */
    public static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the JCA spec", ex);
        }
    }
}
```

- [ ] **Step 4: Chạy pass rồi commit**

Run: `mvn -q test -pl javalibs-security/javalibs-security-issuer`
Expected: PASS.

```bash
git add javalibs-security/javalibs-security-issuer
git commit -m "feat(security): TokenIssuer ký access token + RefreshTokens opaque"
```

---

### Task 14: security-issuer — SPI store + `AuthenticationService`

**Files:**
- Create: `javalibs-security/javalibs-security-issuer/src/main/java/io/javalibs/security/issuer/CredentialsStore.java`
- Create: `javalibs-security/javalibs-security-issuer/src/main/java/io/javalibs/security/issuer/StoredCredentials.java`
- Create: `javalibs-security/javalibs-security-issuer/src/main/java/io/javalibs/security/issuer/RefreshTokenStore.java`
- Create: `javalibs-security/javalibs-security-issuer/src/main/java/io/javalibs/security/issuer/RefreshTokenRecord.java`
- Create: `javalibs-security/javalibs-security-issuer/src/main/java/io/javalibs/security/issuer/InvalidCredentialsException.java`
- Create: `javalibs-security/javalibs-security-issuer/src/main/java/io/javalibs/security/issuer/InvalidRefreshTokenException.java`
- Create: `javalibs-security/javalibs-security-issuer/src/main/java/io/javalibs/security/issuer/TokenPair.java`
- Create: `javalibs-security/javalibs-security-issuer/src/main/java/io/javalibs/security/issuer/AuthenticationService.java`
- Test: `javalibs-security/javalibs-security-issuer/src/test/java/io/javalibs/security/issuer/AuthenticationServiceTest.java`

**Interfaces:**
- Consumes: `PasswordHasher`, `TokenIssuer`, `RefreshTokens`, `JwtIssuerConfig` (Task 12–13); `TokenBlacklist` (security-core).
- Produces:
  - `record StoredCredentials(String userId, String username, String email, String passwordHash, boolean enabled)`
  - `interface CredentialsStore { Optional<StoredCredentials> findByUsername(String username); }`
  - `record RefreshTokenRecord(String tokenHash, String userId, String familyId, Instant expiresAt, Instant revokedAt, String rotatedToHash)` + helper `boolean isRevoked()`, `boolean isExpired(Instant now)`, `boolean wasRotated()`
  - `interface RefreshTokenStore { void save(RefreshTokenRecord record); Optional<RefreshTokenRecord> findByTokenHash(String tokenHash); void markRotated(String tokenHash, String rotatedToHash, Instant revokedAt); void revoke(String tokenHash, Instant revokedAt); void revokeFamily(String familyId, Instant revokedAt); }`
  - `record TokenPair(String accessToken, Instant accessTokenExpiresAt, String refreshToken, Instant refreshTokenExpiresAt)`
  - `class AuthenticationService { AuthenticationService(CredentialsStore, RefreshTokenStore, PasswordHasher, TokenIssuer, JwtIssuerConfig, Clock, TokenBlacklist blacklistOrNull); TokenPair login(String username, String rawPassword); TokenPair refresh(String refreshToken); void logout(String refreshToken, String accessTokenId); }`
  - `class InvalidCredentialsException extends RuntimeException` / `class InvalidRefreshTokenException extends RuntimeException`

**Ngữ nghĩa bắt buộc:**
- `login`: user không tồn tại, sai password, hoặc `enabled=false` → `InvalidCredentialsException` với message chung `"Invalid credentials"`; user không tồn tại vẫn chạy một lần `matches` với hash giả để giảm chênh lệch thời gian phản hồi.
- `refresh`: token không tồn tại / hết hạn → `InvalidRefreshTokenException`; token đã revoke **và** `wasRotated()` → **reuse detection**: `revokeFamily(familyId, now)` rồi throw; rotation: token cũ `markRotated(oldHash, newHash, now)`, token mới cùng `familyId`, hết hạn theo `refreshTokenTtl` tính từ now.
- `logout`: `revoke(refreshHash, now)` (không lỗi khi token không tồn tại); nếu có blacklist và `accessTokenId != null` → `blacklist.revoke(accessTokenId, now + accessTokenTtl)`.

- [ ] **Step 1: Viết test fail** — dùng fake in-memory (HashMap) cho 2 store, `Clock.fixed` tăng dần bằng biến `MutableClock` đơn giản hoặc dùng `Clock.systemUTC()` với TTL dài; các case:

```java
package io.javalibs.security.issuer;

import io.javalibs.security.InMemoryTokenBlacklist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class AuthenticationServiceTest {

    static class InMemoryRefreshTokenStore implements RefreshTokenStore {
        final Map<String, RefreshTokenRecord> byHash = new HashMap<>();

        @Override public void save(RefreshTokenRecord record) {
            byHash.put(record.tokenHash(), record);
        }
        @Override public Optional<RefreshTokenRecord> findByTokenHash(String tokenHash) {
            return Optional.ofNullable(byHash.get(tokenHash));
        }
        @Override public void markRotated(String tokenHash, String rotatedToHash, Instant revokedAt) {
            RefreshTokenRecord r = byHash.get(tokenHash);
            byHash.put(tokenHash, new RefreshTokenRecord(r.tokenHash(), r.userId(),
                    r.familyId(), r.expiresAt(), revokedAt, rotatedToHash));
        }
        @Override public void revoke(String tokenHash, Instant revokedAt) {
            RefreshTokenRecord r = byHash.get(tokenHash);
            if (r != null) {
                byHash.put(tokenHash, new RefreshTokenRecord(r.tokenHash(), r.userId(),
                        r.familyId(), r.expiresAt(), revokedAt, r.rotatedToHash()));
            }
        }
        @Override public void revokeFamily(String familyId, Instant revokedAt) {
            byHash.replaceAll((hash, r) -> r.familyId().equals(familyId) && r.revokedAt() == null
                    ? new RefreshTokenRecord(r.tokenHash(), r.userId(), r.familyId(),
                            r.expiresAt(), revokedAt, r.rotatedToHash())
                    : r);
        }
    }

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    PasswordHasher hasher = new PasswordHasher();
    InMemoryRefreshTokenStore refreshTokens = new InMemoryRefreshTokenStore();
    InMemoryTokenBlacklist blacklist = new InMemoryTokenBlacklist();
    CredentialsStore credentials;
    AuthenticationService service;

    @BeforeEach
    void setUp() {
        StoredCredentials alice = new StoredCredentials(
                "u1", "alice", "alice@example.com", hasher.hash("s3cret"), true);
        StoredCredentials disabled = new StoredCredentials(
                "u2", "bob", null, hasher.hash("s3cret"), false);
        Map<String, StoredCredentials> byUsername = Map.of("alice", alice, "bob", disabled);
        Map<String, StoredCredentials> byUserId = Map.of("u1", alice, "u2", disabled);
        credentials = new CredentialsStore() {
            @Override public Optional<StoredCredentials> findByUsername(String username) {
                return Optional.ofNullable(byUsername.get(username));
            }
            @Override public Optional<StoredCredentials> findByUserId(String userId) {
                return Optional.ofNullable(byUserId.get(userId));
            }
        };
        JwtIssuerConfig config = JwtIssuerConfig.builder().hmacSecret(SECRET).build();
        service = new AuthenticationService(credentials, refreshTokens, hasher,
                new TokenIssuer(config, Clock.systemUTC()), config, Clock.systemUTC(), blacklist);
    }

    @Test
    void loginIssuesTokenPairAndStoresHashedRefreshToken() {
        TokenPair pair = service.login("alice", "s3cret");
        assertThat(pair.accessToken()).isNotBlank();
        assertThat(refreshTokens.byHash)
                .containsKey(RefreshTokens.hash(pair.refreshToken()))
                .doesNotContainKey(pair.refreshToken());
    }

    @Test
    void loginRejectsWrongPasswordUnknownUserAndDisabledUser() {
        assertThatExceptionOfType(InvalidCredentialsException.class)
                .isThrownBy(() -> service.login("alice", "wrong"))
                .withMessage("Invalid credentials");
        assertThatExceptionOfType(InvalidCredentialsException.class)
                .isThrownBy(() -> service.login("nobody", "s3cret"))
                .withMessage("Invalid credentials");
        assertThatExceptionOfType(InvalidCredentialsException.class)
                .isThrownBy(() -> service.login("bob", "s3cret"))
                .withMessage("Invalid credentials");
    }

    @Test
    void refreshRotatesToken() {
        TokenPair first = service.login("alice", "s3cret");
        TokenPair second = service.refresh(first.refreshToken());
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        RefreshTokenRecord old = refreshTokens.byHash
                .get(RefreshTokens.hash(first.refreshToken()));
        assertThat(old.revokedAt()).isNotNull();
        assertThat(old.rotatedToHash())
                .isEqualTo(RefreshTokens.hash(second.refreshToken()));
    }

    @Test
    void reusingRotatedTokenRevokesWholeFamily() {
        TokenPair first = service.login("alice", "s3cret");
        TokenPair second = service.refresh(first.refreshToken());
        assertThatExceptionOfType(InvalidRefreshTokenException.class)
                .isThrownBy(() -> service.refresh(first.refreshToken()));
        // token mới cùng family cũng đã bị revoke
        assertThatExceptionOfType(InvalidRefreshTokenException.class)
                .isThrownBy(() -> service.refresh(second.refreshToken()));
    }

    @Test
    void unknownRefreshTokenIsRejected() {
        assertThatExceptionOfType(InvalidRefreshTokenException.class)
                .isThrownBy(() -> service.refresh(RefreshTokens.generate()));
    }

    @Test
    void logoutRevokesRefreshTokenAndBlacklistsAccessJti() {
        TokenPair pair = service.login("alice", "s3cret");
        service.logout(pair.refreshToken(), "jti-123");
        assertThat(blacklist.isRevoked("jti-123")).isTrue();
        assertThatExceptionOfType(InvalidRefreshTokenException.class)
                .isThrownBy(() -> service.refresh(pair.refreshToken()));
    }
}
```

- [ ] **Step 2: Chạy, xác nhận fail**

Run: `mvn -q test -pl javalibs-security/javalibs-security-issuer`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement** — các record/interface đúng chữ ký ở khối Interfaces; `AuthenticationService`:

```java
package io.javalibs.security.issuer;

import io.javalibs.security.TokenBlacklist;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Username/password authentication with rotating refresh tokens and reuse detection.
 * Wire the two stores from javalibs-authz-jpa (default) or your own implementation.
 */
public class AuthenticationService {

    /** Dummy BCrypt hash used to equalize timing when the username is unknown. */
    private static final String TIMING_NOISE_HASH =
            "{bcrypt}$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5B0m0FGz0dV7pJcyDgVOMZz2bhZTa";

    private final CredentialsStore credentialsStore;
    private final RefreshTokenStore refreshTokenStore;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;
    private final JwtIssuerConfig config;
    private final Clock clock;
    private final TokenBlacklist tokenBlacklist; // nullable

    public AuthenticationService(CredentialsStore credentialsStore,
            RefreshTokenStore refreshTokenStore, PasswordHasher passwordHasher,
            TokenIssuer tokenIssuer, JwtIssuerConfig config, Clock clock,
            TokenBlacklist tokenBlacklist) {
        this.credentialsStore = Objects.requireNonNull(credentialsStore);
        this.refreshTokenStore = Objects.requireNonNull(refreshTokenStore);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.tokenIssuer = Objects.requireNonNull(tokenIssuer);
        this.config = Objects.requireNonNull(config);
        this.clock = Objects.requireNonNull(clock);
        this.tokenBlacklist = tokenBlacklist;
    }

    /** Authenticates and issues a fresh access + refresh token pair (a new token family). */
    public TokenPair login(String username, String rawPassword) {
        Optional<StoredCredentials> found = credentialsStore.findByUsername(username);
        if (found.isEmpty()) {
            passwordHasher.matches(rawPassword, TIMING_NOISE_HASH);
            throw new InvalidCredentialsException("Invalid credentials");
        }
        StoredCredentials user = found.get();
        if (!passwordHasher.matches(rawPassword, user.passwordHash()) || !user.enabled()) {
            throw new InvalidCredentialsException("Invalid credentials");
        }
        return issuePair(user, UUID.randomUUID().toString());
    }

    /** Rotates the refresh token, revoking the whole family when reuse is detected. */
    public TokenPair refresh(String refreshToken) {
        Instant now = clock.instant();
        String hash = RefreshTokens.hash(refreshToken);
        RefreshTokenRecord record = refreshTokenStore.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is invalid"));
        if (record.isRevoked()) {
            if (record.wasRotated()) {
                // Reuse of an already-rotated token: assume theft, kill the whole session family.
                refreshTokenStore.revokeFamily(record.familyId(), now);
            }
            throw new InvalidRefreshTokenException("Refresh token is invalid");
        }
        if (record.isExpired(now)) {
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }
        StoredCredentials byId = credentialsStore.findByUserId(record.userId())
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is invalid"));
        if (!byId.enabled()) {
            throw new InvalidRefreshTokenException("Refresh token is invalid");
        }
        String newToken = RefreshTokens.generate();
        refreshTokenStore.markRotated(hash, RefreshTokens.hash(newToken), now);
        return issuePairWithRefreshToken(byId, record.familyId(), newToken);
    }

    /** Revokes the refresh token and, when a blacklist is configured, the access token id. */
    public void logout(String refreshToken, String accessTokenId) {
        Instant now = clock.instant();
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenStore.revoke(RefreshTokens.hash(refreshToken), now);
        }
        if (tokenBlacklist != null && accessTokenId != null && !accessTokenId.isBlank()) {
            tokenBlacklist.revoke(accessTokenId, now.plus(config.accessTokenTtl()));
        }
    }

    private TokenPair issuePair(StoredCredentials user, String familyId) {
        return issuePairWithRefreshToken(user, familyId, RefreshTokens.generate());
    }

    private TokenPair issuePairWithRefreshToken(StoredCredentials user, String familyId,
            String refreshToken) {
        TokenIssuer.IssuedToken access = tokenIssuer.issue(
                user.userId(), user.username(), user.email());
        Instant refreshExpiresAt = clock.instant().plus(config.refreshTokenTtl());
        refreshTokenStore.save(new RefreshTokenRecord(RefreshTokens.hash(refreshToken),
                user.userId(), familyId, refreshExpiresAt, null, null));
        return new TokenPair(access.token(), access.expiresAt(), refreshToken, refreshExpiresAt);
    }
}
```

**Chú ý:** `CredentialsStore` có **hai** method (fake trong test ở Step 1 đã implement cả hai):

```java
public interface CredentialsStore {
    Optional<StoredCredentials> findByUsername(String username);
    Optional<StoredCredentials> findByUserId(String userId);
}
```

- [ ] **Step 4: Chạy pass rồi commit**

Run: `mvn -q test -pl javalibs-security/javalibs-security-issuer`
Expected: PASS (7 tests).

```bash
git add javalibs-security/javalibs-security-issuer
git commit -m "feat(security): AuthenticationService với refresh rotation và reuse detection"
```

---

### Task 15: security-issuer — REST endpoint `/auth/*`

**Files:**
- Create: `javalibs-security/javalibs-security-issuer/src/main/java/io/javalibs/security/issuer/web/AuthEndpoints.java`
- Create: `javalibs-security/javalibs-security-issuer/src/main/java/io/javalibs/security/issuer/web/LoginRequest.java`
- Create: `javalibs-security/javalibs-security-issuer/src/main/java/io/javalibs/security/issuer/web/RefreshRequest.java`
- Create: `javalibs-security/javalibs-security-issuer/src/main/java/io/javalibs/security/issuer/web/LogoutRequest.java`
- Create: `javalibs-security/javalibs-security-issuer/src/main/java/io/javalibs/security/issuer/web/TokenResponse.java`
- Test: `javalibs-security/javalibs-security-issuer/src/test/java/io/javalibs/security/issuer/web/AuthEndpointsTest.java`
- Modify: `javalibs-security/javalibs-security-issuer/pom.xml` — thêm dependency `org.springframework:spring-webmvc` và test-scope `com.fasterxml.jackson.core:jackson-databind` nếu thiếu.

**Interfaces:**
- Consumes: `AuthenticationService`, `TokenPair`, exceptions (Task 14); `UserContextHolder` **không** dùng ở đây (module không phụ thuộc security-spring) — jti access token lấy từ `LogoutRequest`.
- Produces:
  - `record LoginRequest(String username, String password)`
  - `record RefreshRequest(String refreshToken)`
  - `record LogoutRequest(String refreshToken, String accessTokenId)` — `accessTokenId` (jti) optional
  - `record TokenResponse(String tokenType, String accessToken, Instant accessTokenExpiresAt, String refreshToken, Instant refreshTokenExpiresAt)` + factory `static TokenResponse from(TokenPair pair)` (tokenType luôn `"Bearer"`)
  - `class AuthEndpoints` — `POST {base}/login`, `POST {base}/refresh`, `POST {base}/logout` (204); base path từ property placeholder `${javalibs.security.issuer.endpoints.base-path:/auth}`; `@ExceptionHandler` trả 401 JSON `{"timestamp","status":401,"code","message"}` với code `ERR_INVALID_CREDENTIALS` / `ERR_INVALID_REFRESH_TOKEN`; body thiếu field → 400 do Spring tự xử.

- [ ] **Step 1: Viết test fail** — standalone MockMvc:

```java
package io.javalibs.security.issuer.web;

import io.javalibs.security.issuer.AuthenticationService;
import io.javalibs.security.issuer.InvalidCredentialsException;
import io.javalibs.security.issuer.InvalidRefreshTokenException;
import io.javalibs.security.issuer.TokenPair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthEndpointsTest {

    AuthenticationService service = Mockito.mock(AuthenticationService.class);
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AuthEndpoints(service)).build();
    }

    @Test
    void loginReturnsTokenPair() throws Exception {
        Mockito.when(service.login("alice", "s3cret")).thenReturn(new TokenPair(
                "access-jwt", Instant.parse("2026-07-17T00:15:00Z"),
                "refresh-opaque", Instant.parse("2026-08-16T00:00:00Z")));
        mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"s3cret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").value("access-jwt"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-opaque"));
    }

    @Test
    void invalidCredentialsBecome401Json() throws Exception {
        Mockito.when(service.login(any(), any()))
                .thenThrow(new InvalidCredentialsException("Invalid credentials"));
        mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"bad\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("ERR_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void invalidRefreshTokenBecomes401Json() throws Exception {
        Mockito.when(service.refresh(any()))
                .thenThrow(new InvalidRefreshTokenException("Refresh token is invalid"));
        mvc.perform(post("/auth/refresh").contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"stale\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logoutReturns204() throws Exception {
        mvc.perform(post("/auth/logout").contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"r\",\"accessTokenId\":\"jti-1\"}"))
                .andExpect(status().isNoContent());
        Mockito.verify(service).logout(eq("r"), eq("jti-1"));
    }
}
```

Lưu ý: standalone MockMvc không resolve property placeholder trong `@RequestMapping` — vì vậy đặt placeholder có default `/auth` (`${javalibs.security.issuer.endpoints.base-path:/auth}`); nếu standalone setup vẫn lỗi placeholder, thêm `.addPlaceholderValue("javalibs.security.issuer.endpoints.base-path", "/auth")` vào builder.

- [ ] **Step 2: Chạy, xác nhận fail**

Run: `mvn -q test -pl javalibs-security/javalibs-security-issuer`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement**

DTO records (mỗi record một file, Javadoc một dòng):

```java
public record LoginRequest(String username, String password) { }
public record RefreshRequest(String refreshToken) { }
public record LogoutRequest(String refreshToken, String accessTokenId) { }

public record TokenResponse(String tokenType, String accessToken,
        Instant accessTokenExpiresAt, String refreshToken, Instant refreshTokenExpiresAt) {

    public static TokenResponse from(TokenPair pair) {
        return new TokenResponse("Bearer", pair.accessToken(), pair.accessTokenExpiresAt(),
                pair.refreshToken(), pair.refreshTokenExpiresAt());
    }
}
```

```java
package io.javalibs.security.issuer.web;

import io.javalibs.security.issuer.AuthenticationService;
import io.javalibs.security.issuer.InvalidCredentialsException;
import io.javalibs.security.issuer.InvalidRefreshTokenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Ready-to-use authentication endpoints: POST /auth/login, /auth/refresh, /auth/logout.
 * Auto-configured by javalibs-security when this module is on the classpath; the base path
 * is configurable via javalibs.security.issuer.endpoints.base-path. Remember to permit the
 * base path in javalibs.security.permit-all — login/refresh are anonymous by nature.
 */
@RestController
@RequestMapping("${javalibs.security.issuer.endpoints.base-path:/auth}")
public class AuthEndpoints {

    /** Machine readable error code for rejected logins. */
    public static final String ERR_INVALID_CREDENTIALS = "ERR_INVALID_CREDENTIALS";

    /** Machine readable error code for rejected refresh tokens. */
    public static final String ERR_INVALID_REFRESH_TOKEN = "ERR_INVALID_REFRESH_TOKEN";

    private final AuthenticationService authenticationService;

    public AuthEndpoints(AuthenticationService authenticationService) {
        this.authenticationService = Objects.requireNonNull(authenticationService);
    }

    /** Authenticates with username/password and returns a fresh token pair. */
    @PostMapping("/login")
    public TokenResponse login(@RequestBody LoginRequest request) {
        return TokenResponse.from(
                authenticationService.login(request.username(), request.password()));
    }

    /** Rotates the refresh token and returns a fresh token pair. */
    @PostMapping("/refresh")
    public TokenResponse refresh(@RequestBody RefreshRequest request) {
        return TokenResponse.from(authenticationService.refresh(request.refreshToken()));
    }

    /** Revokes the refresh token and blacklists the access token id when provided. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody LogoutRequest request) {
        authenticationService.logout(request.refreshToken(), request.accessTokenId());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<Map<String, Object>> onInvalidCredentials(InvalidCredentialsException ex) {
        return unauthorized(ERR_INVALID_CREDENTIALS, ex.getMessage());
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    ResponseEntity<Map<String, Object>> onInvalidRefreshToken(InvalidRefreshTokenException ex) {
        return unauthorized(ERR_INVALID_REFRESH_TOKEN, ex.getMessage());
    }

    private static ResponseEntity<Map<String, Object>> unauthorized(String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("code", code);
        body.put("message", message);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }
}
```

- [ ] **Step 4: Chạy pass rồi commit**

Run: `mvn -q test -pl javalibs-security/javalibs-security-issuer`
Expected: PASS.

```bash
git add javalibs-security/javalibs-security-issuer
git commit -m "feat(security): endpoint /auth/login, /auth/refresh, /auth/logout"
```

---

### Task 16: security autoconfigure — `IssuerProperties` + `SecurityIssuerAutoConfiguration`

**Files:**
- Create: `javalibs-security/javalibs-security-spring-boot-autoconfigure/src/main/java/io/javalibs/security/autoconfigure/IssuerProperties.java`
- Create: `javalibs-security/javalibs-security-spring-boot-autoconfigure/src/main/java/io/javalibs/security/autoconfigure/SecurityIssuerAutoConfiguration.java`
- Modify: `javalibs-security/javalibs-security-spring-boot-autoconfigure/pom.xml` — thêm dependency optional `io.javalibs:javalibs-security-issuer:${project.version}`
- Modify: `javalibs-security/javalibs-security-spring-boot-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` — thêm dòng `io.javalibs.security.autoconfigure.SecurityIssuerAutoConfiguration`
- Test: `javalibs-security/javalibs-security-spring-boot-autoconfigure/src/test/java/io/javalibs/security/autoconfigure/SecurityIssuerAutoConfigurationTest.java`

**Interfaces:**
- Consumes: mọi type issuer (Task 12–15); `SecurityProperties` (secret/issuer/audience/claim names dùng chung với validator).
- Produces: properties `javalibs.security.issuer.enabled` (true), `.private-key` (PEM PKCS#8, cho RS256), `.access-token-ttl` (15m), `.refresh-token-ttl` (30d), `.endpoints.enabled` (true), `.endpoints.base-path` (/auth). Bean (`@ConditionalOnMissingBean` từng cái): `PasswordHasher`, `JwtIssuerConfig`, `TokenIssuer`, `AuthenticationService` (`@ConditionalOnBean({CredentialsStore.class, RefreshTokenStore.class})`, blacklist qua `ObjectProvider`), `AuthEndpoints` (`@ConditionalOnBean(AuthenticationService.class)` + `@ConditionalOnProperty endpoints.enabled` + `@ConditionalOnWebApplication`).

- [ ] **Step 1: Viết test fail**

```java
package io.javalibs.security.autoconfigure;

import io.javalibs.security.issuer.AuthenticationService;
import io.javalibs.security.issuer.CredentialsStore;
import io.javalibs.security.issuer.PasswordHasher;
import io.javalibs.security.issuer.RefreshTokenRecord;
import io.javalibs.security.issuer.RefreshTokenStore;
import io.javalibs.security.issuer.StoredCredentials;
import io.javalibs.security.issuer.TokenIssuer;
import io.javalibs.security.issuer.web.AuthEndpoints;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityIssuerAutoConfigurationTest {

    private static final String SECRET_PROPERTY =
            "javalibs.security.jwt.secret=0123456789abcdef0123456789abcdef";

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecurityIssuerAutoConfiguration.class))
            .withPropertyValues(SECRET_PROPERTY);

    @Configuration(proxyBeanMethods = false)
    static class StoreConfiguration {
        @Bean
        CredentialsStore credentialsStore() {
            return new CredentialsStore() {
                @Override public Optional<StoredCredentials> findByUsername(String username) {
                    return Optional.empty();
                }
                @Override public Optional<StoredCredentials> findByUserId(String userId) {
                    return Optional.empty();
                }
            };
        }
        @Bean
        RefreshTokenStore refreshTokenStore() {
            return new RefreshTokenStore() {
                @Override public void save(RefreshTokenRecord record) { }
                @Override public Optional<RefreshTokenRecord> findByTokenHash(String hash) {
                    return Optional.empty();
                }
                @Override public void markRotated(String h, String to, Instant at) { }
                @Override public void revoke(String h, Instant at) { }
                @Override public void revokeFamily(String familyId, Instant at) { }
            };
        }
    }

    @Test
    void wiresIssuerBeansWhenStoresPresent() {
        runner.withUserConfiguration(StoreConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(PasswordHasher.class);
            assertThat(context).hasSingleBean(TokenIssuer.class);
            assertThat(context).hasSingleBean(AuthenticationService.class);
            assertThat(context).hasSingleBean(AuthEndpoints.class);
        });
    }

    @Test
    void skipsAuthenticationServiceWithoutStores() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(TokenIssuer.class);
            assertThat(context).doesNotHaveBean(AuthenticationService.class);
            assertThat(context).doesNotHaveBean(AuthEndpoints.class);
        });
    }

    @Test
    void endpointsCanBeDisabled() {
        runner.withUserConfiguration(StoreConfiguration.class)
                .withPropertyValues("javalibs.security.issuer.endpoints.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(AuthenticationService.class);
                    assertThat(context).doesNotHaveBean(AuthEndpoints.class);
                });
    }

    @Test
    void backsOffWhenDisabled() {
        runner.withPropertyValues("javalibs.security.issuer.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(TokenIssuer.class));
    }
}
```

- [ ] **Step 2: Chạy, xác nhận fail**

Run: `mvn -q test -pl javalibs-security/javalibs-security-spring-boot-autoconfigure`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement**

`IssuerProperties.java` — `@ConfigurationProperties("javalibs.security.issuer")`, phong cách `SecurityProperties`:

```java
package io.javalibs.security.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Configuration properties for token issuing ({@code javalibs.security.issuer.*}). */
@ConfigurationProperties("javalibs.security.issuer")
public class IssuerProperties {

    /** Whether the issuer auto-configuration is active. */
    private boolean enabled = true;

    /**
     * RSA private key (PKCS#8 PEM) used to RS256-sign issued tokens. When unset, tokens are
     * HS256-signed with javalibs.security.jwt.secret.
     */
    private String privateKey;

    /** Lifetime of issued access tokens. */
    private Duration accessTokenTtl = Duration.ofMinutes(15);

    /** Lifetime of issued refresh tokens. */
    private Duration refreshTokenTtl = Duration.ofDays(30);

    private final Endpoints endpoints = new Endpoints();

    // getters/setters + getEndpoints()

    /** Built-in REST endpoint settings ({@code javalibs.security.issuer.endpoints.*}). */
    public static class Endpoints {
        /** Whether the built-in /auth endpoints are registered. */
        private boolean enabled = true;
        /** Base path of the built-in endpoints. Add it to javalibs.security.permit-all. */
        private String basePath = "/auth";
        // getters/setters
    }
}
```

`SecurityIssuerAutoConfiguration.java`:

```java
package io.javalibs.security.autoconfigure;

import io.javalibs.security.TokenBlacklist;
import io.javalibs.security.issuer.AuthenticationService;
import io.javalibs.security.issuer.CredentialsStore;
import io.javalibs.security.issuer.JwtIssuerConfig;
import io.javalibs.security.issuer.PasswordHasher;
import io.javalibs.security.issuer.RefreshTokenStore;
import io.javalibs.security.issuer.TokenIssuer;
import io.javalibs.security.issuer.web.AuthEndpoints;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

import java.time.Clock;

/**
 * Auto-configuration for token issuing. Requires application-provided (or
 * javalibs-authz-jpa-provided) CredentialsStore and RefreshTokenStore beans for the
 * AuthenticationService; signing reuses the javalibs.security.jwt.* settings so every
 * javalibs service validates issued tokens out of the box.
 */
@AutoConfiguration
@ConditionalOnClass(AuthenticationService.class)
@ConditionalOnProperty(prefix = "javalibs.security.issuer", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({SecurityProperties.class, IssuerProperties.class})
public class SecurityIssuerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PasswordHasher javalibsPasswordHasher() {
        return new PasswordHasher();
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtIssuerConfig javalibsJwtIssuerConfig(SecurityProperties security,
            IssuerProperties issuer) {
        SecurityProperties.Jwt jwt = security.getJwt();
        boolean hasPrivateKey = StringUtils.hasText(issuer.getPrivateKey());
        if (!hasPrivateKey && !StringUtils.hasText(jwt.getSecret())) {
            throw new IllegalStateException(
                    "Token issuing requires javalibs.security.jwt.secret (HS256) or "
                            + "javalibs.security.issuer.private-key (RS256). Configure one, "
                            + "or disable issuing with javalibs.security.issuer.enabled=false.");
        }
        return JwtIssuerConfig.builder()
                .hmacSecret(hasPrivateKey ? null : jwt.getSecret())
                .rsaPrivateKeyPem(issuer.getPrivateKey())
                .issuer(jwt.getIssuer())
                .audience(jwt.getAudience())
                .accessTokenTtl(issuer.getAccessTokenTtl())
                .refreshTokenTtl(issuer.getRefreshTokenTtl())
                .usernameClaim(jwt.getUsernameClaim())
                .emailClaim(jwt.getEmailClaim())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public TokenIssuer javalibsTokenIssuer(JwtIssuerConfig config) {
        return new TokenIssuer(config, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnBean({CredentialsStore.class, RefreshTokenStore.class})
    @ConditionalOnMissingBean
    public AuthenticationService javalibsAuthenticationService(CredentialsStore credentials,
            RefreshTokenStore refreshTokens, PasswordHasher passwordHasher,
            TokenIssuer tokenIssuer, JwtIssuerConfig config,
            ObjectProvider<TokenBlacklist> tokenBlacklist) {
        return new AuthenticationService(credentials, refreshTokens, passwordHasher,
                tokenIssuer, config, Clock.systemUTC(), tokenBlacklist.getIfAvailable());
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnBean(AuthenticationService.class)
    @ConditionalOnProperty(prefix = "javalibs.security.issuer.endpoints", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public AuthEndpoints javalibsAuthEndpoints(AuthenticationService authenticationService) {
        return new AuthEndpoints(authenticationService);
    }
}
```

- [ ] **Step 4: Chạy pass rồi commit**

Run: `mvn -q test -pl javalibs-security/javalibs-security-spring-boot-autoconfigure`
Expected: PASS (cả các test autoconfigure cũ vẫn xanh).

```bash
git add javalibs-security
git commit -m "feat(security): autoconfigure issuer (IssuerProperties + SecurityIssuerAutoConfiguration)"
```

---

### Task 17: authz-jpa — adapter store cho issuer (V2 migration)

**Files:**
- Create: `javalibs-authz/javalibs-authz-jpa/src/main/resources/db/migration/javalibs-authz/V2__authz_refresh_token.sql`
- Create: `javalibs-authz/javalibs-authz-jpa/src/main/java/io/javalibs/authz/jpa/AuthzRefreshTokenEntity.java`
- Create: `javalibs-authz/javalibs-authz-jpa/src/main/java/io/javalibs/authz/jpa/AuthzRefreshTokenRepository.java`
- Create: `javalibs-authz/javalibs-authz-jpa/src/main/java/io/javalibs/authz/jpa/issuer/JpaCredentialsStore.java`
- Create: `javalibs-authz/javalibs-authz-jpa/src/main/java/io/javalibs/authz/jpa/issuer/JpaRefreshTokenStore.java`
- Modify: `javalibs-authz/javalibs-authz-jpa/pom.xml` — thêm dependency optional `io.javalibs:javalibs-security-issuer:${project.version}` (đã hoãn từ Task 1)
- Modify: `javalibs-authz/javalibs-authz-spring-boot-autoconfigure/src/main/java/io/javalibs/authz/autoconfigure/AuthzJpaAutoConfiguration.java` — thêm nested `IssuerStoreConfiguration`
- Modify: `javalibs-authz/javalibs-authz-spring-boot-autoconfigure/pom.xml` — thêm dependency optional `io.javalibs:javalibs-security-issuer:${project.version}`
- Test: `javalibs-authz/javalibs-authz-jpa/src/test/java/io/javalibs/authz/jpa/issuer/JpaIssuerStoresIT.java`

**Interfaces:**
- Consumes: `CredentialsStore`, `StoredCredentials`, `RefreshTokenStore`, `RefreshTokenRecord` (Task 14); `AuthzUserRepository` (Task 8).
- Produces: `class JpaCredentialsStore implements CredentialsStore { JpaCredentialsStore(AuthzUserRepository); }` (`findByUserId` với chuỗi không phải UUID → empty); `class JpaRefreshTokenStore implements RefreshTokenStore { JpaRefreshTokenStore(AuthzRefreshTokenRepository); }`; nested autoconfig đăng ký cả hai `@ConditionalOnClass(CredentialsStore.class)` + `@ConditionalOnMissingBean`.

- [ ] **Step 1: Migration `V2__authz_refresh_token.sql`**

```sql
CREATE TABLE authz_refresh_token (
    id              UUID PRIMARY KEY,
    user_id         UUID        NOT NULL REFERENCES authz_user (id) ON DELETE CASCADE,
    token_hash      VARCHAR(64) NOT NULL UNIQUE,
    family_id       VARCHAR(36) NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    rotated_to_hash VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_authz_refresh_token_family ON authz_refresh_token (family_id);
```

- [ ] **Step 2: Viết integration test fail**

```java
package io.javalibs.authz.jpa.issuer;

import io.javalibs.authz.jpa.AuthzRefreshTokenRepository;
import io.javalibs.authz.jpa.AuthzUserRepository;
import io.javalibs.security.issuer.RefreshTokenRecord;
import io.javalibs.test.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JpaIssuerStoresIT extends BaseIntegrationTest {

    @Autowired AuthzUserRepository users;
    @Autowired AuthzRefreshTokenRepository tokens;

    private UUID createUser(String username) {
        var user = new io.javalibs.authz.jpa.AuthzUserEntity();
        user.setUsername(username);
        user.setEmail(username + "@x.io");
        user.setPasswordHash("{bcrypt}h");
        return users.save(user).getId();
    }

    @Test
    void credentialsStoreFindsByUsernameAndUserId() {
        UUID id = createUser("store-alice");
        JpaCredentialsStore store = new JpaCredentialsStore(users);

        var byName = store.findByUsername("store-alice").orElseThrow();
        assertThat(byName.userId()).isEqualTo(id.toString());
        assertThat(byName.passwordHash()).isEqualTo("{bcrypt}h");
        assertThat(store.findByUserId(id.toString())).isPresent();
        assertThat(store.findByUserId("not-a-uuid")).isEmpty();
        assertThat(store.findByUsername("ghost")).isEmpty();
    }

    @Test
    void refreshTokenStoreRoundTripRotateAndRevokeFamily() {
        UUID id = createUser("store-bob");
        JpaRefreshTokenStore store = new JpaRefreshTokenStore(tokens);
        Instant expires = Instant.now().plus(30, ChronoUnit.DAYS);

        store.save(new RefreshTokenRecord("hash-1", id.toString(), "fam-1", expires, null, null));
        store.save(new RefreshTokenRecord("hash-2", id.toString(), "fam-1", expires, null, null));

        assertThat(store.findByTokenHash("hash-1")).isPresent();

        Instant now = Instant.now();
        store.markRotated("hash-1", "hash-2", now);
        RefreshTokenRecord rotated = store.findByTokenHash("hash-1").orElseThrow();
        assertThat(rotated.revokedAt()).isNotNull();
        assertThat(rotated.rotatedToHash()).isEqualTo("hash-2");

        store.revokeFamily("fam-1", now);
        assertThat(store.findByTokenHash("hash-2").orElseThrow().revokedAt()).isNotNull();
    }
}
```

- [ ] **Step 3: Chạy, xác nhận fail**

Run: `mvn -q verify -pl javalibs-authz/javalibs-authz-jpa`
Expected: COMPILATION ERROR.

- [ ] **Step 4: Implement**

Entity + repository:

```java
package io.javalibs.authz.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** JPA mapping of the {@code authz_refresh_token} table (hashed rotating refresh tokens). */
@Entity
@Table(name = "authz_refresh_token")
public class AuthzRefreshTokenEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "rotated_to_hash", length = 64)
    private String rotatedToHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    // getters/setters
}
```

```java
package io.javalibs.authz.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link AuthzRefreshTokenEntity}. */
public interface AuthzRefreshTokenRepository extends JpaRepository<AuthzRefreshTokenEntity, UUID> {

    Optional<AuthzRefreshTokenEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update AuthzRefreshTokenEntity t set t.revokedAt = :revokedAt "
            + "where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") String familyId, @Param("revokedAt") Instant revokedAt);
}
```

Adapters (package `io.javalibs.authz.jpa.issuer`):

```java
package io.javalibs.authz.jpa.issuer;

import io.javalibs.authz.jpa.AuthzUserEntity;
import io.javalibs.authz.jpa.AuthzUserRepository;
import io.javalibs.security.issuer.CredentialsStore;
import io.javalibs.security.issuer.StoredCredentials;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** {@link CredentialsStore} backed by the {@code authz_user} table. */
public class JpaCredentialsStore implements CredentialsStore {

    private final AuthzUserRepository userRepository;

    public JpaCredentialsStore(AuthzUserRepository userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredCredentials> findByUsername(String username) {
        return userRepository.findByUsername(username).map(JpaCredentialsStore::toCredentials);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredCredentials> findByUserId(String userId) {
        UUID uuid;
        try {
            uuid = UUID.fromString(userId);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        return userRepository.findById(uuid).map(JpaCredentialsStore::toCredentials);
    }

    private static StoredCredentials toCredentials(AuthzUserEntity user) {
        return new StoredCredentials(user.getId().toString(), user.getUsername(),
                user.getEmail(), user.getPasswordHash(), user.isEnabled());
    }
}
```

```java
package io.javalibs.authz.jpa.issuer;

import io.javalibs.authz.jpa.AuthzRefreshTokenEntity;
import io.javalibs.authz.jpa.AuthzRefreshTokenRepository;
import io.javalibs.security.issuer.RefreshTokenRecord;
import io.javalibs.security.issuer.RefreshTokenStore;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** {@link RefreshTokenStore} backed by the {@code authz_refresh_token} table. */
public class JpaRefreshTokenStore implements RefreshTokenStore {

    private final AuthzRefreshTokenRepository repository;

    public JpaRefreshTokenStore(AuthzRefreshTokenRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    @Transactional
    public void save(RefreshTokenRecord record) {
        AuthzRefreshTokenEntity entity = new AuthzRefreshTokenEntity();
        entity.setUserId(UUID.fromString(record.userId()));
        entity.setTokenHash(record.tokenHash());
        entity.setFamilyId(record.familyId());
        entity.setExpiresAt(record.expiresAt());
        entity.setRevokedAt(record.revokedAt());
        entity.setRotatedToHash(record.rotatedToHash());
        repository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshTokenRecord> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(entity -> new RefreshTokenRecord(
                entity.getTokenHash(), entity.getUserId().toString(), entity.getFamilyId(),
                entity.getExpiresAt(), entity.getRevokedAt(), entity.getRotatedToHash()));
    }

    @Override
    @Transactional
    public void markRotated(String tokenHash, String rotatedToHash, Instant revokedAt) {
        repository.findByTokenHash(tokenHash).ifPresent(entity -> {
            entity.setRevokedAt(revokedAt);
            entity.setRotatedToHash(rotatedToHash);
            repository.save(entity);
        });
    }

    @Override
    @Transactional
    public void revoke(String tokenHash, Instant revokedAt) {
        repository.findByTokenHash(tokenHash).ifPresent(entity -> {
            entity.setRevokedAt(revokedAt);
            repository.save(entity);
        });
    }

    @Override
    @Transactional
    public void revokeFamily(String familyId, Instant revokedAt) {
        repository.revokeFamily(familyId, revokedAt);
    }
}
```

Nested autoconfig thêm vào cuối class `AuthzJpaAutoConfiguration`:

```java
    /** Wires the issuer stores when javalibs-security-issuer is on the classpath. */
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(io.javalibs.security.issuer.CredentialsStore.class)
    static class IssuerStoreConfiguration {

        @Bean
        @ConditionalOnMissingBean(io.javalibs.security.issuer.CredentialsStore.class)
        JpaCredentialsStore javalibsJpaCredentialsStore(AuthzUserRepository userRepository) {
            return new io.javalibs.authz.jpa.issuer.JpaCredentialsStore(userRepository);
        }

        @Bean
        @ConditionalOnMissingBean(io.javalibs.security.issuer.RefreshTokenStore.class)
        JpaRefreshTokenStore javalibsJpaRefreshTokenStore(
                AuthzRefreshTokenRepository repository) {
            return new io.javalibs.authz.jpa.issuer.JpaRefreshTokenStore(repository);
        }
    }
```

(dùng import bình thường thay vì FQN — FQN ở đây chỉ để rõ nguồn gốc type).

- [ ] **Step 5: Chạy pass rồi commit**

Run: `mvn -q verify -pl javalibs-authz/javalibs-authz-jpa && mvn -q test -pl javalibs-authz/javalibs-authz-spring-boot-autoconfigure`
Expected: PASS.

```bash
git add javalibs-authz
git commit -m "feat(authz): JpaCredentialsStore/JpaRefreshTokenStore cho issuer + V2 migration"
```

---

### Task 18: E2E integration test — login → @RequirePermission → refresh → logout

**Files:**
- Modify: `javalibs-authz/javalibs-authz-jpa/pom.xml` — thêm test-scope: `io.javalibs:javalibs-authz-spring-boot-starter`, `io.javalibs:javalibs-security-spring-boot-starter`, `io.javalibs:javalibs-security-issuer` (chuyển từ optional thành vẫn optional ở compile + test-scope không cần vì optional đã cho compile; chỉ cần thêm 2 starter), `org.springframework.boot:spring-boot-starter-web`
- Create: `javalibs-authz/javalibs-authz-jpa/src/test/java/io/javalibs/authz/jpa/e2e/E2eTestController.java`
- Create: `javalibs-authz/javalibs-authz-jpa/src/test/resources/application-e2e.yml`
- Test: `javalibs-authz/javalibs-authz-jpa/src/test/java/io/javalibs/authz/jpa/e2e/AuthFlowE2eIT.java`

**Interfaces:**
- Consumes: toàn bộ starter authz + security + issuer (mọi task trước).

- [ ] **Step 1: Controller test + cấu hình**

`E2eTestController.java`:

```java
package io.javalibs.authz.jpa.e2e;

import io.javalibs.authz.spring.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Test-only endpoint guarded by a project-scoped permission. */
@RestController
public class E2eTestController {

    @GetMapping("/api/projects/{projectId}/issues")
    @RequirePermission(value = "issue.read", scopeType = "project", scopeIdParam = "projectId")
    public Map<String, String> issues(@PathVariable String projectId) {
        return Map.of("project", projectId);
    }
}
```

`application-e2e.yml` (profile `e2e` cộng thêm profile `test` sẵn có của `BaseIntegrationTest`):

```yaml
javalibs:
  security:
    jwt:
      secret: e2e-secret-0123456789abcdef-0123456789abcdef
      issuer: https://e2e.javalibs.io
    permit-all:
      - /auth/**
    blacklist:
      mode: in-memory
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

(`spring.flyway.locations` không cần đặt — FlywayConfigurationCustomizer của autoconfigure tự thêm `classpath:db/migration/javalibs-authz`; nếu vị trí mặc định `classpath:db/migration` không tồn tại làm Flyway kêu, đặt `spring.flyway.locations: classpath:db/migration/javalibs-authz` trực tiếp.)

- [ ] **Step 2: Viết E2E test**

```java
package io.javalibs.authz.jpa.e2e;

import io.javalibs.authz.Scope;
import io.javalibs.authz.Subject;
import io.javalibs.authz.jpa.AuthzManagementService;
import io.javalibs.security.issuer.PasswordHasher;
import io.javalibs.test.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles({"test", "e2e"})
class AuthFlowE2eIT extends BaseIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired AuthzManagementService authz;
    @Autowired PasswordHasher hasher;

    boolean seeded;

    @BeforeEach
    void seed() {
        if (seeded) {
            return;
        }
        UUID userId = authz.createUser("e2e-alice", "alice@e2e.io", "Alice",
                hasher.hash("s3cret"), true);
        authz.createRole("e2e-viewer", "Viewer", null, Set.of("issue.read"));
        authz.grantRole(Subject.user(userId.toString()), "e2e-viewer",
                Scope.of("project", "p1"));
        seeded = true;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> login() {
        ResponseEntity<Map> response = rest.postForEntity("/auth/login",
                Map.of("username", "e2e-alice", "password", "s3cret"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<String> getWithToken(String path, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test
    void fullAuthAndAuthzFlow() {
        Map<String, Object> tokens = login();
        String access = (String) tokens.get("accessToken");
        String refresh = (String) tokens.get("refreshToken");

        // 1. Quyền per-project: p1 được cấp, p2 không
        assertThat(getWithToken("/api/projects/p1/issues", access).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(getWithToken("/api/projects/p2/issues", access).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // 2. Không token → 401
        assertThat(rest.getForEntity("/api/projects/p1/issues", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // 3. Refresh rotation
        ResponseEntity<Map> refreshed = rest.postForEntity("/auth/refresh",
                Map.of("refreshToken", refresh), Map.class);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        String newRefresh = (String) refreshed.getBody().get("refreshToken");
        assertThat(newRefresh).isNotEqualTo(refresh);

        // 4. Reuse detection: refresh cũ → 401, và cả family chết → refresh mới cũng 401
        assertThat(rest.postForEntity("/auth/refresh",
                        Map.of("refreshToken", refresh), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rest.postForEntity("/auth/refresh",
                        Map.of("refreshToken", newRefresh), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // 5. Logout blacklist access token (jti lấy từ login mới)
        Map<String, Object> tokens2 = login();
        String access2 = (String) tokens2.get("accessToken");
        String refresh2 = (String) tokens2.get("refreshToken");
        assertThat(getWithToken("/api/projects/p1/issues", access2).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        String jti = io.jsonwebtoken.Jwts.parser()
                .verifyWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "e2e-secret-0123456789abcdef-0123456789abcdef".getBytes()))
                .build().parseSignedClaims(access2).getPayload().getId();
        ResponseEntity<Void> logout = rest.postForEntity("/auth/logout",
                Map.of("refreshToken", refresh2, "accessTokenId", jti), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getWithToken("/api/projects/p1/issues", access2).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
```

- [ ] **Step 3: Chạy, sửa đến khi pass**

Run: `mvn -q verify -pl javalibs-authz/javalibs-authz-jpa`
Expected: PASS toàn bộ IT (schema, resolvers, management, issuer stores, e2e). Các lỗi wiring hay gặp: thiếu permit-all cho `/auth/**` (401 ở login), Flyway location, thứ tự autoconfiguration — sửa trong autoconfigure chứ không hack trong test.

- [ ] **Step 4: Chạy full build toàn repo**

Run: `mvn -q install -DskipTests=false`
Expected: BUILD SUCCESS, mọi module xanh (bao gồm test cũ của security/web...).

- [ ] **Step 5: Commit**

```bash
git add javalibs-authz
git commit -m "test(authz): E2E login → @RequirePermission → refresh rotation → logout"
```

---

### Task 19: Tài liệu

**Files:**
- Create: `docs/modules/authz.md`
- Modify: `docs/modules/security.md` (thêm mục issuer: cấu hình `javalibs.security.issuer.*`, endpoint `/auth/*`, nhắc thêm `/auth/**` vào permit-all)
- Modify: `docs/index.md` (thêm link authz.md)
- Modify: `docs/configuration-reference.md` (thêm bảng `javalibs.authz.*` và `javalibs.security.issuer.*`)
- Modify: `docs/cookbook.md` (recipe "Dựng auth-service + phân quyền per-project kiểu YouTrack")
- Modify: `README.md` (thêm dòng `javalibs-authz-*` và cập nhật dòng security trong bảng module)

**Nội dung bắt buộc trong `docs/modules/authz.md`** (tiếng Việt, theo format các file docs/modules hiện có — đọc `docs/modules/security.md` để khớp cấu trúc):
1. Mô hình khái niệm: Permission → Role → Grant (User/Group) → Scope (GLOBAL / scopeType+scopeId) — kèm ví dụ YouTrack (issue.read trong project).
2. Ma trận module (core/spring/jpa/autoconfigure/starter) — ai nhúng gì.
3. Quickstart: thêm starter + authz-jpa, khai báo PermissionCatalog bean, tạo role/grant qua AuthzManagementService, dùng `@RequirePermission` và `PermissionChecker` (code ví dụ chạy được).
4. Bảng cấu hình `javalibs.authz.*` (đủ 6 property với default).
5. Cache: ngữ nghĩa TTL, evict chủ động cùng instance, TTL là chốt an toàn multi-instance.
6. SPI: cách app tự implement GrantResolver/GroupMembershipResolver khi có schema riêng.
7. Giới hạn v1: không nested group, không deny, không rate-limit (trỏ javalibs-resilience).

**Nội dung bắt buộc thêm vào `docs/modules/security.md`**: mục "Phát hành token (issuer)" — bảng cấu hình `javalibs.security.issuer.*`, mô tả 3 endpoint với request/response JSON mẫu, rotation + reuse detection, logout + blacklist, lưu ý HS256 dùng chung secret / RS256 cần private-key + public-key.

- [ ] **Step 1: Viết docs** theo outline trên (đọc trước 1-2 file docs/modules hiện có để khớp giọng văn/format).
- [ ] **Step 2: Kiểm tra chéo** — mọi property nêu trong docs phải tồn tại đúng tên trong `AuthzProperties`/`IssuerProperties`; mọi class nêu tên phải tồn tại.
- [ ] **Step 3: Commit**

```bash
git add docs README.md
git commit -m "docs: tài liệu javalibs-authz và javalibs-security-issuer"
```

---

## Ghi chú thực thi

- Thứ tự task là thứ tự phụ thuộc; Task 12–16 (issuer) không phụ thuộc Task 5–11 (trừ Task 17–18 cần cả hai) — có thể chạy song song 2 nhánh nếu dùng subagent.
- Sau mỗi task chạy lại test của các module bị ảnh hưởng; trước Task 18 nên `mvn -q install -DskipTests` toàn repo để BOM/reactor đồng bộ.
- Mọi bean autoconfigure đặt tên prefix `javalibs` (như `javalibsPermissionChecker`) theo convention hiện có.
- Nếu gặp bug/hành vi lạ: dùng skill superpowers:systematic-debugging, không vá mù.

