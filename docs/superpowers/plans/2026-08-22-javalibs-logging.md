# javalibs-logging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cho mọi microservice trong hệ sinh thái phát log dưới dạng một bản ghi JSON mỗi sự kiện, đúng schema đã chốt (`timestamp`, `level`, `message`, `log_id`, `service`, `host`, `user_id`, `ip`, `request`, `response`, `tags`, `errors`, `metadata`), với dữ liệu nhạy cảm được che sẵn.

**Architecture:** Family module mới `javalibs-logging` theo chuỗi 4 tầng chuẩn của repo. Tầng `-logback` cắm một `StructuredLogFormatter<ILoggingEvent>` vào Structured Logging SPI của Spring Boot 3.4+, nên **mọi** dòng log của ứng dụng (kể cả log của Spring/Hibernate) đều đúng schema. Tầng `-spring` thêm `HttpAccessLogFilter` đặt `userId`/`clientIp` vào MDC và phát dòng access-log mang `request`/`response`. Masking dựa trên `JsonWriter.ValueProcessor` của Boot — đã kiểm chứng nó tự đi vào map lồng nhau và phần tử list.

**Tech Stack:** Java 21, Spring Boot 3.5.3, Logback 1.5.x, SLF4J 2.x, JUnit 5 + AssertJ, Maven.

**Spec:** `docs/superpowers/specs/2026-08-22-javalibs-logging-design.md`

## Global Constraints

- Java 21. **Không dùng Lombok.** Ưu tiên `record` và bất biến.
- Javadoc viết **tiếng Anh**; README và tài liệu trong `docs/` viết **tiếng Việt**.
- `groupId` `io.javalibs`, version `1.0.0-SNAPSHOT`, package gốc `io.javalibs.logging`.
- Tầng `core` **không được** phụ thuộc Spring. Cần Spring thì code đó thuộc tầng khác.
- Trong `autoconfigure`: thư viện bên thứ ba để `<optional>true</optional>`; mọi bean có `@ConditionalOnMissingBean`; có cờ bật/tắt.
- `starter` **tuyệt đối không chứa mã Java** — chỉ `pom.xml` + `README.md`.
- Mọi artifact mới phải được thêm vào BOM `javalibs-dependencies/pom.xml`.
- **Build cần JDK 21** (mặc định của máy là JDK 25 và Maven sẽ báo `JAVA_HOME ... not defined correctly`). Mỗi lệnh build chạy từ thư mục gốc repo và mở đầu bằng:

  ```bash
  export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home
  ```

- Baseline đã được xác nhận xanh trước khi plan này bắt đầu: `mvn -pl javalibs-observability/javalibs-observability-spring,javalibs-web/javalibs-web-spring -am test` trả về exit code 0. Nếu một task thất bại ở module **khác** module nó đụng tới, hãy nghi ngờ thay đổi của chính task đó chứ không phải baseline.

## Cấu trúc file

```
javalibs-logging/
├── pom.xml                                          (Task 1)
├── README.md                                        (Task 17)
├── javalibs-logging-core/                           logic thuần Java
│   ├── LogFields.java              hằng số tên field + MDC key      (Task 1)
│   ├── SensitiveKeys.java          chính sách "key nào nhạy cảm"    (Task 1)
│   ├── SensitiveDataMasker.java    che body form-urlencoded          (Task 2)
│   ├── ClientIpResolver.java       XFF / X-Real-IP / remoteAddr      (Task 3)
│   ├── LogHost.java                hostname có cache                 (Task 4)
│   ├── HttpRequestLog.java         record + toMap()                  (Task 4)
│   └── HttpResponseLog.java        record + toMap()                  (Task 4)
├── javalibs-logging-logback/
│   ├── JavalibsLogFormatSettings.java   đọc javalibs.logging.* từ Environment  (Task 5)
│   ├── JavalibsJsonLogFormatter.java    envelope (Task 6) + ngữ cảnh (Task 7)
│   │                                    + errors (Task 8) + masking (Task 9)
│   └── JavalibsLoggingEnvironmentPostProcessor.java                   (Task 10)
├── javalibs-logging-spring/
│   ├── LogContext.java             tag/metadata phạm vi request       (Task 11)
│   ├── PrincipalResolver.java      SPI lấy user_id                    (Task 12)
│   ├── AccessLogSettings.java      record cấu hình filter             (Task 12)
│   └── HttpAccessLogFilter.java    MDC + dòng access-log (Task 12), body/header (Task 13)
├── javalibs-logging-spring-boot-autoconfigure/
│   ├── LoggingProperties.java                                        (Task 14)
│   └── AccessLogAutoConfiguration.java                               (Task 14)
└── javalibs-logging-spring-boot-starter/            chỉ pom + README  (Task 16)
```

Ngoài family mới, plan đụng tới: `pom.xml` gốc (Task 1), `javalibs-web-spring-boot-autoconfigure` (Task 15), `javalibs-dependencies/pom.xml` (Task 16), và các file tài liệu (Task 17).

**Lý do chia file như trên:** `JavalibsJsonLogFormatter` được bốn task liên tiếp (6→9) bồi đắp vì mỗi nhóm field có cách lấy dữ liệu và cách test khác hẳn nhau — người review có thể bác `errors[]` mà vẫn duyệt phần envelope. `AccessLogSettings` tách khỏi filter để filter không có constructor tám tham số và để test cấu hình không cần dựng cả filter.

---

### Task 1: Bộ khung module + `LogFields` + `SensitiveKeys`

**Files:**
- Create: `javalibs-logging/pom.xml`
- Create: `javalibs-logging/javalibs-logging-core/pom.xml`
- Modify: `pom.xml` (thêm `<module>javalibs-logging</module>` ngay sau dòng `<module>javalibs-observability</module>`, hiện ở dòng 30)
- Create: `javalibs-logging/javalibs-logging-core/src/main/java/io/javalibs/logging/LogFields.java`
- Create: `javalibs-logging/javalibs-logging-core/src/main/java/io/javalibs/logging/SensitiveKeys.java`
- Test: `javalibs-logging/javalibs-logging-core/src/test/java/io/javalibs/logging/SensitiveKeysTest.java`

**Interfaces:**
- Consumes: không có (task đầu tiên).
- Produces:
  - `LogFields` — hằng số `String`: `TIMESTAMP`, `LEVEL`, `MESSAGE`, `LOG_ID`, `SERVICE`, `HOST`, `USER_ID`, `IP`, `REQUEST`, `RESPONSE`, `TAGS`, `ERRORS`, `METADATA`, `METHOD`, `ENDPOINT`, `HEADERS`, `BODY`, `STATUS`, `LATENCY_MS`, `RESPONSE_BODY`, `ERROR_TYPE`, `ERROR_MESSAGE`, `ERROR_STACKTRACE`, `MDC_USER_ID`, `MDC_CLIENT_IP`, `MDC_TAGS`; và `Set<String> PROMOTED_MDC_KEYS`.
  - `SensitiveKeys` — `static SensitiveKeys defaults()`, `static SensitiveKeys withAdditional(Collection<String> extra)`, `static SensitiveKeys none()`, `boolean isSensitive(String key)`, `static final Set<String> DEFAULT_KEYS`.

- [ ] **Step 1: Tạo pom tổng của family**

Tạo `javalibs-logging/pom.xml`:

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

  <artifactId>javalibs-logging</artifactId>
  <packaging>pom</packaging>

  <name>javalibs :: logging</name>
  <description>
    Structured JSON logging for javalibs: one JSON record per event with a
    fixed schema, sensitive-data masking, an HTTP access log filter and Spring
    Boot auto-configuration.
  </description>

  <modules>
    <module>javalibs-logging-core</module>
  </modules>
</project>
```

Các module còn lại sẽ được thêm vào `<modules>` ở đúng task tạo ra chúng (Task 5, 12, 14, 16).

- [ ] **Step 2: Tạo pom của core**

Tạo `javalibs-logging/javalibs-logging-core/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>io.javalibs</groupId>
    <artifactId>javalibs-logging</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>..</relativePath>
  </parent>

  <artifactId>javalibs-logging-core</artifactId>

  <name>javalibs :: logging :: core</name>
  <description>
    Framework-agnostic logging primitives: log field names, sensitive key
    policy, masking of form-encoded bodies, client IP resolution and the
    HTTP request/response log value objects.
  </description>

  <dependencies>
    <!-- Test -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 3: Đăng ký module vào pom gốc**

Trong `pom.xml` ở gốc repo, thêm một dòng ngay **sau** `<module>javalibs-observability</module>`:

```xml
    <module>javalibs-logging</module>
```

- [ ] **Step 4: Viết test đỏ cho `SensitiveKeys`**

Tạo `javalibs-logging/javalibs-logging-core/src/test/java/io/javalibs/logging/SensitiveKeysTest.java`:

```java
package io.javalibs.logging;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveKeysTest {

    @Test
    void matchesDefaultKeysRegardlessOfCaseAndSeparators() {
        SensitiveKeys keys = SensitiveKeys.defaults();

        assertThat(keys.isSensitive("password")).isTrue();
        assertThat(keys.isSensitive("PASSWORD")).isTrue();
        assertThat(keys.isSensitive("Pass_Word")).isTrue();
        assertThat(keys.isSensitive("pass-word")).isTrue();
        assertThat(keys.isSensitive("access_token")).isTrue();
        assertThat(keys.isSensitive("accessToken")).isTrue();
        assertThat(keys.isSensitive("API KEY")).isTrue();
    }

    @Test
    void leavesOrdinaryKeysAlone() {
        SensitiveKeys keys = SensitiveKeys.defaults();

        assertThat(keys.isSensitive("email")).isFalse();
        assertThat(keys.isSensitive("amount")).isFalse();
        assertThat(keys.isSensitive("passenger")).isFalse();
        assertThat(keys.isSensitive("")).isFalse();
        assertThat(keys.isSensitive(null)).isFalse();
    }

    @Test
    void additionalKeysAddToDefaultsInsteadOfReplacingThem() {
        SensitiveKeys keys = SensitiveKeys.withAdditional(List.of("national-id"));

        assertThat(keys.isSensitive("nationalId")).isTrue();
        assertThat(keys.isSensitive("password")).isTrue();
    }

    @Test
    void noneMatchesNothingSoMaskingCanBeTurnedOff() {
        SensitiveKeys keys = SensitiveKeys.none();

        assertThat(keys.isSensitive("password")).isFalse();
        assertThat(keys.isSensitive("token")).isFalse();
    }
}
```

- [ ] **Step 5: Chạy test để chắc chắn nó đỏ**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn -pl javalibs-logging/javalibs-logging-core -am test
```

Kỳ vọng: **FAIL** khi biên dịch test, với `cannot find symbol: class SensitiveKeys`.

- [ ] **Step 6: Viết `LogFields`**

Tạo `javalibs-logging/javalibs-logging-core/src/main/java/io/javalibs/logging/LogFields.java`:

```java
package io.javalibs.logging;

import java.util.Set;

/**
 * Field names of the javalibs structured log record, plus the MDC keys javalibs
 * itself populates.
 *
 * <p>The names are part of the log contract consumed by log aggregation
 * pipelines: changing one is a breaking change for every dashboard and alert
 * built on it.</p>
 */
public final class LogFields {

    /** Event time, ISO-8601 in UTC with millisecond precision. */
    public static final String TIMESTAMP = "timestamp";
    /** Severity of the log event. */
    public static final String LEVEL = "level";
    /** Human readable description of the event. */
    public static final String MESSAGE = "message";
    /** Unique identifier of this single log entry. */
    public static final String LOG_ID = "log_id";
    /** Name of the service or application that emitted the event. */
    public static final String SERVICE = "service";
    /** Machine the event happened on. */
    public static final String HOST = "host";
    /** Identifier of the user the event relates to. */
    public static final String USER_ID = "user_id";
    /** IP address of the requester. */
    public static final String IP = "ip";
    /** Details of the incoming HTTP request. */
    public static final String REQUEST = "request";
    /** Details of the outgoing HTTP response. */
    public static final String RESPONSE = "response";
    /** Keywords used to filter and search logs. */
    public static final String TAGS = "tags";
    /** Errors attached to the event; an empty array when there are none. */
    public static final String ERRORS = "errors";
    /** Additional information about the environment or system. */
    public static final String METADATA = "metadata";

    /** {@code request.method} — HTTP method. */
    public static final String METHOD = "method";
    /** {@code request.endpoint} — request path including query string. */
    public static final String ENDPOINT = "endpoint";
    /** {@code request.headers} — selected request headers. */
    public static final String HEADERS = "headers";
    /** {@code request.body} — request payload. */
    public static final String BODY = "body";
    /** {@code response.status} — HTTP status code. */
    public static final String STATUS = "status";
    /** {@code response.latency_ms} — time taken to serve the request. */
    public static final String LATENCY_MS = "latency_ms";
    /** {@code response.response_body} — response payload. */
    public static final String RESPONSE_BODY = "response_body";

    /** {@code errors[].type} — fully qualified exception class name. */
    public static final String ERROR_TYPE = "type";
    /** {@code errors[].message} — exception message. */
    public static final String ERROR_MESSAGE = "message";
    /** {@code errors[].stacktrace} — rendered stack trace. */
    public static final String ERROR_STACKTRACE = "stacktrace";

    /** MDC key holding the authenticated user id. */
    public static final String MDC_USER_ID = "userId";
    /** MDC key holding the resolved client IP address. */
    public static final String MDC_CLIENT_IP = "clientIp";
    /** MDC key holding comma separated request-scoped tags. */
    public static final String MDC_TAGS = "logTags";

    /**
     * MDC keys that are already rendered as dedicated top-level fields and must
     * therefore not be repeated inside {@link #METADATA}.
     */
    public static final Set<String> PROMOTED_MDC_KEYS = Set.of(MDC_USER_ID, MDC_CLIENT_IP, MDC_TAGS);

    private LogFields() {
        // constants holder
    }
}
```

- [ ] **Step 7: Viết `SensitiveKeys`**

Tạo `javalibs-logging/javalibs-logging-core/src/main/java/io/javalibs/logging/SensitiveKeys.java`:

```java
package io.javalibs.logging;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Decides whether a field name carries a secret and therefore must be masked.
 *
 * <p>Matching is done on a normalized form of the name — lower-cased with
 * {@code _}, {@code -} and spaces removed — so {@code password}, {@code PASSWORD},
 * {@code Pass_Word} and {@code pass-word} are all recognised by the single
 * default entry {@code password}.</p>
 *
 * <p>Application supplied keys are <em>added to</em> the defaults rather than
 * replacing them, so a service that needs one extra key cannot accidentally
 * unmask everything else.</p>
 */
public final class SensitiveKeys {

    /** Field names masked out of the box. */
    public static final Set<String> DEFAULT_KEYS = Set.of(
            "password", "passwd", "token", "access_token", "refresh_token", "id_token",
            "secret", "client_secret", "authorization", "api_key", "apikey",
            "private_key", "otp", "pin", "card_number", "cvv", "ssn");

    private static final SensitiveKeys DEFAULTS = new SensitiveKeys(Set.of(), true);
    private static final SensitiveKeys NONE = new SensitiveKeys(Set.of(), false);

    private final Set<String> normalized;

    private SensitiveKeys(Collection<String> additional, boolean includeDefaults) {
        Set<String> all = new LinkedHashSet<>();
        if (includeDefaults) {
            for (String key : DEFAULT_KEYS) {
                all.add(normalize(key));
            }
        }
        if (additional != null) {
            for (String key : additional) {
                if (key != null && !key.isBlank()) {
                    all.add(normalize(key));
                }
            }
        }
        this.normalized = Set.copyOf(all);
    }

    /**
     * Returns the shared instance matching only {@link #DEFAULT_KEYS}.
     *
     * @return the default policy
     */
    public static SensitiveKeys defaults() {
        return DEFAULTS;
    }

    /**
     * Returns a policy matching the defaults plus the given extra names.
     *
     * @param additional extra field names to treat as sensitive, may be
     *                   {@code null} or empty
     * @return the combined policy
     */
    public static SensitiveKeys withAdditional(Collection<String> additional) {
        return (additional == null || additional.isEmpty()) ? DEFAULTS : new SensitiveKeys(additional, true);
    }

    /**
     * Returns a policy matching nothing, used when masking is switched off.
     *
     * @return a policy for which {@link #isSensitive(String)} is always {@code false}
     */
    public static SensitiveKeys none() {
        return NONE;
    }

    /**
     * Checks whether a field name is considered sensitive.
     *
     * @param key the field name, may be {@code null}
     * @return {@code true} when the value behind this name must be masked
     */
    public boolean isSensitive(String key) {
        return key != null && !key.isEmpty() && normalized.contains(normalize(key));
    }

    private static String normalize(String key) {
        StringBuilder normalized = new StringBuilder(key.length());
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '_' || c == '-' || c == ' ') {
                continue;
            }
            normalized.append(Character.toLowerCase(c));
        }
        return normalized.toString();
    }
}
```

- [ ] **Step 8: Chạy test để chắc chắn nó xanh**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn -pl javalibs-logging/javalibs-logging-core -am test
```

Kỳ vọng: **PASS**, `Tests run: 4, Failures: 0, Errors: 0`.

- [ ] **Step 9: Commit**

```bash
git add pom.xml javalibs-logging
git commit -m "feat(logging): bộ khung module javalibs-logging + LogFields + SensitiveKeys"
```

---

### Task 2: `SensitiveDataMasker` — che body form-urlencoded

**Files:**
- Create: `javalibs-logging/javalibs-logging-core/src/main/java/io/javalibs/logging/SensitiveDataMasker.java`
- Test: `javalibs-logging/javalibs-logging-core/src/test/java/io/javalibs/logging/SensitiveDataMaskerTest.java`

**Interfaces:**
- Consumes: `SensitiveKeys.isSensitive(String)` từ Task 1.
- Produces: `SensitiveDataMasker` — constructor `SensitiveDataMasker(SensitiveKeys keys, String mask)`, hằng số `String DEFAULT_MASK = "********"`, phương thức `String maskFormEncoded(String body)`.

**Bối cảnh — vì sao chỉ có form-urlencoded:** phần che JSON lồng nhau do
`JsonWriter.ValueProcessor` của Spring Boot đảm nhiệm ở Task 9 (đã kiểm chứng nó
tự đi vào map lồng nhau và phần tử list). Trường hợp duy nhất nó không nhìn
xuyên qua được là body **không phải JSON**, vì khi đó cả body chỉ là một chuỗi
nằm ở path `request.body`. Đó là khoảng trống mà class này lấp.

- [ ] **Step 1: Viết test đỏ**

Tạo `javalibs-logging/javalibs-logging-core/src/test/java/io/javalibs/logging/SensitiveDataMaskerTest.java`:

