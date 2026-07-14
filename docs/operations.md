# Vận hành — Build, Release, Publish, CI/CD

## Build & test

```bash
mvn clean install            # build + toàn bộ unit/integration test (~350 test)
mvn clean install -DskipTests
mvn -pl javalibs-security -amd clean install   # chỉ một nhóm module + những gì phụ thuộc nó
mvn -f javalibs-web/pom.xml test               # build một module con độc lập
mvn clean verify             # kèm báo cáo JaCoCo (target/site/jacoco của từng module)
```

Yêu cầu môi trường (enforcer chặn nếu thiếu): **JDK 21+, Maven 3.6.3+**. Test của javalibs không cần Docker (H2 + mock); Docker chỉ cần cho client dùng `javalibs-test`.

## Versioning & release

- Toàn bộ artifact chia sẻ **một version duy nhất** (hiện `1.0.0-SNAPSHOT`), pin tập trung trong BOM `javalibs-dependencies`.
- Theo **SemVer**:
  - `PATCH` — sửa lỗi, không đổi API/hành vi mặc định;
  - `MINOR` — thêm module/tính năng/property mới (backward compatible);
  - `MAJOR` — đổi/bỏ API, đổi giá trị mặc định của property, nâng baseline (Java/Spring Boot major).
- **Quy tắc vàng:** không bao giờ đổi hành vi mặc định trong MINOR — service upgrade BOM phải chạy y hệt.
- Release: đổi version ở `javalibs-root/pom.xml` (tất cả parent/child kế thừa), đổi version trong `javalibs-dependencies/pom.xml` (BOM không có parent — **dễ quên**), tag `v<version>`, để CI deploy.

## Publish lên private repository

`distributionManagement` đã khai báo trong root POM **và** BOM với id `javalibs-releases` / `javalibs-snapshots`; URL là property nên CI ghi đè không cần sửa POM:

```bash
mvn -B -DskipTests deploy -Prelease \
  -Djavalibs.distribution.releases.url=https://nexus.noi-bo/repository/maven-releases \
  -Djavalibs.distribution.snapshots.url=https://nexus.noi-bo/repository/maven-snapshots
```

- Profile `-Prelease` đính kèm `-sources.jar` và `-javadoc.jar`.
- Credentials đặt trong `~/.m2/settings.xml`:

```xml
<servers>
  <server>
    <id>javalibs-releases</id>
    <username>${env.MAVEN_REPO_USERNAME}</username>
    <password>${env.MAVEN_REPO_PASSWORD}</password>
  </server>
  <server>
    <id>javalibs-snapshots</id>
    <username>${env.MAVEN_REPO_USERNAME}</username>
    <password>${env.MAVEN_REPO_PASSWORD}</password>
  </server>
</servers>
```

- Với **GitHub Packages**: cả hai URL trỏ về `https://maven.pkg.github.com/<owner>/<repo>`, credential là GITHUB_TOKEN.

## CI/CD

Workflow mẫu tại `.github/workflows/ci.yml`:

| Trigger | Hành động |
|---|---|
| PR vào `main` | `mvn clean verify` (build + test) |
| Push `main` | build + deploy SNAPSHOT |
| Tag `v*` | build + deploy release (kèm sources/javadoc) |

Secrets cần khai báo: `MAVEN_REPO_RELEASES_URL`, `MAVEN_REPO_SNAPSHOTS_URL`, `MAVEN_REPO_USERNAME`, `MAVEN_REPO_PASSWORD`.

## Nâng cấp Spring Boot / thư viện thứ ba

Version chốt tại một chỗ — `javalibs-root/pom.xml`:

```xml
<spring-boot.version>3.5.3</spring-boot.version>
<jjwt.version>0.12.6</jjwt.version>
<resilience4j.version>2.3.0</resilience4j.version>
<springdoc.version>2.8.9</springdoc.version>
```

Quy trình: đổi property → `mvn clean install` → đọc kỹ deprecation/behavior change trong release notes (đặc biệt Spring Security DSL và springdoc theo minor của Boot) → bump MINOR version của javalibs → thông báo các team.

## Troubleshooting

| Triệu chứng | Nguyên nhân thường gặp |
|---|---|
| Bean của javalibs không được tạo | Cờ `enabled` đang tắt; thiếu bean điều kiện (`DataSource`, `KafkaTemplate`, `RedisConnectionFactory`, `CacheAspectSupport`/`@EnableCaching`); hoặc service đã tự định nghĩa bean cùng type (javalibs chủ động lùi). Chạy với `--debug` để xem Condition Evaluation Report. |
| Startup fail "javalibs.security.jwt.secret or ... must be configured" | Mode `jwt` cần secret hoặc public-key; hoặc tắt module / chuyển mode. Đây là fail-fast chủ đích. |
| `IllegalStateException` khi `outbox.enqueue` | Gọi ngoài transaction — thêm `@Transactional` vào method service (chủ đích của pattern). |
| Outbox row kẹt `FAILED` | Đã quá `max-attempts`. Xem `last_error`, sửa nguyên nhân, `UPDATE ... SET status='PENDING', attempts=0` để relay lại. |
| Cache Redis ném lỗi deserialize sau khi refactor DTO | JSON chứa `@class` cũ — flush cache liên quan (đây là trade-off có tài liệu của serializer). |
| Test client fail vì thiếu Docker | `javalibs-test` dựng PostgreSQL qua Testcontainers — cần Docker daemon; hoặc đừng extend `BaseIntegrationTest` cho unit test. |
| 401 dù token hợp lệ | Kiểm tra secret hai bên khớp, `clock-skew`, issuer/audience; token bị revoke (blacklist); xem log DEBUG của `JwtAuthenticationFilter`. |