```java
package io.javalibs.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataMaskerTest {

    private final SensitiveDataMasker masker =
            new SensitiveDataMasker(SensitiveKeys.defaults(), SensitiveDataMasker.DEFAULT_MASK);

    @Test
    void masksSensitiveFormFieldsAndKeepsTheRest() {
        assertThat(masker.maskFormEncoded("username=alice&password=hunter2&remember=true"))
                .isEqualTo("username=alice&password=********&remember=true");
    }

    @Test
    void masksTheLastFieldWhenThereIsNoTrailingAmpersand() {
        assertThat(masker.maskFormEncoded("user=a&token=abc"))
                .isEqualTo("user=a&token=********");
    }

    @Test
    void keepsSegmentsThatHaveNoEqualsSign() {
        assertThat(masker.maskFormEncoded("flag&password=x"))
                .isEqualTo("flag&password=********");
    }

    @Test
    void decodesPercentEncodedFieldNamesBeforeMatching() {
        assertThat(masker.maskFormEncoded("api%5Fkey=zzz"))
                .isEqualTo("api%5Fkey=********");
    }

    @Test
    void masksAnEmptyValueSoTheAbsenceOfASecretIsNotLeaked() {
        assertThat(masker.maskFormEncoded("password=&user=a"))
                .isEqualTo("password=********&user=a");
    }

    @Test
    void returnsNullAndEmptyInputUnchanged() {
        assertThat(masker.maskFormEncoded(null)).isNull();
        assertThat(masker.maskFormEncoded("")).isEmpty();
    }

    @Test
    void masksNothingWhenTheKeyPolicyIsNone() {
        SensitiveDataMasker off = new SensitiveDataMasker(SensitiveKeys.none(), null);

        assertThat(off.maskFormEncoded("password=hunter2")).isEqualTo("password=hunter2");
    }
}
```

- [ ] **Step 2: Chạy test để chắc chắn nó đỏ**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn -pl javalibs-logging/javalibs-logging-core test
```

Kỳ vọng: **FAIL** khi biên dịch test, `cannot find symbol: class SensitiveDataMasker`.

- [ ] **Step 3: Viết implementation**

Tạo `javalibs-logging/javalibs-logging-core/src/main/java/io/javalibs/logging/SensitiveDataMasker.java`:

```java
package io.javalibs.logging;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Masks secrets inside {@code application/x-www-form-urlencoded} payloads and
 * query strings.
 *
 * <p>Structured JSON payloads are masked by the logging formatter itself, which
 * walks the object graph and consults {@link SensitiveKeys} for every value it
 * writes. A form-encoded body has no such structure once it reaches the log
 * record — it is a single string — so it is handled here instead.</p>
 */
public final class SensitiveDataMasker {

    /** Replacement written in place of a secret. */
    public static final String DEFAULT_MASK = "********";

    private final SensitiveKeys keys;
    private final String mask;

    /**
     * Creates a masker.
     *
     * @param keys the sensitive key policy; {@code null} falls back to
     *             {@link SensitiveKeys#defaults()}
     * @param mask the replacement text; {@code null} or empty falls back to
     *             {@link #DEFAULT_MASK}
     */
    public SensitiveDataMasker(SensitiveKeys keys, String mask) {
        this.keys = (keys != null) ? keys : SensitiveKeys.defaults();
        this.mask = (mask != null && !mask.isEmpty()) ? mask : DEFAULT_MASK;
    }

    /**
     * Replaces the value of every sensitive field in a form-encoded string.
     *
     * <p>Field names are percent-decoded before being matched, so
     * {@code api%5Fkey} is recognised as {@code api_key}. The original encoding
     * of the name is preserved in the output.</p>
     *
     * @param body the form-encoded body or query string, may be {@code null}
     * @return the body with sensitive values replaced; {@code null} in,
     *         {@code null} out
     */
    public String maskFormEncoded(String body) {
        if (body == null || body.isEmpty()) {
            return body;
        }
        StringBuilder masked = new StringBuilder(body.length());
        int start = 0;
        while (true) {
            int ampersand = body.indexOf('&', start);
            int end = (ampersand >= 0) ? ampersand : body.length();
            appendPair(masked, body, start, end);
            if (ampersand < 0) {
                return masked.toString();
            }
            masked.append('&');
            start = ampersand + 1;
        }
    }

    private void appendPair(StringBuilder masked, String body, int start, int end) {
        int equals = body.indexOf('=', start);
        if (equals < 0 || equals >= end) {
            masked.append(body, start, end);
            return;
        }
        String name = body.substring(start, equals);
        masked.append(name).append('=');
        if (keys.isSensitive(decode(name))) {
            masked.append(mask);
        } else {
            masked.append(body, equals + 1, end);
        }
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return value;
        }
    }
}
```

- [ ] **Step 4: Chạy test để chắc chắn nó xanh**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn -pl javalibs-logging/javalibs-logging-core test
```

Kỳ vọng: **PASS**, `Tests run: 11, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

```bash
git add javalibs-logging/javalibs-logging-core
git commit -m "feat(logging): SensitiveDataMasker che body form-urlencoded"
```

---

### Task 3: `ClientIpResolver`

**Files:**
- Create: `javalibs-logging/javalibs-logging-core/src/main/java/io/javalibs/logging/ClientIpResolver.java`
- Test: `javalibs-logging/javalibs-logging-core/src/test/java/io/javalibs/logging/ClientIpResolverTest.java`

**Interfaces:**
- Consumes: không có.
- Produces: `ClientIpResolver` — constructor `ClientIpResolver(boolean trustProxy)`, phương thức `String resolve(Function<String, String> headerLookup, String remoteAddress)` (trả `null` khi không xác định được), hằng số `HEADER_FORWARDED_FOR`, `HEADER_REAL_IP`.

**Bối cảnh:** core không được phụ thuộc `jakarta.servlet`, nên resolver nhận một
`Function<String, String>` tra header thay vì nhận `HttpServletRequest`. Tầng
`-spring` sẽ truyền `request::getHeader`.

- [ ] **Step 1: Viết test đỏ**

Tạo `javalibs-logging/javalibs-logging-core/src/test/java/io/javalibs/logging/ClientIpResolverTest.java`:

```java
package io.javalibs.logging;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    void ignoresForwardedHeadersWhenTheProxyIsNotTrusted() {
        ClientIpResolver resolver = new ClientIpResolver(false);
        Map<String, String> headers = Map.of(ClientIpResolver.HEADER_FORWARDED_FOR, "1.2.3.4");

        assertThat(resolver.resolve(headers::get, "10.0.0.1")).isEqualTo("10.0.0.1");
    }

    @Test
    void usesTheFirstForwardedForEntryWhenTheProxyIsTrusted() {
        ClientIpResolver resolver = new ClientIpResolver(true);
        Map<String, String> headers =
                Map.of(ClientIpResolver.HEADER_FORWARDED_FOR, "203.0.113.7, 10.0.0.5, 10.0.0.6");

        assertThat(resolver.resolve(headers::get, "10.0.0.1")).isEqualTo("203.0.113.7");
    }

    @Test
    void fallsBackToRemoteAddressWhenTheForwardedValueIsNotAnAddress() {
        ClientIpResolver resolver = new ClientIpResolver(true);
        Map<String, String> headers =
                Map.of(ClientIpResolver.HEADER_FORWARDED_FOR, "not an ip\r\nInjected: header");

        assertThat(resolver.resolve(headers::get, "10.0.0.1")).isEqualTo("10.0.0.1");
    }

    @Test
    void fallsBackToRealIpHeaderWhenForwardedForIsAbsent() {
        ClientIpResolver resolver = new ClientIpResolver(true);
        Map<String, String> headers = Map.of(ClientIpResolver.HEADER_REAL_IP, "198.51.100.9");

        assertThat(resolver.resolve(headers::get, "10.0.0.1")).isEqualTo("198.51.100.9");
    }

    @Test
    void acceptsIpv6Addresses() {
        ClientIpResolver resolver = new ClientIpResolver(true);
        Map<String, String> headers = Map.of(ClientIpResolver.HEADER_FORWARDED_FOR, "2001:db8::1");

        assertThat(resolver.resolve(headers::get, "10.0.0.1")).isEqualTo("2001:db8::1");
    }

    @Test
    void returnsNullWhenNothingIsAvailable() {
        assertThat(new ClientIpResolver(false).resolve(name -> null, null)).isNull();
    }
}
```

- [ ] **Step 2: Chạy test để chắc chắn nó đỏ**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn -pl javalibs-logging/javalibs-logging-core test
```

Kỳ vọng: **FAIL**, `cannot find symbol: class ClientIpResolver`.

- [ ] **Step 3: Viết implementation**

Tạo `javalibs-logging/javalibs-logging-core/src/main/java/io/javalibs/logging/ClientIpResolver.java`:

```java
package io.javalibs.logging;

import java.util.function.Function;

/**
 * Resolves the IP address to record as the {@code ip} field of a log event.
 *
 * <p>Proxy headers are only consulted when {@code trustProxy} is enabled. That
 * default matters: {@code X-Forwarded-For} is attacker-controlled on a directly
 * exposed application, so trusting it unconditionally would let any caller
 * choose the address written into the audit trail. Turn it on only when the
 * application really does sit behind a reverse proxy that overwrites the
 * header.</p>
 *
 * <p>Values are validated to contain nothing but hex digits, dots, colons and
 * {@code %} (the IPv6 zone separator). Anything else — including the CR/LF that
 * would allow log injection — is rejected in favour of the transport level
 * address.</p>
 */
public final class ClientIpResolver {

    /** Header carrying the proxy chain, client first. */
    public static final String HEADER_FORWARDED_FOR = "X-Forwarded-For";

    /** Header carrying a single client address, set by some proxies. */
    public static final String HEADER_REAL_IP = "X-Real-IP";

    /** Longest possible textual IPv6 address including a zone index. */
    private static final int MAX_LENGTH = 45;

    private final boolean trustProxy;

    /**
     * Creates a resolver.
     *
     * @param trustProxy whether {@code X-Forwarded-For} / {@code X-Real-IP} may
     *                   be believed
     */
    public ClientIpResolver(boolean trustProxy) {
        this.trustProxy = trustProxy;
    }

    /**
     * Resolves the client address.
     *
     * @param headerLookup function returning a request header by name, may be
     *                     {@code null}
     * @param remoteAddress the transport level peer address, may be {@code null}
     * @return the resolved address, or {@code null} when none is usable
     */
    public String resolve(Function<String, String> headerLookup, String remoteAddress) {
        if (trustProxy && headerLookup != null) {
            String forwarded = firstEntry(headerLookup.apply(HEADER_FORWARDED_FOR));
            if (forwarded != null) {
                return forwarded;
            }
            String realIp = sanitize(headerLookup.apply(HEADER_REAL_IP));
            if (realIp != null) {
                return realIp;
            }
        }
        return sanitize(remoteAddress);
    }

    private static String firstEntry(String headerValue) {
        if (headerValue == null) {
            return null;
        }
        int comma = headerValue.indexOf(',');
        return sanitize((comma >= 0) ? headerValue.substring(0, comma) : headerValue);
    }

    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH) {
            return null;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean allowed = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F')
                    || c == '.' || c == ':' || c == '%';
            if (!allowed) {
                return null;
            }
        }
        return trimmed;
    }
}
```

- [ ] **Step 4: Chạy test để chắc chắn nó xanh**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn -pl javalibs-logging/javalibs-logging-core test
```

Kỳ vọng: **PASS**, `Tests run: 17, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

```bash
git add javalibs-logging/javalibs-logging-core
git commit -m "feat(logging): ClientIpResolver — chỉ tin X-Forwarded-For khi trust-proxy"
```

---

### Task 4: `LogHost` + record `HttpRequestLog` / `HttpResponseLog`

**Files:**
- Create: `javalibs-logging/javalibs-logging-core/src/main/java/io/javalibs/logging/LogHost.java`
- Create: `javalibs-logging/javalibs-logging-core/src/main/java/io/javalibs/logging/HttpRequestLog.java`
- Create: `javalibs-logging/javalibs-logging-core/src/main/java/io/javalibs/logging/HttpResponseLog.java`
- Test: `javalibs-logging/javalibs-logging-core/src/test/java/io/javalibs/logging/HttpExchangeLogTest.java`
- Test: `javalibs-logging/javalibs-logging-core/src/test/java/io/javalibs/logging/LogHostTest.java`

**Interfaces:**
- Consumes: `LogFields` từ Task 1.
- Produces:
  - `LogHost` — `static String current()`, hằng số `String UNKNOWN = "unknown"`.
  - `HttpRequestLog(String method, String endpoint, Map<String, String> headers, Object body)` với `Map<String, Object> toMap()`.
  - `HttpResponseLog(int status, long latencyMs, Object responseBody)` với `Map<String, Object> toMap()`.

- [ ] **Step 1: Viết test đỏ**

Tạo `javalibs-logging/javalibs-logging-core/src/test/java/io/javalibs/logging/HttpExchangeLogTest.java`:

```java
package io.javalibs.logging;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpExchangeLogTest {

    @Test
    void requestMapKeepsSchemaFieldOrder() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", "user@example.com");
        HttpRequestLog request = new HttpRequestLog(
                "POST", "/api/login", Map.of("Content-Type", "application/json"), body);

        assertThat(request.toMap()).containsExactly(
                Map.entry(LogFields.METHOD, "POST"),
                Map.entry(LogFields.ENDPOINT, "/api/login"),
                Map.entry(LogFields.HEADERS, Map.of("Content-Type", "application/json")),
                Map.entry(LogFields.BODY, body));
    }

    @Test
    void requestMapOmitsEmptyHeadersAndAbsentBody() {
        HttpRequestLog request = new HttpRequestLog("GET", "/health", Map.of(), null);

        assertThat(request.toMap()).containsOnlyKeys(LogFields.METHOD, LogFields.ENDPOINT);
    }

    @Test
    void requestTreatsNullHeadersAsEmpty() {
        HttpRequestLog request = new HttpRequestLog("GET", "/health", null, null);

        assertThat(request.headers()).isEmpty();
    }

    @Test
    void responseMapKeepsSchemaFieldOrder() {
        Map<String, Object> body = Map.of("success", true);
        HttpResponseLog response = new HttpResponseLog(200, 152L, body);

        assertThat(response.toMap()).containsExactly(
                Map.entry(LogFields.STATUS, 200),
                Map.entry(LogFields.LATENCY_MS, 152L),
                Map.entry(LogFields.RESPONSE_BODY, body));
    }

    @Test
    void responseMapOmitsAbsentBody() {
        HttpResponseLog response = new HttpResponseLog(204, 3L, null);

        assertThat(response.toMap()).containsOnlyKeys(LogFields.STATUS, LogFields.LATENCY_MS);
    }
}
```

Tạo `javalibs-logging/javalibs-logging-core/src/test/java/io/javalibs/logging/LogHostTest.java`:

```java
package io.javalibs.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogHostTest {

    @Test
    void resolvesANonBlankHostNameAndCachesIt() {
        String first = LogHost.current();

        assertThat(first).isNotBlank();
        assertThat(LogHost.current()).isSameAs(first);
    }
}
```

- [ ] **Step 2: Chạy test để chắc chắn nó đỏ**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn -pl javalibs-logging/javalibs-logging-core test
```

Kỳ vọng: **FAIL**, `cannot find symbol: class HttpRequestLog`.

- [ ] **Step 3: Viết `LogHost`**

Tạo `javalibs-logging/javalibs-logging-core/src/main/java/io/javalibs/logging/LogHost.java`:

```java
package io.javalibs.logging;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Resolves the machine name written into the {@code host} field.
 *
 * <p>The {@code HOSTNAME} environment variable is preferred because container
 * runtimes set it to the container or pod name — the identity operators
 * actually search by — and because reading it avoids a reverse DNS lookup on
 * the request path. The value is resolved once and cached; a failure degrades
 * to {@value #UNKNOWN} rather than propagating, since no logging concern is
 * worth preventing an application from starting.</p>
 */
public final class LogHost {

    /** Value used when the host name cannot be determined. */
    public static final String UNKNOWN = "unknown";

    private static final String RESOLVED = resolve();

    private LogHost() {
        // static utility
    }

    /**
     * Returns the cached host name.
     *
     * @return the host name, never {@code null} or blank
     */
    public static String current() {
        return RESOLVED;
    }

    private static String resolve() {
        String fromEnvironment = System.getenv("HOSTNAME");
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return fromEnvironment.trim();
        }
        try {
            String hostName = InetAddress.getLocalHost().getHostName();
            return (hostName != null && !hostName.isBlank()) ? hostName : UNKNOWN;
        } catch (UnknownHostException | RuntimeException ex) {
            return UNKNOWN;
        }
    }
}
```

- [ ] **Step 4: Viết hai record**

Tạo `javalibs-logging/javalibs-logging-core/src/main/java/io/javalibs/logging/HttpRequestLog.java`:

```java
package io.javalibs.logging;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code request} subtree of a log record.
 *
 * @param method   HTTP method
 * @param endpoint request path, including the query string when present
 * @param headers  selected request headers, never {@code null} after construction
 * @param body     request payload — a parsed {@code Map} for JSON, a
 *                 {@code String} otherwise, or {@code null} when not captured
 */
public record HttpRequestLog(String method, String endpoint, Map<String, String> headers, Object body) {

    /** Canonical constructor normalizing {@code null} headers to an empty map. */
    public HttpRequestLog {
        headers = (headers == null) ? Map.of() : Map.copyOf(headers);
    }

    /**
     * Renders this request as a map in schema field order, omitting fields that
     * carry no value.
     *
     * @return an ordered map ready to be written as JSON
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>(4);
        map.put(LogFields.METHOD, method);
        map.put(LogFields.ENDPOINT, endpoint);
        if (!headers.isEmpty()) {
            map.put(LogFields.HEADERS, headers);
        }
        if (body != null) {
            map.put(LogFields.BODY, body);
        }
        return map;
    }
}
```

Tạo `javalibs-logging/javalibs-logging-core/src/main/java/io/javalibs/logging/HttpResponseLog.java`:

```java
package io.javalibs.logging;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code response} subtree of a log record.
 *
 * @param status       HTTP status code
 * @param latencyMs    time taken to serve the request, in milliseconds
 * @param responseBody response payload — a parsed {@code Map} for JSON, a
 *                     {@code String} otherwise, or {@code null} when not captured
 */
public record HttpResponseLog(int status, long latencyMs, Object responseBody) {

    /**
     * Renders this response as a map in schema field order, omitting the body
     * when it was not captured.
     *
     * @return an ordered map ready to be written as JSON
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>(3);
        map.put(LogFields.STATUS, status);
        map.put(LogFields.LATENCY_MS, latencyMs);
        if (responseBody != null) {
            map.put(LogFields.RESPONSE_BODY, responseBody);
        }
        return map;
    }
}
```

- [ ] **Step 5: Chạy test để chắc chắn nó xanh**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn -pl javalibs-logging/javalibs-logging-core test
```

Kỳ vọng: **PASS**, `Tests run: 23, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit**

```bash
git add javalibs-logging/javalibs-logging-core
git commit -m "feat(logging): LogHost + record HttpRequestLog/HttpResponseLog"
```

---

### Task 5: Module `-logback` + `JavalibsLogFormatSettings`

**Files:**
- Create: `javalibs-logging/javalibs-logging-logback/pom.xml`
- Modify: `javalibs-logging/pom.xml` (thêm `<module>javalibs-logging-logback</module>`)
- Create: `javalibs-logging/javalibs-logging-logback/src/main/java/io/javalibs/logging/logback/JavalibsLogFormatSettings.java`
- Test: `javalibs-logging/javalibs-logging-logback/src/test/java/io/javalibs/logging/logback/JavalibsLogFormatSettingsTest.java`

**Interfaces:**
- Consumes: `LogHost.current()`, `SensitiveKeys.withAdditional(...)`, `SensitiveKeys.none()`, `SensitiveDataMasker.DEFAULT_MASK` từ Task 1–4.
- Produces: `JavalibsLogFormatSettings` — `static JavalibsLogFormatSettings from(Environment environment)` và các accessor `service()`, `host()`, `tags()`, `metadata()`, `sensitiveKeys()`, `mask()`, `maskingEnabled()`, `stacktraceEnabled()`, `stacktraceMaxLength()`.

**Bối cảnh — vì sao đọc `Environment` chứ không `@ConfigurationProperties`:**
formatter được `Instantiator` của Boot dựng lúc khởi tạo logging system, **trước
khi có ApplicationContext**. Nó không lấy được bean; tham số constructor duy nhất
dùng được là `Environment`. Đọc và cache một lần tại đây để không phải tra
property trên từng dòng log.

- [ ] **Step 1: Tạo pom và đăng ký module**

Tạo `javalibs-logging/javalibs-logging-logback/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>io.javalibs</groupId>
    <artifactId>javalibs-logging</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>..</relativePath>
  </parent>

  <artifactId>javalibs-logging-logback</artifactId>

  <name>javalibs :: logging :: logback</name>
  <description>
    Logback binding for javalibs structured logging: a StructuredLogFormatter
    rendering every log event as one JSON record following the javalibs schema.
  </description>

  <dependencies>
    <dependency>
      <groupId>io.javalibs</groupId>
      <artifactId>javalibs-logging-core</artifactId>
      <version>${project.version}</version>
    </dependency>

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-core</artifactId>
    </dependency>
    <dependency>
      <groupId>ch.qos.logback</groupId>
      <artifactId>logback-classic</artifactId>
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

Trong `javalibs-logging/pom.xml`, đổi khối `<modules>` thành:

```xml
  <modules>
    <module>javalibs-logging-core</module>
    <module>javalibs-logging-logback</module>
  </modules>
```

- [ ] **Step 2: Viết test đỏ**

Tạo `javalibs-logging/javalibs-logging-logback/src/test/java/io/javalibs/logging/logback/JavalibsLogFormatSettingsTest.java`:

```java
package io.javalibs.logging.logback;

import java.util.Map;

import io.javalibs.logging.LogHost;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class JavalibsLogFormatSettingsTest {

    private static StandardEnvironment environmentWith(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        return environment;
    }

    @Test
    void fallsBackToApplicationNameAndMachineHost() {
        JavalibsLogFormatSettings settings = JavalibsLogFormatSettings.from(
                environmentWith(Map.of("spring.application.name", "auth-service")));

        assertThat(settings.service()).isEqualTo("auth-service");
        assertThat(settings.host()).isEqualTo(LogHost.current());
    }

    @Test
    void explicitServiceAndHostWinOverFallbacks() {
        JavalibsLogFormatSettings settings = JavalibsLogFormatSettings.from(environmentWith(Map.of(
                "spring.application.name", "auth-service",
                "javalibs.logging.service", "auth",
                "javalibs.logging.host", "server-01")));

        assertThat(settings.service()).isEqualTo("auth");
        assertThat(settings.host()).isEqualTo("server-01");
    }

    @Test
    void environmentAndVersionBecomeMetadataEntries() {
        JavalibsLogFormatSettings settings = JavalibsLogFormatSettings.from(environmentWith(Map.of(
                "javalibs.logging.environment", "production",
                "javalibs.logging.version", "1.2.3",
                "javalibs.logging.metadata.region", "ap-southeast-1")));

        assertThat(settings.metadata())
                .containsEntry("env", "production")
                .containsEntry("version", "1.2.3")
                .containsEntry("region", "ap-southeast-1");
    }

    @Test
    void bindsStaticTagsAsAList() {
        JavalibsLogFormatSettings settings = JavalibsLogFormatSettings.from(
                environmentWith(Map.of("javalibs.logging.tags", "auth,login")));

        assertThat(settings.tags()).containsExactly("auth", "login");
    }

    @Test
    void appliesMaskingDefaultsAndExtraKeys() {
        JavalibsLogFormatSettings settings = JavalibsLogFormatSettings.from(
                environmentWith(Map.of("javalibs.logging.masking.keys", "national-id")));

        assertThat(settings.maskingEnabled()).isTrue();
        assertThat(settings.mask()).isEqualTo("********");
        assertThat(settings.sensitiveKeys().isSensitive("nationalId")).isTrue();
        assertThat(settings.sensitiveKeys().isSensitive("password")).isTrue();
    }

    @Test
    void disablingMaskingYieldsAPolicyThatMatchesNothing() {
        JavalibsLogFormatSettings settings = JavalibsLogFormatSettings.from(
                environmentWith(Map.of("javalibs.logging.masking.enabled", "false")));

        assertThat(settings.maskingEnabled()).isFalse();
        assertThat(settings.sensitiveKeys().isSensitive("password")).isFalse();
    }

    @Test
    void stacktraceDefaultsAreOnAndBounded() {
        JavalibsLogFormatSettings settings = JavalibsLogFormatSettings.from(environmentWith(Map.of()));

        assertThat(settings.stacktraceEnabled()).isTrue();
        assertThat(settings.stacktraceMaxLength()).isEqualTo(4096);
    }
}
```

- [ ] **Step 3: Chạy test để chắc chắn nó đỏ**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn -pl javalibs-logging/javalibs-logging-logback -am test
```

Kỳ vọng: **FAIL**, `cannot find symbol: class JavalibsLogFormatSettings`.

- [ ] **Step 4: Viết implementation**

Tạo `javalibs-logging/javalibs-logging-logback/src/main/java/io/javalibs/logging/logback/JavalibsLogFormatSettings.java`:

```java
package io.javalibs.logging.logback;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.javalibs.logging.LogHost;
import io.javalibs.logging.SensitiveDataMasker;
import io.javalibs.logging.SensitiveKeys;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * Snapshot of the {@code javalibs.logging.*} configuration needed by
 * {@link JavalibsJsonLogFormatter}.
 *
 * <p>The formatter is built by Spring Boot while the logging system starts —
 * before any {@code ApplicationContext} exists — so configuration cannot arrive
 * through {@code @ConfigurationProperties} beans. It is read straight from the
 * {@link Environment} once, here, and cached for the lifetime of the formatter
 * so that no property lookup happens per log line.</p>
 */
public final class JavalibsLogFormatSettings {

    /** Default cap on the number of characters kept from one stack trace. */
    public static final int DEFAULT_STACKTRACE_MAX_LENGTH = 4096;

    private final String service;
    private final String host;
    private final List<String> tags;
    private final Map<String, String> metadata;
    private final SensitiveKeys sensitiveKeys;
    private final String mask;
    private final boolean maskingEnabled;
    private final boolean stacktraceEnabled;
    private final int stacktraceMaxLength;

    private JavalibsLogFormatSettings(String service, String host, List<String> tags,
            Map<String, String> metadata, SensitiveKeys sensitiveKeys, String mask,
            boolean maskingEnabled, boolean stacktraceEnabled, int stacktraceMaxLength) {
        this.service = service;
        this.host = host;
        this.tags = List.copyOf(tags);
        this.metadata = Map.copyOf(metadata);
        this.sensitiveKeys = sensitiveKeys;
        this.mask = mask;
        this.maskingEnabled = maskingEnabled;
        this.stacktraceEnabled = stacktraceEnabled;
        this.stacktraceMaxLength = stacktraceMaxLength;
    }

    /**
     * Reads the settings from the environment, applying every documented default.
     *
     * @param environment the application environment, must not be {@code null}
     * @return an immutable settings snapshot
     */
    public static JavalibsLogFormatSettings from(Environment environment) {
        Binder binder = Binder.get(environment);

        String service = firstNonBlank(environment.getProperty("javalibs.logging.service"),
                environment.getProperty("spring.application.name"));
        String host = firstNonBlank(environment.getProperty("javalibs.logging.host"), LogHost.current());

        List<String> tags = binder.bind("javalibs.logging.tags", Bindable.listOf(String.class))
                .orElseGet(List::of);

        Map<String, String> metadata = new LinkedHashMap<>(
                binder.bind("javalibs.logging.metadata", Bindable.mapOf(String.class, String.class))
                        .orElseGet(Map::of));
        putIfPresent(metadata, "env", environment.getProperty("javalibs.logging.environment"));
        putIfPresent(metadata, "version", environment.getProperty("javalibs.logging.version"));

        boolean maskingEnabled =
                environment.getProperty("javalibs.logging.masking.enabled", Boolean.class, Boolean.TRUE);
        List<String> extraKeys = binder.bind("javalibs.logging.masking.keys", Bindable.listOf(String.class))
                .orElseGet(List::of);
        SensitiveKeys sensitiveKeys =
                maskingEnabled ? SensitiveKeys.withAdditional(extraKeys) : SensitiveKeys.none();
        String mask = environment.getProperty("javalibs.logging.masking.value",
                SensitiveDataMasker.DEFAULT_MASK);

        boolean stacktraceEnabled =
                environment.getProperty("javalibs.logging.stacktrace.enabled", Boolean.class, Boolean.TRUE);
        int stacktraceMaxLength = environment.getProperty("javalibs.logging.stacktrace.max-length",
                Integer.class, DEFAULT_STACKTRACE_MAX_LENGTH);

        return new JavalibsLogFormatSettings(service, host, tags, metadata, sensitiveKeys, mask,
                maskingEnabled, stacktraceEnabled, stacktraceMaxLength);
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return (preferred != null && !preferred.isBlank()) ? preferred : fallback;
    }

    private static void putIfPresent(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    /** @return the value of the {@code service} field, may be {@code null} */
    public String service() {
        return service;
    }

    /** @return the value of the {@code host} field */
    public String host() {
        return host;
    }

    /** @return tags attached to every log event */
    public List<String> tags() {
        return tags;
    }

    /** @return metadata attached to every log event */
    public Map<String, String> metadata() {
        return metadata;
    }

    /** @return the sensitive key policy; matches nothing when masking is off */
    public SensitiveKeys sensitiveKeys() {
        return sensitiveKeys;
    }

    /** @return the replacement written in place of a secret */
    public String mask() {
        return mask;
    }

    /** @return whether masking is switched on */
    public boolean maskingEnabled() {
        return maskingEnabled;
    }

    /** @return whether stack traces are written into {@code errors[]} */
    public boolean stacktraceEnabled() {
        return stacktraceEnabled;
    }

    /** @return the cap on characters kept from one stack trace */
    public int stacktraceMaxLength() {
        return stacktraceMaxLength;
    }
}
```

- [ ] **Step 5: Chạy test để chắc chắn nó xanh**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn -pl javalibs-logging/javalibs-logging-logback -am test
```

Kỳ vọng: **PASS**, `Tests run: 7, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit**

```bash
git add javalibs-logging
git commit -m "feat(logging): module -logback + JavalibsLogFormatSettings đọc từ Environment"
```

---

### Task 6: `JavalibsJsonLogFormatter` — phần envelope

**Files:**
- Create: `javalibs-logging/javalibs-logging-logback/src/main/java/io/javalibs/logging/logback/JavalibsJsonLogFormatter.java`
- Test: `javalibs-logging/javalibs-logging-logback/src/test/java/io/javalibs/logging/logback/LogEvents.java`
- Test: `javalibs-logging/javalibs-logging-logback/src/test/java/io/javalibs/logging/logback/JavalibsJsonLogFormatterTest.java`

**Interfaces:**
- Consumes: `JavalibsLogFormatSettings` (Task 5), `LogFields` (Task 1).
- Produces:
  - `JavalibsJsonLogFormatter` — `public JavalibsJsonLogFormatter(Environment environment)` (constructor Boot gọi) và constructor package-private `JavalibsJsonLogFormatter(JavalibsLogFormatSettings settings)` dùng cho test. Kế thừa `String format(ILoggingEvent)`.
  - `LogEvents` (test fixture, package-private) — `static LoggingEvent event(Level level, String message)`, `static JavalibsLogFormatSettings settings(Map<String, Object> properties)`, `static Map<String, Object> parse(String json)`. Task 7, 8 và 9 dùng lại fixture này.

- [ ] **Step 1: Viết test fixture**

Tạo `javalibs-logging/javalibs-logging-logback/src/test/java/io/javalibs/logging/logback/LogEvents.java`:

```java
package io.javalibs.logging.logback;

import java.time.Instant;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/** Test helpers for building log events and reading back formatter output. */
final class LogEvents {

    /** Fixed event time so timestamp assertions are deterministic. */
    static final Instant FIXED_INSTANT = Instant.parse("2026-08-13T10:25:30.123Z");

    private LogEvents() {
    }

    static LoggingEvent event(Level level, String message) {
        LoggingEvent event = new LoggingEvent();
        event.setLoggerName("io.javalibs.demo.AuthService");
        event.setLevel(level);
        event.setMessage(message);
        event.setInstant(FIXED_INSTANT);
        event.setThreadName("main");
        return event;
    }

    static JavalibsLogFormatSettings settings(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        return JavalibsLogFormatSettings.from(environment);
    }

    static Map<String, Object> parse(String json) {
        return JsonParserFactory.getJsonParser().parseMap(json);
    }
}
```

- [ ] **Step 2: Viết test đỏ cho envelope**

Tạo `javalibs-logging/javalibs-logging-logback/src/test/java/io/javalibs/logging/logback/JavalibsJsonLogFormatterTest.java`:

```java
package io.javalibs.logging.logback;

import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import io.javalibs.logging.LogFields;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JavalibsJsonLogFormatterTest {

    private final JavalibsJsonLogFormatter formatter = new JavalibsJsonLogFormatter(
            LogEvents.settings(Map.of(
                    "javalibs.logging.service", "auth-service",
                    "javalibs.logging.host", "server-01")));

    @Test
    void writesTheEnvelopeFieldsInSchemaOrder() {
        LoggingEvent event = LogEvents.event(Level.INFO, "User login successful");

        Map<String, Object> record = LogEvents.parse(formatter.format(event));

        assertThat(record.keySet()).startsWith(
                LogFields.TIMESTAMP, LogFields.LEVEL, LogFields.MESSAGE, LogFields.LOG_ID,
                LogFields.SERVICE, LogFields.HOST);
        assertThat(record.get(LogFields.TIMESTAMP)).isEqualTo("2026-08-13T10:25:30.123Z");
        assertThat(record.get(LogFields.LEVEL)).isEqualTo("INFO");
        assertThat(record.get(LogFields.MESSAGE)).isEqualTo("User login successful");
        assertThat(record.get(LogFields.SERVICE)).isEqualTo("auth-service");
        assertThat(record.get(LogFields.HOST)).isEqualTo("server-01");
    }

    @Test
    void givesEveryEntryItsOwnLogId() {
        String first = (String) LogEvents.parse(
                formatter.format(LogEvents.event(Level.INFO, "one"))).get(LogFields.LOG_ID);
        String second = (String) LogEvents.parse(
                formatter.format(LogEvents.event(Level.INFO, "two"))).get(LogFields.LOG_ID);

        assertThat(first).matches("[0-9a-f-]{36}");
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void endsEachRecordWithASingleNewlineSoOneLineIsOneEvent() {
        String rendered = formatter.format(LogEvents.event(Level.WARN, "careful"));

        assertThat(rendered).endsWith("\n");
        assertThat(rendered.stripTrailing()).doesNotContain("\n");
    }

    @Test
    void omitsServiceWhenItIsNotConfiguredButAlwaysWritesAHost() {
        JavalibsJsonLogFormatter bare = new JavalibsJsonLogFormatter(LogEvents.settings(Map.of()));

        Map<String, Object> record = LogEvents.parse(bare.format(LogEvents.event(Level.INFO, "hi")));

        assertThat(record).doesNotContainKey(LogFields.SERVICE);
        assertThat((String) record.get(LogFields.HOST)).isNotBlank();
    }

    @Test
    void survivesAnEventWithoutAMessage() {
        LoggingEvent event = LogEvents.event(Level.ERROR, null);

        Map<String, Object> record = LogEvents.parse(formatter.format(event));

        assertThat(record.get(LogFields.LEVEL)).isEqualTo("ERROR");
    }
}
```

- [ ] **Step 3: Chạy test để chắc chắn nó đỏ**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn -pl javalibs-logging/javalibs-logging-logback test
```

Kỳ vọng: **FAIL**, `cannot find symbol: class JavalibsJsonLogFormatter`.

- [ ] **Step 4: Viết implementation**

Tạo `javalibs-logging/javalibs-logging-logback/src/main/java/io/javalibs/logging/logback/JavalibsJsonLogFormatter.java`:

```java
package io.javalibs.logging.logback;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.javalibs.logging.LogFields;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.JsonWriterStructuredLogFormatter;
import org.springframework.core.env.Environment;

/**
 * Renders every Logback event as one JSON record following the javalibs log
 * schema.
 *
 * <p>Activate it by pointing Spring Boot's structured logging at this class:</p>
 *
 * <pre>
 * logging.structured.format.console=io.javalibs.logging.logback.JavalibsJsonLogFormatter
 * </pre>
 *
 * <p>Setting {@code javalibs.logging.json.enabled=true} does the same thing
 * without naming the class — see
 * {@code JavalibsLoggingEnvironmentPostProcessor}.</p>
 *
 * <p>This class is instantiated by Spring Boot before the application context
 * exists, so it accepts only an {@link Environment} and never a bean.</p>
 */
public class JavalibsJsonLogFormatter extends JsonWriterStructuredLogFormatter<ILoggingEvent> {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    /**
     * Constructor used by Spring Boot's structured logging support.
     *
     * @param environment the application environment
     */
    public JavalibsJsonLogFormatter(Environment environment) {
        this(JavalibsLogFormatSettings.from(environment));
    }

    JavalibsJsonLogFormatter(JavalibsLogFormatSettings settings) {
        super(JsonWriter.<ILoggingEvent>of(members -> configure(members, settings)).withNewLineAtEnd());
    }

    private static void configure(JsonWriter.Members<ILoggingEvent> members,
            JavalibsLogFormatSettings settings) {
        members.add(LogFields.TIMESTAMP, event -> TIMESTAMP_FORMAT.format(event.getInstant()));
        members.add(LogFields.LEVEL, event -> String.valueOf(event.getLevel()));
        members.add(LogFields.MESSAGE, ILoggingEvent::getFormattedMessage);
        members.add(LogFields.LOG_ID, event -> UUID.randomUUID().toString());
        members.add(LogFields.SERVICE, event -> settings.service()).whenHasLength();
        members.add(LogFields.HOST, event -> settings.host()).whenHasLength();
    }
}
```

- [ ] **Step 5: Chạy test để chắc chắn nó xanh**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn -pl javalibs-logging/javalibs-logging-logback test
```

Kỳ vọng: **PASS**, `Tests run: 12, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit**

```bash
git add javalibs-logging/javalibs-logging-logback
git commit -m "feat(logging): JavalibsJsonLogFormatter — envelope timestamp/level/message/log_id/service/host"
```

---

### Task 7: Formatter — `user_id`, `ip`, `request`, `response`, `tags`, `metadata`

**Files:**
- Modify: `javalibs-logging/javalibs-logging-logback/src/main/java/io/javalibs/logging/logback/JavalibsJsonLogFormatter.java`
- Test: `javalibs-logging/javalibs-logging-logback/src/test/java/io/javalibs/logging/logback/JavalibsJsonLogFormatterContextTest.java`

**Interfaces:**
- Consumes: `LogEvents` fixture (Task 6), `LogFields.PROMOTED_MDC_KEYS` (Task 1).
- Produces: không có API mới — mở rộng hành vi của `format(ILoggingEvent)`.

**Nguồn dữ liệu:** `user_id` và `ip` lấy từ MDC (`userId`, `clientIp`);
`request`/`response` lấy từ `KeyValuePair` do `HttpAccessLogFilter` gắn qua API
fluent của SLF4J; `tags` gộp tag tĩnh + MDC `logTags` + `KeyValuePair` tên
`tags`; `metadata` gom metadata tĩnh + mọi MDC key **chưa** được đưa lên field
cấp cao + mọi `KeyValuePair` không phải `request`/`response`/`tags`.

- [ ] **Step 1: Viết test đỏ**

Tạo `javalibs-logging/javalibs-logging-logback/src/test/java/io/javalibs/logging/logback/JavalibsJsonLogFormatterContextTest.java`:

```java
package io.javalibs.logging.logback;

import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import io.javalibs.logging.LogFields;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;

import static org.assertj.core.api.Assertions.assertThat;

class JavalibsJsonLogFormatterContextTest {

    private final JavalibsJsonLogFormatter formatter = new JavalibsJsonLogFormatter(
            LogEvents.settings(Map.of(
                    "javalibs.logging.service", "auth-service",
                    "javalibs.logging.tags", "auth",
                    "javalibs.logging.environment", "production",
                    "javalibs.logging.version", "1.2.3")));

    @Test
    void promotesUserIdAndIpFromMdcToTopLevelFields() {
        LoggingEvent event = LogEvents.event(Level.INFO, "User login successful");
        event.setMDCPropertyMap(Map.of(
                LogFields.MDC_USER_ID, "user_12345",
                LogFields.MDC_CLIENT_IP, "192.168.1.10"));

        Map<String, Object> record = LogEvents.parse(formatter.format(event));

        assertThat(record.get(LogFields.USER_ID)).isEqualTo("user_12345");
        assertThat(record.get(LogFields.IP)).isEqualTo("192.168.1.10");
    }

    @Test
    void doesNotRepeatPromotedMdcKeysInsideMetadata() {
        LoggingEvent event = LogEvents.event(Level.INFO, "hi");
        event.setMDCPropertyMap(Map.of(
                LogFields.MDC_USER_ID, "user_12345",
                LogFields.MDC_CLIENT_IP, "192.168.1.10",
                "correlationId", "8f14e45fceea167a"));

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata =
                (Map<String, Object>) LogEvents.parse(formatter.format(event)).get(LogFields.METADATA);

        assertThat(metadata)
                .containsEntry("correlationId", "8f14e45fceea167a")
                .containsEntry("env", "production")
                .containsEntry("version", "1.2.3")
                .doesNotContainKeys(LogFields.MDC_USER_ID, LogFields.MDC_CLIENT_IP);
    }

    @Test
    void writesRequestAndResponseSubtreesFromKeyValuePairs() {
        LoggingEvent event = LogEvents.event(Level.INFO, "POST /api/login 200 152ms");
        event.setKeyValuePairs(List.of(
                new KeyValuePair(LogFields.REQUEST,
                        Map.of(LogFields.METHOD, "POST", LogFields.ENDPOINT, "/api/login")),
                new KeyValuePair(LogFields.RESPONSE,
                        Map.of(LogFields.STATUS, 200, LogFields.LATENCY_MS, 152L))));

        Map<String, Object> record = LogEvents.parse(formatter.format(event));

        @SuppressWarnings("unchecked")
        Map<String, Object> request = (Map<String, Object>) record.get(LogFields.REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) record.get(LogFields.RESPONSE);
        assertThat(request).containsEntry(LogFields.ENDPOINT, "/api/login");
        assertThat(response).containsEntry(LogFields.STATUS, 200);
    }

    @Test
    void mergesStaticTagsWithRequestScopedAndPerLineTags() {
        LoggingEvent event = LogEvents.event(Level.INFO, "hi");
        event.setMDCPropertyMap(Map.of(LogFields.MDC_TAGS, "login, user"));
        event.setKeyValuePairs(List.of(new KeyValuePair(LogFields.TAGS, List.of("user", "audit"))));

        @SuppressWarnings("unchecked")
        List<String> tags =
                (List<String>) LogEvents.parse(formatter.format(event)).get(LogFields.TAGS);

        assertThat(tags).containsExactly("auth", "login", "user", "audit");
    }

    @Test
    void putsArbitraryKeyValuePairsIntoMetadata() {
        LoggingEvent event = LogEvents.event(Level.INFO, "Order created");
        event.setKeyValuePairs(List.of(new KeyValuePair("orderId", "ord-99")));

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata =
                (Map<String, Object>) LogEvents.parse(formatter.format(event)).get(LogFields.METADATA);

        assertThat(metadata).containsEntry("orderId", "ord-99");
    }

    @Test
    void omitsUserIdIpRequestAndResponseForNonHttpEvents() {
        Map<String, Object> record =
                LogEvents.parse(formatter.format(LogEvents.event(Level.INFO, "Scheduled sweep done")));

        assertThat(record).doesNotContainKeys(
                LogFields.USER_ID, LogFields.IP, LogFields.REQUEST, LogFields.RESPONSE);
    }
}
```

- [ ] **Step 2: Chạy test để chắc chắn nó đỏ**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn -pl javalibs-logging/javalibs-logging-logback test
```

Kỳ vọng: **FAIL** — các assertion về `user_id`, `metadata`, `tags` trả `null`.

- [ ] **Step 3: Mở rộng formatter**

Trong `JavalibsJsonLogFormatter.java`, thêm import:

```java
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.event.KeyValuePair;
```

Thêm hằng số ngay dưới `TIMESTAMP_FORMAT`:

```java
    /** Key-value keys rendered as dedicated fields, hence excluded from metadata. */
    private static final Set<String> RESERVED_KEY_VALUES =
            Set.of(LogFields.REQUEST, LogFields.RESPONSE, LogFields.TAGS);
```

Thêm vào cuối phương thức `configure(...)`, ngay sau dòng `members.add(LogFields.HOST, ...)`:

```java
        members.add(LogFields.USER_ID, event -> mdc(event).get(LogFields.MDC_USER_ID)).whenHasLength();
        members.add(LogFields.IP, event -> mdc(event).get(LogFields.MDC_CLIENT_IP)).whenHasLength();
        members.add(LogFields.REQUEST, event -> keyValue(event, LogFields.REQUEST)).whenNotNull();
        members.add(LogFields.RESPONSE, event -> keyValue(event, LogFields.RESPONSE)).whenNotNull();
        members.add(LogFields.TAGS, event -> tags(event, settings)).whenNotEmpty();
        members.add(LogFields.METADATA, event -> metadata(event, settings)).whenNotEmpty();
```

Thêm các phương thức trợ giúp vào cuối class:

```java
    private static Map<String, String> mdc(ILoggingEvent event) {
        Map<String, String> properties = event.getMDCPropertyMap();
        return (properties != null) ? properties : Map.of();
    }

    private static Object keyValue(ILoggingEvent event, String key) {
        List<KeyValuePair> pairs = event.getKeyValuePairs();
        if (pairs == null) {
            return null;
        }
        for (KeyValuePair pair : pairs) {
            if (key.equals(pair.key)) {
                return pair.value;
            }
        }
        return null;
    }

    private static List<String> tags(ILoggingEvent event, JavalibsLogFormatSettings settings) {
        Set<String> tags = new LinkedHashSet<>(settings.tags());
        addSplitTags(tags, mdc(event).get(LogFields.MDC_TAGS));
        Object fromEvent = keyValue(event, LogFields.TAGS);
        if (fromEvent instanceof Collection<?> collection) {
            for (Object tag : collection) {
                addTag(tags, tag);
            }
        } else if (fromEvent != null) {
            addTag(tags, fromEvent);
        }
        return List.copyOf(tags);
    }

    private static void addSplitTags(Set<String> tags, String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isBlank()) {
            return;
        }
        for (String tag : commaSeparated.split(",")) {
            addTag(tags, tag);
        }
    }

    private static void addTag(Set<String> tags, Object tag) {
        if (tag == null) {
            return;
        }
        String text = tag.toString().trim();
        if (!text.isEmpty()) {
            tags.add(text);
        }
    }

    private static Map<String, Object> metadata(ILoggingEvent event, JavalibsLogFormatSettings settings) {
        Map<String, Object> metadata = new LinkedHashMap<>(settings.metadata());
        mdc(event).forEach((key, value) -> {
            if (!LogFields.PROMOTED_MDC_KEYS.contains(key)) {
                metadata.put(key, value);
            }
        });
        List<KeyValuePair> pairs = event.getKeyValuePairs();
        if (pairs != null) {
            for (KeyValuePair pair : pairs) {
                if (!RESERVED_KEY_VALUES.contains(pair.key)) {
                    metadata.put(pair.key, pair.value);
                }
            }
        }
        return metadata;
    }
```

- [ ] **Step 4: Chạy test để chắc chắn nó xanh**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn -pl javalibs-logging/javalibs-logging-logback test
```

Kỳ vọng: **PASS**, `Tests run: 18, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

```bash
git add javalibs-logging/javalibs-logging-logback
git commit -m "feat(logging): formatter gom user_id/ip/request/response/tags/metadata"
```

---

### Task 8: Formatter — mảng `errors[]`

**Files:**
- Modify: `javalibs-logging/javalibs-logging-logback/src/main/java/io/javalibs/logging/logback/JavalibsJsonLogFormatter.java`
- Test: `javalibs-logging/javalibs-logging-logback/src/test/java/io/javalibs/logging/logback/JavalibsJsonLogFormatterErrorsTest.java`

**Interfaces:**
- Consumes: `LogEvents` fixture (Task 6), `JavalibsLogFormatSettings.stacktraceEnabled()` / `.stacktraceMaxLength()` (Task 5).
- Produces: không có API mới.

**Yêu cầu từ schema:** `errors` **luôn xuất hiện**, là `[]` khi không có lỗi —
đây là ngoại lệ duy nhất của quy tắc "bỏ field khi rỗng". Vị trí của nó trong
bản ghi nằm **giữa** `tags` và `metadata`.

- [ ] **Step 1: Viết test đỏ**

Tạo `javalibs-logging/javalibs-logging-logback/src/test/java/io/javalibs/logging/logback/JavalibsJsonLogFormatterErrorsTest.java`:

```java
package io.javalibs.logging.logback;

import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import io.javalibs.logging.LogFields;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JavalibsJsonLogFormatterErrorsTest {

    private final JavalibsJsonLogFormatter formatter =
            new JavalibsJsonLogFormatter(LogEvents.settings(Map.of()));

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> errorsOf(LoggingEvent event) {
        return (List<Map<String, Object>>) LogEvents.parse(formatter.format(event)).get(LogFields.ERRORS);
    }

    @Test
    void writesAnEmptyArrayWhenThereIsNoThrowable() {
        Map<String, Object> record =
                LogEvents.parse(formatter.format(LogEvents.event(Level.INFO, "all good")));

        assertThat(record).containsKey(LogFields.ERRORS);
        assertThat((List<?>) record.get(LogFields.ERRORS)).isEmpty();
    }

    @Test
    void writesTypeMessageAndStacktraceForAThrowable() {
        LoggingEvent event = LogEvents.event(Level.ERROR, "Login failed");
        event.setThrowableProxy(new ThrowableProxy(new IllegalStateException("account locked")));

        List<Map<String, Object>> errors = errorsOf(event);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0))
                .containsEntry(LogFields.ERROR_TYPE, "java.lang.IllegalStateException")
                .containsEntry(LogFields.ERROR_MESSAGE, "account locked");
        assertThat((String) errors.get(0).get(LogFields.ERROR_STACKTRACE))
                .contains("JavalibsJsonLogFormatterErrorsTest");
    }

    @Test
    void walksTheWholeCauseChain() {
        Throwable root = new IllegalArgumentException("bad id");
        Throwable wrapper = new IllegalStateException("cannot load user", root);
        LoggingEvent event = LogEvents.event(Level.ERROR, "boom");
        event.setThrowableProxy(new ThrowableProxy(wrapper));

        List<Map<String, Object>> errors = errorsOf(event);

        assertThat(errors).hasSize(2);
        assertThat(errors.get(0)).containsEntry(LogFields.ERROR_TYPE, "java.lang.IllegalStateException");
        assertThat(errors.get(1)).containsEntry(LogFields.ERROR_TYPE, "java.lang.IllegalArgumentException");
    }

    @Test
    void omitsStacktraceWhenItIsSwitchedOff() {
        JavalibsJsonLogFormatter withoutTraces = new JavalibsJsonLogFormatter(
                LogEvents.settings(Map.of("javalibs.logging.stacktrace.enabled", "false")));
        LoggingEvent event = LogEvents.event(Level.ERROR, "boom");
        event.setThrowableProxy(new ThrowableProxy(new IllegalStateException("nope")));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> errors = (List<Map<String, Object>>) LogEvents
                .parse(withoutTraces.format(event)).get(LogFields.ERRORS);

        assertThat(errors.get(0)).doesNotContainKey(LogFields.ERROR_STACKTRACE);
    }

    @Test
    void truncatesAStacktraceToTheConfiguredLength() {
        JavalibsJsonLogFormatter shortTraces = new JavalibsJsonLogFormatter(
                LogEvents.settings(Map.of("javalibs.logging.stacktrace.max-length", "40")));
        LoggingEvent event = LogEvents.event(Level.ERROR, "boom");
        event.setThrowableProxy(new ThrowableProxy(new IllegalStateException("nope")));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> errors = (List<Map<String, Object>>) LogEvents
                .parse(shortTraces.format(event)).get(LogFields.ERRORS);

        assertThat((String) errors.get(0).get(LogFields.ERROR_STACKTRACE)).hasSizeLessThanOrEqualTo(40);
    }

    @Test
    void placesErrorsBetweenTagsAndMetadata() {
        LoggingEvent event = LogEvents.event(Level.ERROR, "boom");
        event.setMDCPropertyMap(Map.of(LogFields.MDC_TAGS, "auth", "correlationId", "abc"));
        event.setThrowableProxy(new ThrowableProxy(new IllegalStateException("nope")));

        List<String> keys = List.copyOf(LogEvents.parse(formatter.format(event)).keySet());

        assertThat(keys.indexOf(LogFields.ERRORS)).isGreaterThan(keys.indexOf(LogFields.TAGS));
        assertThat(keys.indexOf(LogFields.ERRORS)).isLessThan(keys.indexOf(LogFields.METADATA));
    }
}
```

- [ ] **Step 2: Chạy test để chắc chắn nó đỏ**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn -pl javalibs-logging/javalibs-logging-logback test
```

Kỳ vọng: **FAIL** — bản ghi chưa có khoá `errors`.

- [ ] **Step 3: Mở rộng formatter**

Trong `JavalibsJsonLogFormatter.java`, bổ sung import:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;

import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
```

Thêm hằng số dưới `RESERVED_KEY_VALUES`:

```java
    /** Upper bound on the cause chain, so a cyclic or pathological chain cannot bloat a line. */
    private static final int MAX_ERROR_CHAIN = 10;
```

Trong `configure(...)`, **thay** dòng

```java
        members.add(LogFields.METADATA, event -> metadata(event, settings)).whenNotEmpty();
```

bằng hai dòng theo đúng thứ tự schema:

```java
        members.add(LogFields.ERRORS, event -> errors(event, settings));
        members.add(LogFields.METADATA, event -> metadata(event, settings)).whenNotEmpty();
```

Thêm hai phương thức vào cuối class:

```java
    private static List<Map<String, Object>> errors(ILoggingEvent event,
            JavalibsLogFormatSettings settings) {
        IThrowableProxy throwable = event.getThrowableProxy();
        if (throwable == null) {
            return List.of();
        }
        List<Map<String, Object>> errors = new ArrayList<>(2);
        Set<IThrowableProxy> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        while (throwable != null && errors.size() < MAX_ERROR_CHAIN && visited.add(throwable)) {
            Map<String, Object> error = new LinkedHashMap<>(3);
            error.put(LogFields.ERROR_TYPE, throwable.getClassName());
            error.put(LogFields.ERROR_MESSAGE, throwable.getMessage());
            if (settings.stacktraceEnabled()) {
                error.put(LogFields.ERROR_STACKTRACE,
                        stackTrace(throwable, settings.stacktraceMaxLength()));
            }
            errors.add(error);
            throwable = throwable.getCause();
        }
        return errors;
    }

    private static String stackTrace(IThrowableProxy throwable, int maxLength) {
        StackTraceElementProxy[] frames = throwable.getStackTraceElementProxyArray();
        if (frames == null || frames.length == 0) {
            return "";
        }
        StringBuilder rendered = new StringBuilder(256);
        for (StackTraceElementProxy frame : frames) {
            if (rendered.length() >= maxLength) {
                break;
            }
            rendered.append(frame.getSTEAsString()).append('\n');
        }
        return (rendered.length() > maxLength) ? rendered.substring(0, maxLength) : rendered.toString();
    }
```

- [ ] **Step 4: Chạy test để chắc chắn nó xanh**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn -pl javalibs-logging/javalibs-logging-logback test
```

Kỳ vọng: **PASS**, `Tests run: 24, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

```bash
git add javalibs-logging/javalibs-logging-logback
git commit -m "feat(logging): formatter dựng errors[] từ chuỗi cause, luôn có mặt dưới dạng []"
```

---

### Task 9: Formatter — che dữ liệu nhạy cảm

**Files:**
- Modify: `javalibs-logging/javalibs-logging-logback/src/main/java/io/javalibs/logging/logback/JavalibsJsonLogFormatter.java`
- Test: `javalibs-logging/javalibs-logging-logback/src/test/java/io/javalibs/logging/logback/JavalibsJsonLogFormatterMaskingTest.java`

**Interfaces:**
- Consumes: `SensitiveKeys.isSensitive(String)` (Task 1), `JavalibsLogFormatSettings.maskingEnabled()` / `.sensitiveKeys()` / `.mask()` (Task 5).
- Produces: không có API mới.

**Cơ chế — đã kiểm chứng, đừng thay bằng bộ duyệt tự viết:** một
`JsonWriter.ValueProcessor<Object>` đăng ký qua `Members.applyingValueProcessor`
được `JsonWriter` gọi cho **mọi** giá trị nó ghi, kèm `MemberPath` đầy đủ — bao
gồm giá trị nằm sâu trong map lồng nhau (`request.body.password`) và trong phần
tử list (`request.list[0].token`). Kiểu phải là `Object` chứ không phải
`String`: bản `Object` che được cả giá trị số (`"pin": 1234`), bản `String` để
lọt.

- [ ] **Step 1: Viết test đỏ**

Tạo `javalibs-logging/javalibs-logging-logback/src/test/java/io/javalibs/logging/logback/JavalibsJsonLogFormatterMaskingTest.java`:

```java
package io.javalibs.logging.logback;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import io.javalibs.logging.LogFields;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;

import static org.assertj.core.api.Assertions.assertThat;

class JavalibsJsonLogFormatterMaskingTest {

    private static LoggingEvent loginEvent() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", "user@example.com");
        body.put("password", "hunter2");
        body.put("pin", 1234);
        body.put("amount", 99);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put(LogFields.METHOD, "POST");
        request.put(LogFields.ENDPOINT, "/api/login");
        request.put(LogFields.HEADERS, Map.of("Authorization", "Bearer abc.def"));
        request.put(LogFields.BODY, body);

        LoggingEvent event = LogEvents.event(Level.INFO, "POST /api/login 200 152ms");
        event.setKeyValuePairs(List.of(new KeyValuePair(LogFields.REQUEST, request)));
        return event;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requestBodyOf(String json) {
        Map<String, Object> request = (Map<String, Object>) LogEvents.parse(json).get(LogFields.REQUEST);
        return (Map<String, Object>) request.get(LogFields.BODY);
    }

    @Test
    void masksSecretsNestedInsideTheRequestBody() {
        JavalibsJsonLogFormatter formatter =
                new JavalibsJsonLogFormatter(LogEvents.settings(Map.of()));

        Map<String, Object> body = requestBodyOf(formatter.format(loginEvent()));

        assertThat(body).containsEntry("password", "********");
    }

    @Test
    void masksNumericSecretsToo() {
        JavalibsJsonLogFormatter formatter =
                new JavalibsJsonLogFormatter(LogEvents.settings(Map.of()));

        Map<String, Object> body = requestBodyOf(formatter.format(loginEvent()));

        assertThat(body).containsEntry("pin", "********");
    }

    @Test
    void leavesOrdinaryFieldsUntouched() {
        JavalibsJsonLogFormatter formatter =
                new JavalibsJsonLogFormatter(LogEvents.settings(Map.of()));

        Map<String, Object> body = requestBodyOf(formatter.format(loginEvent()));

        assertThat(body)
                .containsEntry("email", "user@example.com")
                .containsEntry("amount", 99);
    }

    @Test
    void masksCredentialCarryingHeaders() {
        JavalibsJsonLogFormatter formatter =
                new JavalibsJsonLogFormatter(LogEvents.settings(Map.of()));

        @SuppressWarnings("unchecked")
        Map<String, Object> request = (Map<String, Object>) LogEvents
                .parse(formatter.format(loginEvent())).get(LogFields.REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) request.get(LogFields.HEADERS);

        assertThat(headers).containsEntry("Authorization", "********");
    }

    @Test
    void honoursAConfiguredReplacementAndExtraKeys() {
        JavalibsJsonLogFormatter formatter = new JavalibsJsonLogFormatter(LogEvents.settings(Map.of(
                "javalibs.logging.masking.value", "[redacted]",
                "javalibs.logging.masking.keys", "email")));

        Map<String, Object> body = requestBodyOf(formatter.format(loginEvent()));

        assertThat(body)
                .containsEntry("password", "[redacted]")
                .containsEntry("email", "[redacted]");
    }

    @Test
    void writesEverythingInClearWhenMaskingIsSwitchedOff() {
        JavalibsJsonLogFormatter formatter = new JavalibsJsonLogFormatter(
                LogEvents.settings(Map.of("javalibs.logging.masking.enabled", "false")));

        Map<String, Object> body = requestBodyOf(formatter.format(loginEvent()));

        assertThat(body).containsEntry("password", "hunter2");
    }
}
```

- [ ] **Step 2: Chạy test để chắc chắn nó đỏ**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn -pl javalibs-logging/javalibs-logging-logback test
```

Kỳ vọng: **FAIL** — `password` vẫn là `hunter2`.

- [ ] **Step 3: Cài value processor**

Trong `JavalibsJsonLogFormatter.java`, thêm import:

```java
import io.javalibs.logging.SensitiveKeys;
```

Thêm vào **cuối** phương thức `configure(...)`, sau dòng `members.add(LogFields.METADATA, ...)`:

```java
        if (settings.maskingEnabled()) {
            members.applyingValueProcessor(maskingProcessor(settings));
        }
```

Thêm phương thức vào cuối class:

```java
    /**
     * Builds the processor that replaces every value sitting under a sensitive
     * field name, at any depth, with the configured mask.
     *
     * <p>The processor is typed on {@code Object} rather than {@code String} on
     * purpose: a secret written as a number — a PIN, a card number — would slip
     * through a {@code String} processor untouched.</p>
     */
    private static JsonWriter.ValueProcessor<Object> maskingProcessor(
            JavalibsLogFormatSettings settings) {
        SensitiveKeys keys = settings.sensitiveKeys();
        String mask = settings.mask();
        return JsonWriter.ValueProcessor.of(Object.class, (Object value) -> (Object) mask)
                .whenHasPath(path -> keys.isSensitive(path.name()));
    }
```

- [ ] **Step 4: Chạy test để chắc chắn nó xanh**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn -pl javalibs-logging/javalibs-logging-logback test
```

Kỳ vọng: **PASS**, `Tests run: 30, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

```bash
git add javalibs-logging/javalibs-logging-logback
git commit -m "feat(logging): che dữ liệu nhạy cảm ở mọi độ sâu qua JsonWriter ValueProcessor"
```

---

### Task 10: `JavalibsLoggingEnvironmentPostProcessor`

**Files:**
- Create: `javalibs-logging/javalibs-logging-logback/src/main/java/io/javalibs/logging/logback/JavalibsLoggingEnvironmentPostProcessor.java`
- Create: `javalibs-logging/javalibs-logging-logback/src/main/resources/META-INF/spring.factories`
- Test: `javalibs-logging/javalibs-logging-logback/src/test/java/io/javalibs/logging/logback/JavalibsLoggingEnvironmentPostProcessorTest.java`

**Interfaces:**
- Consumes: `JavalibsJsonLogFormatter` (Task 6).
- Produces: `JavalibsLoggingEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered` — hằng số package-private `ENABLED_PROPERTY = "javalibs.logging.json.enabled"`, `CONSOLE_FORMAT_PROPERTY = "logging.structured.format.console"`, `PROPERTY_SOURCE_NAME = "javalibsLoggingDefaults"`.

**Vì sao cần:** để bật JSON log, người dùng chỉ phải viết
`javalibs.logging.json.enabled: true` thay vì nhớ tên class đầy đủ.
`EnvironmentPostProcessor` chạy **trước** `LoggingApplicationListener`, nên
property kịp có hiệu lực khi logging system khởi tạo.

- [ ] **Step 1: Viết test đỏ**

Tạo `javalibs-logging/javalibs-logging-logback/src/test/java/io/javalibs/logging/logback/JavalibsLoggingEnvironmentPostProcessorTest.java`:

```java
package io.javalibs.logging.logback;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class JavalibsLoggingEnvironmentPostProcessorTest {

    private final JavalibsLoggingEnvironmentPostProcessor processor =
            new JavalibsLoggingEnvironmentPostProcessor();

    private static ConfigurableEnvironment environmentWith(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        return environment;
    }

    @Test
    void pointsStructuredLoggingAtTheJavalibsFormatterWhenEnabled() {
        ConfigurableEnvironment environment =
                environmentWith(Map.of("javalibs.logging.json.enabled", "true"));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("logging.structured.format.console"))
                .isEqualTo(JavalibsJsonLogFormatter.class.getName());
    }

    @Test
    void staysOutOfTheWayWhenNotEnabled() {
        ConfigurableEnvironment environment = environmentWith(Map.of());

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("logging.structured.format.console")).isNull();
    }

    @Test
    void neverOverridesAnExplicitApplicationChoice() {
        ConfigurableEnvironment environment = environmentWith(Map.of(
                "javalibs.logging.json.enabled", "true",
                "logging.structured.format.console", "ecs"));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("ecs");
    }

    @Test
    void leavesFileLoggingAlone() {
        ConfigurableEnvironment environment =
                environmentWith(Map.of("javalibs.logging.json.enabled", "true"));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("logging.structured.format.file")).isNull();
    }

    @Test
    void isRegisteredForSpringBootToPickUp() {
        assertThat(getClass().getClassLoader().getResource("META-INF/spring.factories")).isNotNull();
    }
}
```

- [ ] **Step 2: Chạy test để chắc chắn nó đỏ**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn -pl javalibs-logging/javalibs-logging-logback test
```

Kỳ vọng: **FAIL**, `cannot find symbol: class JavalibsLoggingEnvironmentPostProcessor`.

- [ ] **Step 3: Viết implementation**

Tạo `javalibs-logging/javalibs-logging-logback/src/main/java/io/javalibs/logging/logback/JavalibsLoggingEnvironmentPostProcessor.java`:

```java
package io.javalibs.logging.logback;

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Turns {@code javalibs.logging.json.enabled=true} into the structured logging
 * property Spring Boot actually reads, so applications never have to spell out
 * the formatter class name.
 *
 * <p>Environment post-processors run before {@code LoggingApplicationListener},
 * which is what makes this work: by the time the logging system is initialised
 * the property is already in place.</p>
 *
 * <p>Only the console target is touched. Console output is what container
 * runtimes collect; writing JSON to a file additionally implies decisions about
 * rotation and disk usage that belong to the application, not to a library.</p>
 */
public class JavalibsLoggingEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String ENABLED_PROPERTY = "javalibs.logging.json.enabled";

    static final String CONSOLE_FORMAT_PROPERTY = "logging.structured.format.console";

    static final String PROPERTY_SOURCE_NAME = "javalibsLoggingDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!Boolean.TRUE.equals(environment.getProperty(ENABLED_PROPERTY, Boolean.class, Boolean.FALSE))) {
            return;
        }
        if (environment.getProperty(CONSOLE_FORMAT_PROPERTY) != null) {
            return;
        }
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME,
                Map.of(CONSOLE_FORMAT_PROPERTY, JavalibsJsonLogFormatter.class.getName())));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
```

Tạo `javalibs-logging/javalibs-logging-logback/src/main/resources/META-INF/spring.factories`:

```
org.springframework.boot.env.EnvironmentPostProcessor=\
io.javalibs.logging.logback.JavalibsLoggingEnvironmentPostProcessor
```

- [ ] **Step 4: Chạy test để chắc chắn nó xanh**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn -pl javalibs-logging/javalibs-logging-logback test
```

Kỳ vọng: **PASS**, `Tests run: 35, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

```bash
git add javalibs-logging/javalibs-logging-logback
git commit -m "feat(logging): javalibs.logging.json.enabled tự trỏ structured logging vào formatter"
```

---

### Task 11: Module `-spring` + `LogContext`

**Files:**
- Create: `javalibs-logging/javalibs-logging-spring/pom.xml`
- Modify: `javalibs-logging/pom.xml` (thêm `<module>javalibs-logging-spring</module>`)
- Create: `javalibs-logging/javalibs-logging-spring/src/main/java/io/javalibs/logging/spring/LogContext.java`
- Test: `javalibs-logging/javalibs-logging-spring/src/test/java/io/javalibs/logging/spring/LogContextTest.java`

**Interfaces:**
- Consumes: `LogFields.MDC_TAGS` (Task 1).
- Produces: `LogContext` — `static LogContext.Scope tags(String... tags)`, `static LogContext.Scope put(String key, String value)`; lớp lồng `LogContext.Scope implements AutoCloseable` khôi phục giá trị MDC trước đó khi `close()`.

- [ ] **Step 1: Tạo pom và đăng ký module**

Tạo `javalibs-logging/javalibs-logging-spring/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>io.javalibs</groupId>
    <artifactId>javalibs-logging</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>..</relativePath>
  </parent>

  <artifactId>javalibs-logging-spring</artifactId>

  <name>javalibs :: logging :: spring</name>
  <description>
    Spring integration for javalibs structured logging: an HTTP access log
    filter that publishes the request/response subtrees and the request-scoped
    LogContext helper.
  </description>

  <dependencies>
    <dependency>
      <groupId>io.javalibs</groupId>
      <artifactId>javalibs-logging-core</artifactId>
      <version>${project.version}</version>
    </dependency>

    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-core</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot</artifactId>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-api</artifactId>
    </dependency>
    <dependency>
      <groupId>jakarta.servlet</groupId>
      <artifactId>jakarta.servlet-api</artifactId>
      <scope>provided</scope>
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

`spring-boot` có mặt vì Task 13 dùng `JsonParserFactory` để phân tích body JSON.

Trong `javalibs-logging/pom.xml`, đổi khối `<modules>` thành:

```xml
  <modules>
    <module>javalibs-logging-core</module>
    <module>javalibs-logging-logback</module>
    <module>javalibs-logging-spring</module>
  </modules>
```

- [ ] **Step 2: Viết test đỏ**

Tạo `javalibs-logging/javalibs-logging-spring/src/test/java/io/javalibs/logging/spring/LogContextTest.java`:

```java
package io.javalibs.logging.spring;

import io.javalibs.logging.LogFields;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class LogContextTest {

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    @Test
    void publishesTagsIntoTheMdcAndRemovesThemOnClose() {
        try (LogContext.Scope scope = LogContext.tags("auth", "login")) {
            assertThat(MDC.get(LogFields.MDC_TAGS)).isEqualTo("auth,login");
        }

        assertThat(MDC.get(LogFields.MDC_TAGS)).isNull();
    }

    @Test
    void nestedScopesRestoreTheOuterValueRatherThanClearingIt() {
        try (LogContext.Scope outer = LogContext.tags("auth")) {
            try (LogContext.Scope inner = LogContext.tags("login")) {
                assertThat(MDC.get(LogFields.MDC_TAGS)).isEqualTo("auth,login");
            }
            assertThat(MDC.get(LogFields.MDC_TAGS)).isEqualTo("auth");
        }

        assertThat(MDC.get(LogFields.MDC_TAGS)).isNull();
    }

    @Test
    void ignoresNullAndBlankTagsAndDeduplicates() {
        try (LogContext.Scope scope = LogContext.tags("auth", null, "  ", "auth", " login ")) {
            assertThat(MDC.get(LogFields.MDC_TAGS)).isEqualTo("auth,login");
        }
    }

    @Test
    void putStoresAndRestoresAnArbitraryKey() {
        MDC.put("orderId", "ord-1");

        try (LogContext.Scope scope = LogContext.put("orderId", "ord-2")) {
            assertThat(MDC.get("orderId")).isEqualTo("ord-2");
        }

        assertThat(MDC.get("orderId")).isEqualTo("ord-1");
    }

    @Test
    void putWithNullValueRemovesTheKeyForTheDurationOfTheScope() {
        MDC.put("orderId", "ord-1");

        try (LogContext.Scope scope = LogContext.put("orderId", null)) {
            assertThat(MDC.get("orderId")).isNull();
        }

        assertThat(MDC.get("orderId")).isEqualTo("ord-1");
    }
}
```

- [ ] **Step 3: Chạy test để chắc chắn nó đỏ**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn -pl javalibs-logging/javalibs-logging-spring -am test
```

Kỳ vọng: **FAIL**, `cannot find symbol: class LogContext`.

- [ ] **Step 4: Viết implementation**

Tạo `javalibs-logging/javalibs-logging-spring/src/main/java/io/javalibs/logging/spring/LogContext.java`:

```java
package io.javalibs.logging.spring;

import java.util.LinkedHashSet;
import java.util.Set;

import io.javalibs.logging.LogFields;
import org.slf4j.MDC;

/**
 * Attaches tags and metadata to every log line emitted inside a scope.
 *
 * <p>Use it for facts that belong to a whole unit of work rather than to a
 * single line:</p>
 *
 * <pre>
 * try (LogContext.Scope scope = LogContext.tags("checkout")) {
 *     log.info("Cart validated");   // carries tag "checkout"
 *     charge(order);                // so does everything it logs
 * }
 * </pre>
 *
 * <p>For a single line prefer the standard SLF4J fluent API —
 * {@code log.atInfo().addKeyValue("orderId", id).log("...")} — which the
 * formatter renders into {@code metadata} without any javalibs-specific
 * call.</p>
 *
 * <p>Closing a scope restores whatever the entry held before, rather than
 * clearing it, so nested scopes and thread-pool reuse cannot leak or lose
 * context.</p>
 */
public final class LogContext {

    private LogContext() {
        // static utility
    }

    /**
     * Adds tags for the duration of the returned scope, merging them with any
     * tags already in scope.
     *
     * @param tags the tags to add; {@code null} and blank entries are ignored
     * @return a scope restoring the previous tags when closed
     */
    public static Scope tags(String... tags) {
        String previous = MDC.get(LogFields.MDC_TAGS);
        Set<String> merged = new LinkedHashSet<>();
        addAll(merged, previous);
        if (tags != null) {
            for (String tag : tags) {
                add(merged, tag);
            }
        }
        MDC.put(LogFields.MDC_TAGS, String.join(",", merged));
        return new Scope(LogFields.MDC_TAGS, previous);
    }

    /**
     * Sets an MDC entry for the duration of the returned scope. The formatter
     * renders unclaimed MDC entries into the {@code metadata} object.
     *
     * @param key   the MDC key, must not be {@code null}
     * @param value the value; {@code null} removes the entry within the scope
     * @return a scope restoring the previous value when closed
     */
    public static Scope put(String key, String value) {
        String previous = MDC.get(key);
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
        return new Scope(key, previous);
    }

    private static void addAll(Set<String> target, String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isBlank()) {
            return;
        }
        for (String tag : commaSeparated.split(",")) {
            add(target, tag);
        }
    }

    private static void add(Set<String> target, String tag) {
        if (tag == null) {
            return;
        }
        String trimmed = tag.trim();
        if (!trimmed.isEmpty()) {
            target.add(trimmed);
        }
    }

    /** Restores the MDC entry it replaced. */
    public static final class Scope implements AutoCloseable {

        private final String key;
        private final String previous;

        private Scope(String key, String previous) {
            this.key = key;
            this.previous = previous;
        }

        @Override
        public void close() {
            if (previous == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, previous);
            }
        }
    }
}
```

- [ ] **Step 5: Chạy test để chắc chắn nó xanh**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn -pl javalibs-logging/javalibs-logging-spring -am test
```

Kỳ vọng: **PASS**, `Tests run: 5, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit**

```bash
git add javalibs-logging
git commit -m "feat(logging): module -spring + LogContext gắn tag/metadata theo phạm vi"
```

---

### Task 12: `HttpAccessLogFilter` — MDC và dòng access-log

**Files:**
- Create: `javalibs-logging/javalibs-logging-spring/src/main/java/io/javalibs/logging/spring/PrincipalResolver.java`
- Create: `javalibs-logging/javalibs-logging-spring/src/main/java/io/javalibs/logging/spring/AccessLogSettings.java`
- Create: `javalibs-logging/javalibs-logging-spring/src/main/java/io/javalibs/logging/spring/HttpAccessLogFilter.java`
- Test: `javalibs-logging/javalibs-logging-spring/src/test/java/io/javalibs/logging/spring/AccessLogRecorder.java`
- Test: `javalibs-logging/javalibs-logging-spring/src/test/java/io/javalibs/logging/spring/HttpAccessLogFilterTest.java`

**Interfaces:**
- Consumes: `ClientIpResolver`, `SensitiveDataMasker`, `SensitiveKeys`, `HttpRequestLog`, `HttpResponseLog`, `LogFields` (Task 1–4).
- Produces:
  - `PrincipalResolver` — interface hàm đơn `String resolve(HttpServletRequest request)`, hằng số `PrincipalResolver DEFAULT`.
  - `AccessLogSettings` — record `(boolean includeHeaders, List<String> includedHeaders, boolean includeBody, int maxBodyLength, List<String> excludedPaths, boolean trustProxy, long slowThresholdMs)` với `static AccessLogSettings defaults()` và các hằng số `DEFAULT_INCLUDED_HEADERS`, `DEFAULT_EXCLUDED_PATHS`, `DEFAULT_MAX_BODY_LENGTH`.
  - `HttpAccessLogFilter extends OncePerRequestFilter` — constructor `(AccessLogSettings settings, PrincipalResolver principalResolver, SensitiveDataMasker masker)`.
  - `AccessLogRecorder` (test fixture) — `static AccessLogRecorder attach()`, `List<ILoggingEvent> events()`, `ILoggingEvent single()`, `Object keyValue(String key)`, `void detach()`. Task 13 dùng lại.

**Ghi chú thứ tự filter:** filter được đăng ký ở order `LOWEST_PRECEDENCE - 10`
(Task 14), tức chạy **sau** chuỗi filter của Spring Security (order mặc định
`-100`). Nhờ vậy `request.getUserPrincipal()` đã có giá trị ngay khi filter bắt
đầu, và status ghi được là status cuối cùng.

- [ ] **Step 1: Viết test fixture ghi lại log**

Tạo `javalibs-logging/javalibs-logging-spring/src/test/java/io/javalibs/logging/spring/AccessLogRecorder.java`:

```java
package io.javalibs.logging.spring;

import java.util.List;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;

/** Captures the events the access log filter emits, so tests can assert on them. */
final class AccessLogRecorder {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender;

    private AccessLogRecorder(Logger logger, ListAppender<ILoggingEvent> appender) {
        this.logger = logger;
        this.appender = appender;
    }

    static AccessLogRecorder attach() {
        Logger logger = (Logger) LoggerFactory.getLogger(HttpAccessLogFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return new AccessLogRecorder(logger, appender);
    }

    void detach() {
        logger.detachAppender(appender);
        appender.stop();
    }

    List<ILoggingEvent> events() {
        return appender.list;
    }

    ILoggingEvent single() {
        List<ILoggingEvent> events = events();
        if (events.size() != 1) {
            throw new AssertionError("expected exactly one access log event but got " + events.size());
        }
        return events.get(0);
    }

    Object keyValue(String key) {
        List<KeyValuePair> pairs = single().getKeyValuePairs();
        if (pairs == null) {
            return null;
        }
        return pairs.stream()
                .filter(pair -> key.equals(pair.key))
                .map(pair -> pair.value)
                .findFirst()
                .orElse(null);
    }
}
```

- [ ] **Step 2: Viết test đỏ cho filter**

Tạo `javalibs-logging/javalibs-logging-spring/src/test/java/io/javalibs/logging/spring/HttpAccessLogFilterTest.java`:

```java
package io.javalibs.logging.spring;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import ch.qos.logback.classic.Level;
import io.javalibs.logging.LogFields;
import io.javalibs.logging.SensitiveDataMasker;
import io.javalibs.logging.SensitiveKeys;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class HttpAccessLogFilterTest {

    private AccessLogRecorder recorder;

    @BeforeEach
    void attachRecorder() {
        recorder = AccessLogRecorder.attach();
    }

    @AfterEach
    void cleanUp() {
        recorder.detach();
        MDC.clear();
    }

    private static HttpAccessLogFilter filter(AccessLogSettings settings) {
        return new HttpAccessLogFilter(settings, PrincipalResolver.DEFAULT,
                new SensitiveDataMasker(SensitiveKeys.defaults(), SensitiveDataMasker.DEFAULT_MASK));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requestMap() {
        return (Map<String, Object>) recorder.keyValue(LogFields.REQUEST);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> responseMap() {
        return (Map<String, Object>) recorder.keyValue(LogFields.RESPONSE);
    }

    @Test
    void logsMethodEndpointStatusAndLatency() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        filter(AccessLogSettings.defaults()).doFilter(request, response, (req, res) -> {
        });

        assertThat(requestMap())
                .containsEntry(LogFields.METHOD, "POST")
                .containsEntry(LogFields.ENDPOINT, "/api/login");
        assertThat(responseMap()).containsEntry(LogFields.STATUS, 200);
        assertThat((Long) responseMap().get(LogFields.LATENCY_MS)).isNotNegative();
        assertThat(recorder.single().getFormattedMessage()).startsWith("POST /api/login 200 ");
    }

    @Test
    void putsUserIdAndClientIpIntoTheMdcForTheDurationOfTheRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
        request.setUserPrincipal(() -> "user_12345");
        request.setRemoteAddr("192.168.1.10");
        AtomicReference<String> userIdDuringChain = new AtomicReference<>();
        AtomicReference<String> ipDuringChain = new AtomicReference<>();

        filter(AccessLogSettings.defaults()).doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            userIdDuringChain.set(MDC.get(LogFields.MDC_USER_ID));
            ipDuringChain.set(MDC.get(LogFields.MDC_CLIENT_IP));
        });

        assertThat(userIdDuringChain.get()).isEqualTo("user_12345");
        assertThat(ipDuringChain.get()).isEqualTo("192.168.1.10");
        assertThat(MDC.get(LogFields.MDC_USER_ID)).isNull();
        assertThat(MDC.get(LogFields.MDC_CLIENT_IP)).isNull();
    }

    @Test
    void cleansTheMdcEvenWhenTheChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/boom");
        request.setRemoteAddr("192.168.1.10");

        assertThatExceptionOfType(ServletException.class).isThrownBy(() ->
                filter(AccessLogSettings.defaults()).doFilter(request, new MockHttpServletResponse(),
                        (req, res) -> {
                            throw new ServletException("boom");
                        }));

        assertThat(MDC.get(LogFields.MDC_CLIENT_IP)).isNull();
        assertThat(recorder.events()).hasSize(1);
    }

    @Test
    void ignoresSpoofedForwardedHeaderUnlessTheProxyIsTrusted() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        request.setRemoteAddr("192.168.1.10");
        AtomicReference<String> ip = new AtomicReference<>();

        filter(AccessLogSettings.defaults()).doFilter(request, new MockHttpServletResponse(),
                (req, res) -> ip.set(MDC.get(LogFields.MDC_CLIENT_IP)));

        assertThat(ip.get()).isEqualTo("192.168.1.10");
    }

    @Test
    void masksSecretsInTheQueryString() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/verify");
        request.setQueryString("user=alice&token=abc123");

        filter(AccessLogSettings.defaults()).doFilter(request, new MockHttpServletResponse(), (req, res) -> {
        });

        assertThat(requestMap()).containsEntry(LogFields.ENDPOINT, "/api/verify?user=alice&token=********");
    }

    @Test
    void skipsExcludedPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");

        filter(AccessLogSettings.defaults()).doFilter(request, new MockHttpServletResponse(), (req, res) -> {
        });

        assertThat(recorder.events()).isEmpty();
    }

    @Test
    void raisesTheLevelToWarnForSlowRequests() throws Exception {
        AccessLogSettings slowIsOneMillisecond = new AccessLogSettings(
                false, null, false, 2048, null, false, 1L);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/slow");

        filter(slowIsOneMillisecond).doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            try {
                Thread.sleep(5L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });

        assertThat(recorder.single().getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    void omitsHeadersUnlessTheyAreExplicitlyEnabled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
        request.addHeader("User-Agent", "Mozilla/5.0");

        filter(AccessLogSettings.defaults()).doFilter(request, new MockHttpServletResponse(), (req, res) -> {
        });

        assertThat(requestMap()).doesNotContainKey(LogFields.HEADERS);
    }

    @Test
    void includesOnlyAllowlistedHeadersWhenEnabled() throws Exception {
        AccessLogSettings withHeaders = new AccessLogSettings(
                true, null, false, 2048, null, false, 0L);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
        request.addHeader("User-Agent", "Mozilla/5.0");
        request.addHeader("Cookie", "session=secret");

        filter(withHeaders).doFilter(request, new MockHttpServletResponse(), (req, res) -> {
        });

        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) requestMap().get(LogFields.HEADERS);
        assertThat(headers).containsEntry("User-Agent", "Mozilla/5.0").doesNotContainKey("Cookie");
    }
}
```

- [ ] **Step 3: Chạy test để chắc chắn nó đỏ**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn -pl javalibs-logging/javalibs-logging-spring test
```

Kỳ vọng: **FAIL**, `cannot find symbol: class HttpAccessLogFilter`.

- [ ] **Step 4: Viết `PrincipalResolver`**

Tạo `javalibs-logging/javalibs-logging-spring/src/main/java/io/javalibs/logging/spring/PrincipalResolver.java`:

```java
package io.javalibs.logging.spring;

import java.security.Principal;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Supplies the value of the {@code user_id} log field for a request.
 *
 * <p>Kept as a one-method interface so this module depends on neither
 * javalibs-security nor Spring Security. An application that keeps identity
 * somewhere else — a {@code UserContext}, a custom header, a tenant-qualified
 * id — registers its own bean and everything else keeps working.</p>
 */
@FunctionalInterface
public interface PrincipalResolver {

    /**
     * Reads {@link HttpServletRequest#getUserPrincipal()}, which servlet
     * containers and Spring Security both populate.
     */
    PrincipalResolver DEFAULT = request -> {
        Principal principal = request.getUserPrincipal();
        return (principal != null) ? principal.getName() : null;
    };

    /**
     * Resolves the identifier of the authenticated caller.
     *
     * @param request the current request
     * @return the user id, or {@code null} when the caller is anonymous
     */
    String resolve(HttpServletRequest request);
}
```

- [ ] **Step 5: Viết `AccessLogSettings`**

Tạo `javalibs-logging/javalibs-logging-spring/src/main/java/io/javalibs/logging/spring/AccessLogSettings.java`:

```java
package io.javalibs.logging.spring;

import java.util.List;

/**
 * Settings of the HTTP access log filter.
 *
 * <p>Body and header capture default to off. Recording payloads is a decision
 * about personal data, and it should be one an application makes deliberately
 * rather than one it inherits from a library.</p>
 *
 * @param includeHeaders   whether request headers are logged
 * @param includedHeaders  allowlist of header names to log; {@code null} or
 *                         empty falls back to {@link #DEFAULT_INCLUDED_HEADERS}
 * @param includeBody      whether request and response bodies are logged
 * @param maxBodyLength    cap on characters kept from each body; values below
 *                         one fall back to {@link #DEFAULT_MAX_BODY_LENGTH}
 * @param excludedPaths    Ant patterns never logged; {@code null} falls back to
 *                         {@link #DEFAULT_EXCLUDED_PATHS}
 * @param trustProxy       whether {@code X-Forwarded-For} may be believed
 * @param slowThresholdMs  latency at or above which the event is logged at
 *                         {@code WARN}; {@code 0} disables the promotion
 */
public record AccessLogSettings(
        boolean includeHeaders,
        List<String> includedHeaders,
        boolean includeBody,
        int maxBodyLength,
        List<String> excludedPaths,
        boolean trustProxy,
        long slowThresholdMs) {

    /**
     * Headers logged by default — deliberately an allowlist, because the
     * dangerous headers ({@code Authorization}, {@code Cookie},
     * {@code X-Api-Key}) are exactly the ones a denylist tends to miss.
     */
    public static final List<String> DEFAULT_INCLUDED_HEADERS =
            List.of("Content-Type", "User-Agent", "Accept");

    /** Paths excluded from the access log by default. */
    public static final List<String> DEFAULT_EXCLUDED_PATHS = List.of("/actuator/**");

    /** Default cap on characters kept from one body. */
    public static final int DEFAULT_MAX_BODY_LENGTH = 2048;

    /** Canonical constructor applying the documented fallbacks. */
    public AccessLogSettings {
        includedHeaders = (includedHeaders == null || includedHeaders.isEmpty())
                ? DEFAULT_INCLUDED_HEADERS : List.copyOf(includedHeaders);
        excludedPaths = (excludedPaths == null) ? DEFAULT_EXCLUDED_PATHS : List.copyOf(excludedPaths);
        maxBodyLength = (maxBodyLength > 0) ? maxBodyLength : DEFAULT_MAX_BODY_LENGTH;
        slowThresholdMs = Math.max(slowThresholdMs, 0L);
    }

    /**
     * Returns the conservative defaults: no headers, no bodies, no proxy trust.
     *
     * @return the default settings
     */
    public static AccessLogSettings defaults() {
        return new AccessLogSettings(false, DEFAULT_INCLUDED_HEADERS, false,
                DEFAULT_MAX_BODY_LENGTH, DEFAULT_EXCLUDED_PATHS, false, 0L);
    }
}
```

- [ ] **Step 6: Viết `HttpAccessLogFilter`**

Tạo `javalibs-logging/javalibs-logging-spring/src/main/java/io/javalibs/logging/spring/HttpAccessLogFilter.java`:

```java
package io.javalibs.logging.spring;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import io.javalibs.logging.ClientIpResolver;
import io.javalibs.logging.HttpRequestLog;
import io.javalibs.logging.HttpResponseLog;
import io.javalibs.logging.LogFields;
import io.javalibs.logging.SensitiveDataMasker;
import io.javalibs.logging.SensitiveKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Emits one structured access log event per HTTP request and publishes the
 * request-scoped {@code userId} / {@code clientIp} MDC entries that every other
 * log line in the request picks up for free.
 *
 * <p>The {@code request} and {@code response} subtrees travel as SLF4J
 * key-value pairs, which the javalibs formatter renders as nested JSON objects.
 * The message itself stays human readable ({@code POST /api/login 200 152ms}),
 * so an application that has not switched JSON logging on still gets a sensible
 * plain-text line rather than an empty one.</p>
 */
public class HttpAccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpAccessLogFilter.class);

    private final AccessLogSettings settings;
    private final PrincipalResolver principalResolver;
    private final SensitiveDataMasker masker;
    private final ClientIpResolver clientIpResolver;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * Creates the filter.
     *
     * @param settings          filter settings; {@code null} uses
     *                          {@link AccessLogSettings#defaults()}
     * @param principalResolver resolver for {@code user_id}; {@code null} uses
     *                          {@link PrincipalResolver#DEFAULT}
     * @param masker            masker for query strings and form bodies;
     *                          {@code null} uses the default key policy
     */
    public HttpAccessLogFilter(AccessLogSettings settings, PrincipalResolver principalResolver,
            SensitiveDataMasker masker) {
        this.settings = (settings != null) ? settings : AccessLogSettings.defaults();
        this.principalResolver = (principalResolver != null) ? principalResolver : PrincipalResolver.DEFAULT;
        this.masker = (masker != null) ? masker
                : new SensitiveDataMasker(SensitiveKeys.defaults(), SensitiveDataMasker.DEFAULT_MASK);
        this.clientIpResolver = new ClientIpResolver(this.settings.trustProxy());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return settings.excludedPaths().stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String previousUserId = MDC.get(LogFields.MDC_USER_ID);
        String previousClientIp = MDC.get(LogFields.MDC_CLIENT_IP);
        putOrRemove(LogFields.MDC_USER_ID, resolveUserId(request));
        putOrRemove(LogFields.MDC_CLIENT_IP,
                clientIpResolver.resolve(request::getHeader, request.getRemoteAddr()));

        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long latencyMs = (System.nanoTime() - startedAt) / 1_000_000L;
            try {
                logExchange(request, response, latencyMs);
            } catch (RuntimeException ex) {
                log.warn("Could not write the access log entry for {} {}",
                        request.getMethod(), request.getRequestURI(), ex);
            } finally {
                putOrRemove(LogFields.MDC_USER_ID, previousUserId);
                putOrRemove(LogFields.MDC_CLIENT_IP, previousClientIp);
            }
        }
    }

    private void logExchange(HttpServletRequest request, HttpServletResponse response, long latencyMs) {
        String endpoint = endpointOf(request);
        HttpRequestLog requestLog =
                new HttpRequestLog(request.getMethod(), endpoint, headersOf(request), null);
        HttpResponseLog responseLog = new HttpResponseLog(response.getStatus(), latencyMs, null);

        boolean slow = settings.slowThresholdMs() > 0 && latencyMs >= settings.slowThresholdMs();
        LoggingEventBuilder builder = slow ? log.atWarn() : log.atInfo();
        builder.addKeyValue(LogFields.REQUEST, requestLog.toMap())
                .addKeyValue(LogFields.RESPONSE, responseLog.toMap())
                .log("{} {} {} {}ms", request.getMethod(), endpoint, response.getStatus(), latencyMs);
    }

    private String endpointOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        return (query == null || query.isEmpty()) ? uri : uri + "?" + masker.maskFormEncoded(query);
    }

    private Map<String, String> headersOf(HttpServletRequest request) {
        if (!settings.includeHeaders()) {
            return Map.of();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        for (String name : settings.includedHeaders()) {
            String value = request.getHeader(name);
            if (value != null) {
                headers.put(name, value);
            }
        }
        return headers;
    }

    private String resolveUserId(HttpServletRequest request) {
        try {
            return principalResolver.resolve(request);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static void putOrRemove(String key, String value) {
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }
}
```

- [ ] **Step 7: Chạy test để chắc chắn nó xanh**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn -pl javalibs-logging/javalibs-logging-spring test
```

Kỳ vọng: **PASS**, `Tests run: 14, Failures: 0, Errors: 0`.

- [ ] **Step 8: Commit**

```bash
git add javalibs-logging/javalibs-logging-spring
git commit -m "feat(logging): HttpAccessLogFilter phát request/response + đặt userId/clientIp vào MDC"
```

---

### Task 13: Filter — ghi body request/response

**Files:**
- Modify: `javalibs-logging/javalibs-logging-spring/src/main/java/io/javalibs/logging/spring/HttpAccessLogFilter.java`
- Test: `javalibs-logging/javalibs-logging-spring/src/test/java/io/javalibs/logging/spring/HttpAccessLogFilterBodyTest.java`

**Interfaces:**
- Consumes: `AccessLogRecorder` fixture (Task 12), `SensitiveDataMasker.maskFormEncoded` (Task 2).
- Produces: không có API mới.

**Hai cái bẫy phải tránh:**
1. `ContentCachingResponseWrapper` giữ body lại trong bộ nhớ; **không gọi
   `copyBodyToResponse()` thì client nhận response rỗng.** Phải gọi trong
   `finally`, và nuốt `IOException` tại đó — ném từ `finally` sẽ che mất
   exception thật của request.
2. Body JSON được **parse thành object** để khớp schema trong ảnh; cắt theo
   `max-body-length` **trước** khi parse, nên body dài sẽ parse hỏng — khi đó giữ
   nguyên chuỗi đã cắt thay vì bỏ hẳn field.

- [ ] **Step 1: Viết test đỏ**

Tạo `javalibs-logging/javalibs-logging-spring/src/test/java/io/javalibs/logging/spring/HttpAccessLogFilterBodyTest.java`:

```java
package io.javalibs.logging.spring;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import io.javalibs.logging.LogFields;
import io.javalibs.logging.SensitiveDataMasker;
import io.javalibs.logging.SensitiveKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class HttpAccessLogFilterBodyTest {

    private AccessLogRecorder recorder;

    @BeforeEach
    void attachRecorder() {
        recorder = AccessLogRecorder.attach();
    }

    @AfterEach
    void cleanUp() {
        recorder.detach();
        MDC.clear();
    }

    private static HttpAccessLogFilter filterWithBodies(int maxBodyLength) {
        AccessLogSettings settings =
                new AccessLogSettings(false, null, true, maxBodyLength, null, false, 0L);
        return new HttpAccessLogFilter(settings, PrincipalResolver.DEFAULT,
                new SensitiveDataMasker(SensitiveKeys.defaults(), SensitiveDataMasker.DEFAULT_MASK));
    }

    @SuppressWarnings("unchecked")
    private Object requestBody() {
        return ((Map<String, Object>) recorder.keyValue(LogFields.REQUEST)).get(LogFields.BODY);
    }

    @SuppressWarnings("unchecked")
    private Object responseBody() {
        return ((Map<String, Object>) recorder.keyValue(LogFields.RESPONSE)).get(LogFields.RESPONSE_BODY);
    }

    @Test
    void parsesAJsonRequestBodyIntoAnObject() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/login");
        request.setContentType("application/json");
        request.setContent("{\"email\":\"user@example.com\",\"password\":\"hunter2\"}"
                .getBytes(StandardCharsets.UTF_8));

        filterWithBodies(2048).doFilter(request, new MockHttpServletResponse(),
                (req, res) -> req.getInputStream().readAllBytes());

        assertThat(requestBody()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) requestBody();
        assertThat(body).containsEntry("email", "user@example.com").containsEntry("password", "hunter2");
    }

    @Test
    void masksSecretsInAFormEncodedBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/login");
        request.setContentType("application/x-www-form-urlencoded");
        request.setContent("user=alice&password=hunter2".getBytes(StandardCharsets.UTF_8));

        filterWithBodies(2048).doFilter(request, new MockHttpServletResponse(),
                (req, res) -> req.getInputStream().readAllBytes());

        assertThat(requestBody()).isEqualTo("user=alice&password=********");
    }

    @Test
    void capturesTheResponseBodyAndStillDeliversItToTheClient() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filterWithBodies(2048).doFilter(request, response, (req, res) -> {
            res.setContentType("application/json");
            res.getWriter().write("{\"success\":true}");
        });

        assertThat(responseBody()).isInstanceOf(Map.class);
        assertThat(response.getContentAsString()).isEqualTo("{\"success\":true}");
    }

    @Test
    void neverLogsBinaryContent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/upload");
        request.setContentType("application/octet-stream");
        request.setContent(new byte[] {0x00, 0x01, 0x02, 0x03});

        filterWithBodies(2048).doFilter(request, new MockHttpServletResponse(),
                (req, res) -> req.getInputStream().readAllBytes());

        assertThat(requestBody()).isNull();
    }

    @Test
    void keepsATruncatedJsonBodyAsAStringRatherThanDroppingIt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setContentType("application/json");
        request.setContent("{\"note\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}".getBytes(StandardCharsets.UTF_8));

        filterWithBodies(12).doFilter(request, new MockHttpServletResponse(),
                (req, res) -> req.getInputStream().readAllBytes());

        assertThat(requestBody()).isInstanceOf(String.class);
        assertThat((String) requestBody()).hasSizeLessThanOrEqualTo(12);
    }

    @Test
    void omitsBodiesEntirelyWhenCaptureIsOff() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/login");
        request.setContentType("application/json");
        request.setContent("{\"password\":\"hunter2\"}".getBytes(StandardCharsets.UTF_8));

        HttpAccessLogFilter filter = new HttpAccessLogFilter(AccessLogSettings.defaults(),
                PrincipalResolver.DEFAULT, null);
        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> req.getInputStream().readAllBytes());

        @SuppressWarnings("unchecked")
        Map<String, Object> requestMap = (Map<String, Object>) recorder.keyValue(LogFields.REQUEST);
        assertThat(requestMap).doesNotContainKey(LogFields.BODY);
    }

    @Test
    void stillDeliversTheResponseWhenTheChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/boom");
        MockHttpServletResponse response = new MockHttpServletResponse();

        List<Throwable> thrown = new java.util.ArrayList<>();
        try {
            filterWithBodies(2048).doFilter(request, response, (req, res) -> {
                res.setContentType("application/json");
                res.getWriter().write("{\"partial\":true}");
                throw new IllegalStateException("boom");
            });
        } catch (Exception ex) {
            thrown.add(ex);
        }

        assertThat(thrown).hasSize(1);
        assertThat(response.getContentAsString()).isEqualTo("{\"partial\":true}");
    }
}
```

- [ ] **Step 2: Chạy test để chắc chắn nó đỏ**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn -pl javalibs-logging/javalibs-logging-spring test
```

Kỳ vọng: **FAIL** — `request.body` chưa được ghi (`null`).

- [ ] **Step 3: Bổ sung import và thay `doFilterInternal`**

Trong `HttpAccessLogFilter.java`, thêm import:

```java
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.boot.json.JsonParserFactory;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
```

Thay **toàn bộ** phương thức `doFilterInternal` bằng:

```java
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        HttpServletRequest requestToUse = request;
        HttpServletResponse responseToUse = response;
        if (settings.includeBody()) {
            requestToUse = (request instanceof ContentCachingRequestWrapper cached) ? cached
                    : new ContentCachingRequestWrapper(request, settings.maxBodyLength());
            responseToUse = (response instanceof ContentCachingResponseWrapper cached) ? cached
                    : new ContentCachingResponseWrapper(response);
        }

        String previousUserId = MDC.get(LogFields.MDC_USER_ID);
        String previousClientIp = MDC.get(LogFields.MDC_CLIENT_IP);
        putOrRemove(LogFields.MDC_USER_ID, resolveUserId(requestToUse));
        putOrRemove(LogFields.MDC_CLIENT_IP,
                clientIpResolver.resolve(requestToUse::getHeader, requestToUse.getRemoteAddr()));

        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(requestToUse, responseToUse);
        } finally {
            long latencyMs = (System.nanoTime() - startedAt) / 1_000_000L;
            try {
                logExchange(requestToUse, responseToUse, latencyMs);
            } catch (RuntimeException ex) {
                log.warn("Could not write the access log entry for {} {}",
                        request.getMethod(), request.getRequestURI(), ex);
            } finally {
                copyCachedBody(responseToUse);
                putOrRemove(LogFields.MDC_USER_ID, previousUserId);
                putOrRemove(LogFields.MDC_CLIENT_IP, previousClientIp);
            }
        }
    }
```

- [ ] **Step 4: Cho `logExchange` mang theo body**

Trong `logExchange`, thay hai dòng dựng `requestLog` / `responseLog` bằng:

```java
        HttpRequestLog requestLog = new HttpRequestLog(request.getMethod(), endpoint,
                headersOf(request), requestBodyOf(request));
        HttpResponseLog responseLog =
                new HttpResponseLog(response.getStatus(), latencyMs, responseBodyOf(response));
```

- [ ] **Step 5: Thêm các phương thức đọc body**

Thêm vào cuối class `HttpAccessLogFilter`:

```java
    private static void copyCachedBody(HttpServletResponse response) {
        if (response instanceof ContentCachingResponseWrapper wrapper) {
            try {
                wrapper.copyBodyToResponse();
            } catch (IOException ex) {
                log.warn("Could not copy the cached response body back to the client", ex);
            }
        }
    }

    private Object requestBodyOf(HttpServletRequest request) {
        if (!(request instanceof ContentCachingRequestWrapper wrapper)) {
            return null;
        }
        return bodyOf(wrapper.getContentAsByteArray(), wrapper.getContentType(),
                wrapper.getCharacterEncoding());
    }

    private Object responseBodyOf(HttpServletResponse response) {
        if (!(response instanceof ContentCachingResponseWrapper wrapper)) {
            return null;
        }
        return bodyOf(wrapper.getContentAsByteArray(), wrapper.getContentType(),
                wrapper.getCharacterEncoding());
    }

    /**
     * Renders a captured payload. JSON is parsed into a map so it nests properly
     * in the log record; form data is masked; anything binary is dropped
     * entirely rather than being turned into mojibake.
     */
    private Object bodyOf(byte[] content, String contentType, String encoding) {
        if (content == null || content.length == 0) {
            return null;
        }
        String type = (contentType != null) ? contentType.toLowerCase(Locale.ROOT) : "";
        if (!isTextual(type)) {
            return null;
        }
        String raw = new String(content, charsetOf(encoding));
        if (raw.length() > settings.maxBodyLength()) {
            raw = raw.substring(0, settings.maxBodyLength());
        }
        if (type.startsWith("application/json")) {
            try {
                return JsonParserFactory.getJsonParser().parseMap(raw);
            } catch (RuntimeException ex) {
                return raw;
            }
        }
        if (type.startsWith("application/x-www-form-urlencoded")) {
            return masker.maskFormEncoded(raw);
        }
        return raw;
    }

    private static boolean isTextual(String contentType) {
        return contentType.startsWith("application/json")
                || contentType.startsWith("application/x-www-form-urlencoded")
                || contentType.startsWith("text/");
    }

    private static Charset charsetOf(String encoding) {
        if (encoding == null) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding);
        } catch (RuntimeException ex) {
            return StandardCharsets.UTF_8;
        }
    }
```

- [ ] **Step 6: Chạy toàn bộ test của module**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn -pl javalibs-logging/javalibs-logging-spring test
```

Kỳ vọng: **PASS**, `Tests run: 21, Failures: 0, Errors: 0`. Lưu ý các test của Task 12 phải vẫn xanh — đặc biệt test dọn MDC khi chain ném exception.

- [ ] **Step 7: Commit**

```bash
git add javalibs-logging/javalibs-logging-spring
git commit -m "feat(logging): filter ghi body request/response, parse JSON và che form-urlencoded"
```

---

### Task 14: Module `-spring-boot-autoconfigure`

**Files:**
- Create: `javalibs-logging/javalibs-logging-spring-boot-autoconfigure/pom.xml`
- Modify: `javalibs-logging/pom.xml` (thêm module)
- Create: `.../src/main/java/io/javalibs/logging/autoconfigure/LoggingProperties.java`
- Create: `.../src/main/java/io/javalibs/logging/autoconfigure/AccessLogAutoConfiguration.java`
- Create: `.../src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Test: `.../src/test/java/io/javalibs/logging/autoconfigure/AccessLogAutoConfigurationTest.java`

(Đường dẫn đầy đủ của các file `.../` là `javalibs-logging/javalibs-logging-spring-boot-autoconfigure/`.)

**Interfaces:**
- Consumes: `AccessLogSettings`, `PrincipalResolver`, `HttpAccessLogFilter` (Task 12), `SensitiveKeys`, `SensitiveDataMasker` (Task 1–2).
- Produces:
  - `LoggingProperties` — record `@ConfigurationProperties("javalibs.logging")` với các thành phần lồng `Json`, `Masking`, `Stacktrace`, `Access`.
  - `AccessLogAutoConfiguration` — bean `javalibsPrincipalResolver()` và `javalibsHttpAccessLogFilter(...)` trả `FilterRegistrationBean<HttpAccessLogFilter>` order `LOWEST_PRECEDENCE - 10`.

- [ ] **Step 1: Tạo pom và đăng ký module**

Tạo `javalibs-logging/javalibs-logging-spring-boot-autoconfigure/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>io.javalibs</groupId>
    <artifactId>javalibs-logging</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>..</relativePath>
  </parent>

  <artifactId>javalibs-logging-spring-boot-autoconfigure</artifactId>

  <name>javalibs :: logging :: spring-boot-autoconfigure</name>
  <description>
    Spring Boot auto-configuration for javalibs structured logging: registers
    the HTTP access log filter under the javalibs.logging.* namespace.
  </description>

  <dependencies>
    <dependency>
      <groupId>io.javalibs</groupId>
      <artifactId>javalibs-logging-spring</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>io.javalibs</groupId>
      <artifactId>javalibs-logging-logback</artifactId>
      <version>${project.version}</version>
    </dependency>

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-autoconfigure</artifactId>
    </dependency>
    <dependency>
      <groupId>jakarta.servlet</groupId>
      <artifactId>jakarta.servlet-api</artifactId>
      <scope>provided</scope>
    </dependency>

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-configuration-processor</artifactId>
      <optional>true</optional>
    </dependency>

    <!-- Test -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>io.javalibs</groupId>
      <artifactId>javalibs-web-spring-boot-autoconfigure</artifactId>
      <version>${project.version}</version>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

`javalibs-web-spring-boot-autoconfigure` ở scope `test` là để Task 15 chứng minh
được filter cũ thực sự lùi khi filter mới có mặt.

Trong `javalibs-logging/pom.xml`, đổi khối `<modules>` thành:

```xml
  <modules>
    <module>javalibs-logging-core</module>
    <module>javalibs-logging-logback</module>
    <module>javalibs-logging-spring</module>
    <module>javalibs-logging-spring-boot-autoconfigure</module>
  </modules>
```

- [ ] **Step 2: Viết test đỏ**

Tạo `javalibs-logging/javalibs-logging-spring-boot-autoconfigure/src/test/java/io/javalibs/logging/autoconfigure/AccessLogAutoConfigurationTest.java`:

```java
package io.javalibs.logging.autoconfigure;

import io.javalibs.logging.spring.HttpAccessLogFilter;
import io.javalibs.logging.spring.PrincipalResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class AccessLogAutoConfigurationTest {

    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AccessLogAutoConfiguration.class));

    @Test
    void registersTheAccessLogFilterByDefault() {
        webRunner.run(context -> {
            assertThat(context).hasSingleBean(FilterRegistrationBean.class);
            assertThat(context).hasSingleBean(PrincipalResolver.class);
        });
    }

    @Test
    void backsOffWhenTheFeatureIsDisabled() {
        webRunner.withPropertyValues("javalibs.logging.access.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(FilterRegistrationBean.class));
    }

    @Test
    void doesNothingOutsideAServletApplication() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AccessLogAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(FilterRegistrationBean.class));
    }

    @Test
    void letsTheApplicationSupplyItsOwnPrincipalResolver() {
        webRunner.withUserConfiguration(CustomResolverConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(PrincipalResolver.class);
            assertThat(context.getBean(PrincipalResolver.class))
                    .isSameAs(context.getBean(CustomResolverConfiguration.class).resolver);
        });
    }

    @Test
    void bindsEveryDocumentedDefault() {
        webRunner.run(context -> {
            LoggingProperties properties = context.getBean(LoggingProperties.class);

            assertThat(properties.json().enabled()).isFalse();
            assertThat(properties.masking().enabled()).isTrue();
            assertThat(properties.masking().value()).isEqualTo("********");
            assertThat(properties.stacktrace().enabled()).isTrue();
            assertThat(properties.stacktrace().maxLength()).isEqualTo(4096);
            assertThat(properties.access().enabled()).isTrue();
            assertThat(properties.access().includeHeaders()).isFalse();
            assertThat(properties.access().includeBody()).isFalse();
            assertThat(properties.access().maxBodyLength()).isEqualTo(2048);
            assertThat(properties.access().trustProxy()).isFalse();
            assertThat(properties.access().slowThresholdMs()).isZero();
            assertThat(properties.access().excludedPaths()).containsExactly("/actuator/**");
            assertThat(properties.access().includedHeaders())
                    .containsExactly("Content-Type", "User-Agent", "Accept");
        });
    }

    @Test
    void registersTheFilterLateSoItSeesTheFinalStatus() {
        webRunner.run(context -> {
            FilterRegistrationBean<?> registration = context.getBean(FilterRegistrationBean.class);

            assertThat(registration.getFilter()).isInstanceOf(HttpAccessLogFilter.class);
            assertThat(registration.getOrder()).isEqualTo(Integer.MAX_VALUE - 10);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomResolverConfiguration {

        private final PrincipalResolver resolver = request -> "fixed-user";

        @Bean
        PrincipalResolver principalResolver() {
            return this.resolver;
        }
    }
}
```

- [ ] **Step 3: Chạy test để chắc chắn nó đỏ**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn -pl javalibs-logging/javalibs-logging-spring-boot-autoconfigure -am test
```

Kỳ vọng: **FAIL**, `cannot find symbol: class AccessLogAutoConfiguration`.

- [ ] **Step 4: Viết `LoggingProperties`**

Tạo `javalibs-logging/javalibs-logging-spring-boot-autoconfigure/src/main/java/io/javalibs/logging/autoconfigure/LoggingProperties.java`:

```java
package io.javalibs.logging.autoconfigure;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for javalibs structured logging, bound from the
 * {@code javalibs.logging.*} namespace.
 *
 * <p>These bindings drive the access log filter. The log formatter itself
 * cannot use them — it is created before the application context exists — and
 * reads the same properties straight from the {@code Environment}. Keep the two
 * sets of defaults in step: they describe one feature, not two.</p>
 *
 * @param json        JSON console output settings
 * @param service     value of the {@code service} field; defaults to
 *                    {@code spring.application.name}
 * @param host        value of the {@code host} field; defaults to the machine name
 * @param environment written to {@code metadata.env}
 * @param version     written to {@code metadata.version}
 * @param metadata    extra metadata attached to every log event
 * @param tags        tags attached to every log event
 * @param masking     sensitive data masking settings
 * @param stacktrace  stack trace rendering settings
 * @param access      HTTP access log settings
 */
@ConfigurationProperties("javalibs.logging")
public record LoggingProperties(
        @DefaultValue Json json,
        String service,
        String host,
        String environment,
        String version,
        @DefaultValue Map<String, String> metadata,
        @DefaultValue List<String> tags,
        @DefaultValue Masking masking,
        @DefaultValue Stacktrace stacktrace,
        @DefaultValue Access access) {

    /** Canonical constructor normalizing absent collections to empty ones. */
    public LoggingProperties {
        metadata = (metadata == null) ? Map.of() : Map.copyOf(metadata);
        tags = (tags == null) ? List.of() : List.copyOf(tags);
    }

    /**
     * JSON console output.
     *
     * @param enabled whether log output is rendered as javalibs JSON records
     *                (default {@code false}, so a developer console is not
     *                turned into JSON merely by adding the starter)
     */
    public record Json(@DefaultValue("false") boolean enabled) {
    }

    /**
     * Sensitive data masking.
     *
     * @param enabled whether masking is applied (default {@code true})
     * @param keys    extra field names to mask, <em>added to</em> the built-in
     *                denylist rather than replacing it
     * @param value   replacement text (default {@code ********})
     */
    public record Masking(
            @DefaultValue("true") boolean enabled,
            @DefaultValue List<String> keys,
            @DefaultValue("********") String value) {

        /** Canonical constructor normalizing absent key lists. */
        public Masking {
            keys = (keys == null) ? List.of() : List.copyOf(keys);
        }
    }

    /**
     * Stack trace rendering inside {@code errors[]}.
     *
     * @param enabled   whether stack traces are written (default {@code true})
     * @param maxLength cap on characters per stack trace (default {@code 4096})
     */
    public record Stacktrace(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("4096") int maxLength) {
    }

    /**
     * HTTP access log.
     *
     * @param enabled          whether the filter is registered (default {@code true})
     * @param includeHeaders   whether request headers are logged (default {@code false})
     * @param includedHeaders  allowlist of header names
     * @param includeBody      whether bodies are logged (default {@code false})
     * @param maxBodyLength    cap on characters per body (default {@code 2048})
     * @param excludedPaths    Ant patterns never logged (default {@code /actuator/**})
     * @param trustProxy       whether {@code X-Forwarded-For} may be believed
     *                         (default {@code false} — it is caller-controlled)
     * @param slowThresholdMs  latency at or above which the event is logged at
     *                         {@code WARN}; {@code 0} disables the promotion
     */
    public record Access(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("false") boolean includeHeaders,
            @DefaultValue({"Content-Type", "User-Agent", "Accept"}) List<String> includedHeaders,
            @DefaultValue("false") boolean includeBody,
            @DefaultValue("2048") int maxBodyLength,
            @DefaultValue("/actuator/**") List<String> excludedPaths,
            @DefaultValue("false") boolean trustProxy,
            @DefaultValue("0") long slowThresholdMs) {
    }
}
```

- [ ] **Step 5: Viết `AccessLogAutoConfiguration` và file imports**

Tạo `javalibs-logging/javalibs-logging-spring-boot-autoconfigure/src/main/java/io/javalibs/logging/autoconfigure/AccessLogAutoConfiguration.java`:

```java
package io.javalibs.logging.autoconfigure;

import io.javalibs.logging.SensitiveDataMasker;
import io.javalibs.logging.SensitiveKeys;
import io.javalibs.logging.spring.AccessLogSettings;
import io.javalibs.logging.spring.HttpAccessLogFilter;
import io.javalibs.logging.spring.PrincipalResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Registers the javalibs HTTP access log filter for servlet applications.
 *
 * <p>Enabled by default; switch it off with
 * {@code javalibs.logging.access.enabled=false}. The filter is ordered late in
 * the chain so that the status it records is the final one, and so that Spring
 * Security has already established the principal by the time {@code user_id} is
 * resolved.</p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(LoggingProperties.class)
@ConditionalOnProperty(prefix = "javalibs.logging.access", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class AccessLogAutoConfiguration {

    /**
     * Supplies the default {@code user_id} resolver, reading the servlet
     * principal.
     *
     * @return the default resolver
     */
    @Bean
    @ConditionalOnMissingBean
    public PrincipalResolver javalibsPrincipalResolver() {
        return PrincipalResolver.DEFAULT;
    }

    /**
     * Registers the access log filter configured from {@code javalibs.logging.*}.
     *
     * @param properties        the bound javalibs logging properties
     * @param principalResolver the resolver supplying {@code user_id}
     * @return the filter registration
     */
    @Bean
    @ConditionalOnMissingBean(HttpAccessLogFilter.class)
    public FilterRegistrationBean<HttpAccessLogFilter> javalibsHttpAccessLogFilter(
            LoggingProperties properties, PrincipalResolver principalResolver) {
        LoggingProperties.Access access = properties.access();
        AccessLogSettings settings = new AccessLogSettings(
                access.includeHeaders(), access.includedHeaders(), access.includeBody(),
                access.maxBodyLength(), access.excludedPaths(), access.trustProxy(),
                access.slowThresholdMs());

        LoggingProperties.Masking masking = properties.masking();
        SensitiveKeys keys = masking.enabled()
                ? SensitiveKeys.withAdditional(masking.keys()) : SensitiveKeys.none();
        SensitiveDataMasker masker = new SensitiveDataMasker(keys, masking.value());

        FilterRegistrationBean<HttpAccessLogFilter> registration = new FilterRegistrationBean<>(
                new HttpAccessLogFilter(settings, principalResolver, masker));
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
        return registration;
    }
}
```

Tạo `javalibs-logging/javalibs-logging-spring-boot-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
io.javalibs.logging.autoconfigure.AccessLogAutoConfiguration
```

- [ ] **Step 6: Chạy test để chắc chắn nó xanh**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn -pl javalibs-logging/javalibs-logging-spring-boot-autoconfigure -am test
```

Kỳ vọng: **PASS**, `Tests run: 6, Failures: 0, Errors: 0`.

- [ ] **Step 7: Commit**

```bash
git add javalibs-logging
git commit -m "feat(logging): auto-configuration đăng ký HttpAccessLogFilter + LoggingProperties"
```

---

### Task 15: `javalibs-web` lùi khi `javalibs-logging` có mặt

**Files:**
- Modify: `javalibs-web/javalibs-web-spring-boot-autoconfigure/src/main/java/io/javalibs/web/autoconfigure/RequestLoggingAutoConfiguration.java`
- Test: `javalibs-web/javalibs-web-spring-boot-autoconfigure/src/test/java/io/javalibs/web/autoconfigure/RequestLoggingAutoConfigurationTest.java`
- Test: `javalibs-logging/javalibs-logging-spring-boot-autoconfigure/src/test/java/io/javalibs/logging/autoconfigure/WebRequestLoggingBackOffTest.java`

**Interfaces:**
- Consumes: `HttpAccessLogFilter` (Task 12) — **chỉ theo tên class dạng chuỗi**, không tạo dependency Maven từ `javalibs-web` sang `javalibs-logging`.
- Produces: không có API mới.

**Vấn đề đang giải:** cả `RequestLoggingFilter` (cũ, một dòng text) lẫn
`HttpAccessLogFilter` (mới, JSON) đều đăng ký mặc định. Service dùng cả hai
starter sẽ log **hai lần mỗi request**. Cách khắc phục là để module cũ tự lùi
khi thấy class của module mới trên classpath — mặc định đúng, không cần ai nhớ
tắt property nào.

- [ ] **Step 1: Viết test đỏ ở phía `javalibs-logging`**

Tạo `javalibs-logging/javalibs-logging-spring-boot-autoconfigure/src/test/java/io/javalibs/logging/autoconfigure/WebRequestLoggingBackOffTest.java`:

```java
package io.javalibs.logging.autoconfigure;

import io.javalibs.logging.spring.HttpAccessLogFilter;
import io.javalibs.web.autoconfigure.RequestLoggingAutoConfiguration;
import io.javalibs.web.spring.RequestLoggingFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.assertj.core.api.Assertions.assertThat;

class WebRequestLoggingBackOffTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RequestLoggingAutoConfiguration.class, AccessLogAutoConfiguration.class));

    @Test
    void onlyTheStructuredAccessLogFilterIsRegistered() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(FilterRegistrationBean.class);
            assertThat(context.getBean(FilterRegistrationBean.class).getFilter())
                    .isInstanceOf(HttpAccessLogFilter.class);
        });
    }

    @Test
    void theOlderPlainTextFilterIsNotRegisteredAtAll() {
        runner.run(context -> assertThat(context).doesNotHaveBean(RequestLoggingFilter.class));
    }
}
```

- [ ] **Step 2: Chạy test để chắc chắn nó đỏ**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn -pl javalibs-logging/javalibs-logging-spring-boot-autoconfigure -am test
```

Kỳ vọng: **FAIL** với `expected a single bean but found 2` — đúng chính xác cái bug đang đi vá.

- [ ] **Step 3: Cho autoconfiguration cũ lùi**

Trong `javalibs-web/javalibs-web-spring-boot-autoconfigure/src/main/java/io/javalibs/web/autoconfigure/RequestLoggingAutoConfiguration.java`, thêm import:

```java
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
```

Thêm annotation vào class, ngay dưới `@ConditionalOnProperty`:

```java
@ConditionalOnMissingClass("io.javalibs.logging.spring.HttpAccessLogFilter")
```

Cập nhật Javadoc của class thành:

```java
/**
 * Auto-configuration registering the javalibs {@link RequestLoggingFilter} for servlet
 * web applications.
 *
 * <p>Enabled by default; disable with {@code javalibs.web.logging.enabled=false}. The
 * filter runs late in the chain (close to the handler) so the logged status reflects the
 * final response.</p>
 *
 * <p>It backs off entirely when {@code javalibs-logging-spring} is on the
 * classpath: that module's {@code HttpAccessLogFilter} supersedes this one, and
 * registering both would log every request twice. The class is referenced by
 * name so that javalibs-web keeps no dependency on javalibs-logging.</p>
 */
```

- [ ] **Step 4: Thêm test phía `javalibs-web` chống lùi nhầm**

Tạo `javalibs-web/javalibs-web-spring-boot-autoconfigure/src/test/java/io/javalibs/web/autoconfigure/RequestLoggingAutoConfigurationTest.java`:

```java
package io.javalibs.web.autoconfigure;

import io.javalibs.web.spring.RequestLoggingFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RequestLoggingAutoConfiguration.class));

    @Test
    void registersTheFilterWhenJavalibsLoggingIsNotOnTheClasspath() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(FilterRegistrationBean.class);
            assertThat(context.getBean(FilterRegistrationBean.class).getFilter())
                    .isInstanceOf(RequestLoggingFilter.class);
        });
    }

    @Test
    void backsOffWhenDisabledByProperty() {
        runner.withPropertyValues("javalibs.web.logging.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(FilterRegistrationBean.class));
    }
}
```

- [ ] **Step 5: Chạy test của cả hai module**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn -pl javalibs-web/javalibs-web-spring-boot-autoconfigure,javalibs-logging/javalibs-logging-spring-boot-autoconfigure -am test
```

Kỳ vọng: **PASS** ở cả hai module. `javalibs-web` vẫn đăng ký filter cũ (vì `javalibs-logging` không nằm trên classpath của nó); `javalibs-logging` chỉ thấy một filter.

- [ ] **Step 6: Commit**

```bash
git add javalibs-web javalibs-logging
git commit -m "fix(web): RequestLoggingFilter lùi khi javalibs-logging có mặt, tránh log đôi"
```

---

### Task 16: Starter + đăng ký vào BOM

**Files:**
- Create: `javalibs-logging/javalibs-logging-spring-boot-starter/pom.xml`
- Modify: `javalibs-logging/pom.xml` (thêm module cuối)
- Modify: `javalibs-dependencies/pom.xml`

**Interfaces:**
- Consumes: cả 4 module đã dựng ở Task 1–14.
- Produces: artifact `io.javalibs:javalibs-logging-spring-boot-starter` và 5 mục `dependencyManagement` trong BOM.

- [ ] **Step 1: Tạo pom của starter**

Tạo `javalibs-logging/javalibs-logging-spring-boot-starter/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>io.javalibs</groupId>
    <artifactId>javalibs-logging</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>..</relativePath>
  </parent>

  <artifactId>javalibs-logging-spring-boot-starter</artifactId>

  <name>javalibs :: logging :: spring-boot-starter</name>
  <description>
    One-stop Spring Boot starter for javalibs structured logging: JSON log
    records following the javalibs schema, sensitive data masking and the HTTP
    access log filter.
  </description>

  <dependencies>
    <dependency>
      <groupId>io.javalibs</groupId>
      <artifactId>javalibs-logging-core</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>io.javalibs</groupId>
      <artifactId>javalibs-logging-logback</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>io.javalibs</groupId>
      <artifactId>javalibs-logging-spring</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>io.javalibs</groupId>
      <artifactId>javalibs-logging-spring-boot-autoconfigure</artifactId>
      <version>${project.version}</version>
    </dependency>
  </dependencies>
</project>
```

Starter **không** khai báo `logback-classic`: nó đã là dependency compile của
`javalibs-logging-logback`, và mọi ứng dụng Spring Boot vốn có sẵn qua
`spring-boot-starter-logging`.

Trong `javalibs-logging/pom.xml`, hoàn thiện khối `<modules>`:

```xml
  <modules>
    <module>javalibs-logging-core</module>
    <module>javalibs-logging-logback</module>
    <module>javalibs-logging-spring</module>
    <module>javalibs-logging-spring-boot-autoconfigure</module>
    <module>javalibs-logging-spring-boot-starter</module>
  </modules>
```

- [ ] **Step 2: Thêm 5 artifact vào BOM**

Trong `javalibs-dependencies/pom.xml`, ngay **trước** khối bình luận
`<!-- Persistence (Flyway conventions) -->`, chèn:

```xml
      <!-- Logging (structured JSON) -->
      <dependency>
        <groupId>io.javalibs</groupId>
        <artifactId>javalibs-logging-core</artifactId>
        <version>${project.version}</version>
      </dependency>
      <dependency>
        <groupId>io.javalibs</groupId>
        <artifactId>javalibs-logging-logback</artifactId>
        <version>${project.version}</version>
      </dependency>
      <dependency>
        <groupId>io.javalibs</groupId>
        <artifactId>javalibs-logging-spring</artifactId>
        <version>${project.version}</version>
      </dependency>
      <dependency>
        <groupId>io.javalibs</groupId>
        <artifactId>javalibs-logging-spring-boot-autoconfigure</artifactId>
        <version>${project.version}</version>
      </dependency>
      <dependency>
        <groupId>io.javalibs</groupId>
        <artifactId>javalibs-logging-spring-boot-starter</artifactId>
        <version>${project.version}</version>
      </dependency>
```

- [ ] **Step 3: Build toàn bộ reactor**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn clean install -DskipTests
```

Kỳ vọng: **BUILD SUCCESS**, và trong danh sách reactor có đủ 5 dòng
`javalibs :: logging :: ...`.

- [ ] **Step 4: Chạy toàn bộ test của repo**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn test
```

Kỳ vọng: **BUILD SUCCESS**, không module nào đỏ. Đây là lần đầu toàn repo được
chạy sau thay đổi ở `javalibs-web` (Task 15) — nếu có module khác vỡ thì gần như
chắc chắn là do annotation vừa thêm ở đó.

- [ ] **Step 5: Commit**

```bash
git add javalibs-logging javalibs-dependencies/pom.xml
git commit -m "feat(logging): starter javalibs-logging-spring-boot-starter + đăng ký BOM"
```

---

### Task 17: Tài liệu

**Files:**
- Create: `javalibs-logging/README.md`
- Create: `javalibs-logging/javalibs-logging-spring-boot-starter/README.md`
- Create: `docs/modules/logging.md`
- Modify: `README.md` (bảng "Danh sách module")
- Modify: `docs/index.md`
- Modify: `docs/configuration-reference.md`

**Interfaces:**
- Consumes: toàn bộ API đã dựng ở Task 1–16.
- Produces: không có mã.

**Quy ước:** tài liệu viết **tiếng Việt**, theo đúng phong cách
`javalibs-observability/README.md` — mở đầu bằng mục đích, rồi sơ đồ kiến trúc
module, bảng thuộc tính, ví dụ, cách dùng nhanh.

- [ ] **Step 1: Viết `javalibs-logging/README.md`**

Nội dung bắt buộc, theo thứ tự:

1. **Mục đích** — vì sao cần một bản ghi JSON cho mỗi sự kiện thay vì log text.
2. **Ví dụ đầu ra** — chép nguyên khối JSON mẫu ở §1 của
   `docs/superpowers/specs/2026-08-22-javalibs-logging-design.md`.
3. **Kiến trúc module** — sơ đồ cây 5 module kèm bảng "Module | Nội dung chính",
   giống bảng trong `javalibs-observability/README.md`.
4. **Bật JSON log** — nêu rõ mặc định là **tắt** và vì sao (console dev), kèm:

   ```yaml
   javalibs:
     logging:
       json:
         enabled: true
   ```

   và ghi chú rằng ai muốn tự chỉ định thì đặt
   `logging.structured.format.console=io.javalibs.logging.logback.JavalibsJsonLogFormatter`,
   còn log ra file thì tự đặt `logging.structured.format.file`.
5. **Bảng thuộc tính cấu hình** — chép nguyên bảng 19 dòng ở §4 của spec.
6. **Che dữ liệu nhạy cảm** — liệt kê 17 key mặc định, giải thích quy tắc chuẩn
   hóa tên (`Pass_Word` = `password`), nêu rõ `masking.keys` **cộng dồn** chứ
   không thay thế, và cảnh báo rằng masking không thay thế việc không log dữ liệu
   không cần thiết.
7. **API cho developer** — hai ví dụ:

   ```java
   // một dòng: API chuẩn SLF4J 2.x, không cần class nào của javalibs
   log.atInfo().addKeyValue("orderId", order.id()).log("Order created");

   // cả một phạm vi: tag gắn cho mọi dòng log bên trong
   try (LogContext.Scope scope = LogContext.tags("checkout")) {
       log.info("Cart validated");
   }
   ```

8. **Quan hệ với các module khác** — `javalibs-web` tự tắt `RequestLoggingFilter`
   khi module này có mặt; `correlationId` / `traceId` / `spanId` từ
   `javalibs-observability` tự chảy vào `metadata`.
9. **Cảnh báo vận hành** — `access.include-body` mặc định tắt vì lý do PII;
   `access.trust-proxy` mặc định tắt vì `X-Forwarded-For` do client kiểm soát.

- [ ] **Step 2: Viết `javalibs-logging/javalibs-logging-spring-boot-starter/README.md`**

Ngắn, theo mẫu `javalibs-observability-spring-boot-starter/README.md`: starter
kéo theo những gì, đoạn XML khai báo dependency, cấu hình tối thiểu để bật JSON
log, và liên kết ngược về `javalibs-logging/README.md`.

- [ ] **Step 3: Viết `docs/modules/logging.md`**

Tham chiếu chi tiết, theo cấu trúc các file khác trong `docs/modules/`: bảng
schema từng field kèm nguồn dữ liệu (chép bảng ở §3.2 của spec), luồng dữ liệu
(chép sơ đồ ở §5 của spec), quy tắc field vắng mặt, cách viết
`PrincipalResolver` riêng, và cách tắt từng phần.

- [ ] **Step 4: Cập nhật 3 file tài liệu tổng**

Trong `README.md` gốc, thêm một dòng vào bảng "Danh sách module", đặt ngay
**dưới** dòng `javalibs-observability-*`:

```
| `javalibs-logging-*` | Log JSON có cấu trúc: schema cố định mỗi sự kiện, che dữ liệu nhạy cảm, access log HTTP | `javalibs-logging-spring-boot-starter` |
```

Trong `docs/index.md`, thêm liên kết tới `docs/modules/logging.md` ở đúng vị trí
danh sách module hiện có.

Trong `docs/configuration-reference.md`, thêm mục `javalibs.logging.*` với đủ 19
thuộc tính ở §4 của spec, đặt cạnh mục `javalibs.observability.*`.

- [ ] **Step 5: Kiểm tra liên kết và ví dụ**

```bash
grep -rn "javalibs-logging" README.md docs/index.md docs/configuration-reference.md docs/modules/logging.md
```

Kỳ vọng: mọi đường dẫn file được nhắc tới đều tồn tại thật; không còn chỗ nào ghi
`TODO`.

- [ ] **Step 6: Chạy lại toàn bộ build lần cuối**

```bash
export JAVA_HOME=/Users/tanvx/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home && mvn clean install
```

Kỳ vọng: **BUILD SUCCESS**.

- [ ] **Step 7: Commit**

```bash
git add README.md docs javalibs-logging
git commit -m "docs(logging): README module + starter + tham chiếu chi tiết và cập nhật tài liệu tổng"
```

---

## Tổng kết

17 task, mỗi task tự đứng được và có chu kỳ test riêng. Thứ tự phụ thuộc:

- **Task 1–4** dựng `-core` thuần Java — chạy nhanh, không cần Spring.
- **Task 5–10** dựng `-logback`; Task 6→9 bồi đắp dần cùng một formatter, nên
  phải chạy đúng thứ tự.
- **Task 11–13** dựng `-spring`; độc lập với `-logback`, có thể làm song song
  với Task 5–10 nếu cần.
- **Task 14–16** ráp lại: autoconfigure, vá chỗ log đôi ở `javalibs-web`, starter, BOM.
- **Task 17** tài liệu, làm sau cùng khi API đã đứng yên.

Rủi ro lớn nhất nằm ở Task 15: đó là task duy nhất sửa mã của module đang được
service khác dùng. Task 16 Step 4 chạy `mvn test` toàn repo chính là lưới an
toàn cho nó.
