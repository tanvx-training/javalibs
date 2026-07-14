# javalibs-spring

> Mở rộng Spring Framework core (không dính web hay security): truy cập `ApplicationContext` tĩnh, chẩn đoán bean khởi tạo chậm và helper cho profile.

## Artifacts

| artifactId | Packaging | Mô tả |
|---|---|---|
| `javalibs-spring` | jar | Module đơn, 3 lớp trong `io.javalibs.spring.*`. Phụ thuộc duy nhất `spring-context` và `slf4j-api`. |

## Khi nào dùng / không dùng

**Dùng khi:**
- Code legacy hoặc static factory cần lấy bean mà không inject được (`ApplicationContextProvider`).
- Ứng dụng khởi động chậm và cần tìm bean nào là thủ phạm (`SlowBeanInitializationLogger`).
- Muốn thống nhất tên profile (`local`/`dev`/`staging`/`prod`/`test`) giữa các service và query profile an toàn (`Profiles`).

**Không dùng khi:**
- Cần utility thuần Java không dính Spring — dùng [javalibs-internal](internal.md).
- Cần tính năng web (error handling, paging) — dùng [javalibs-web](web.md).
- Code mới có thể constructor injection bình thường — `ApplicationContextProvider` là **escape hatch**, không phải pattern nên lạm dụng.

## Các thành phần chính

### `io.javalibs.spring.context.ApplicationContextProvider`

Cho phép truy cập `ApplicationContext` từ static context. Implement `ApplicationContextAware`, nên **phải được đăng ký làm bean** thì Spring mới gán context vào (module không có auto-configuration — xem [Cấu hình](#cấu-hình)).

| Method | Hành vi |
|---|---|
| `void setApplicationContext(ApplicationContext applicationContext)` | Callback của `ApplicationContextAware`, lưu context vào field `static volatile`. |
| `static ApplicationContext context()` | Trả về context hiện tại; ném `IllegalStateException` (kèm hướng dẫn) nếu context chưa được set. |
| `static <T> T getBean(Class<T> type)` | Viết tắt của `context().getBean(type)`. |
| `static <T> T getBean(String name, Class<T> type)` | Viết tắt của `context().getBean(name, type)`. |

### `io.javalibs.spring.lifecycle.SlowBeanInitializationLogger`

`BeanPostProcessor` đo thời gian **initialization** của từng bean và log WARN cho bean vượt ngưỡng. Grep log theo chuỗi `"Slow bean"` để tìm thủ phạm khởi động chậm.

| Constructor / hành vi | Chi tiết |
|---|---|
| `SlowBeanInitializationLogger()` | Ngưỡng mặc định **500 ms**. |
| `SlowBeanInitializationLogger(Duration threshold)` | Ngưỡng tùy chọn. |
| Cách đo | Ghi `System.nanoTime()` tại `postProcessBeforeInitialization`, tính hiệu tại `postProcessAfterInitialization`. Khi `elapsed >= threshold` log: `Slow bean initialization: '<beanName>' (<className>) took <n> ms` ở mức **WARN**, logger `io.javalibs.spring.lifecycle.SlowBeanInitializationLogger`. |

Lưu ý phạm vi đo: khoảng giữa hai callback này gồm `@PostConstruct`, `afterPropertiesSet()` và các `BeanPostProcessor` khác — **không** gồm thời gian chạy constructor hay injection dependency.

### `io.javalibs.spring.env.Profiles`

Hằng số tên profile quy ước và helper query environment (dựa trên `Environment.matchesProfiles`, hỗ trợ profile expression).

| Thành phần | Giá trị / hành vi |
|---|---|
| `Profiles.LOCAL` / `DEV` / `STAGING` / `PROD` / `TEST` | `"local"`, `"dev"`, `"staging"`, `"prod"`, `"test"`. |
| `static boolean isActive(Environment environment, String profile)` | `environment.matchesProfiles(profile)`. |
| `static boolean isProd(Environment environment)` | `true` khi profile `prod` active. |
| `static boolean isLocalOrTest(Environment environment)` | Match expression `"local | test"` — an toàn để bật diagnostics chi tiết. |

## Cấu hình

Module **không có auto-configuration và không có thuộc tính `javalibs.*` nào**. Cả `ApplicationContextProvider` lẫn `SlowBeanInitializationLogger` phải được ứng dụng tự khai báo bean (xem bên dưới). `Profiles` là static utility, dùng trực tiếp.

## Hướng dẫn sử dụng

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-spring</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Đăng ký bean:

```java
import io.javalibs.spring.context.ApplicationContextProvider;
import io.javalibs.spring.lifecycle.SlowBeanInitializationLogger;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PlatformConfig {

    @Bean
    public ApplicationContextProvider applicationContextProvider() {
        return new ApplicationContextProvider();
    }

    // BeanPostProcessor nên khai báo static để được đăng ký sớm nhất có thể
    @Bean
    static SlowBeanInitializationLogger slowBeanInitializationLogger() {
        return new SlowBeanInitializationLogger(Duration.ofMillis(300));
    }
}
```

Sử dụng:

```java
// Lấy bean từ static context (chỉ khi không inject được)
OrderService service = ApplicationContextProvider.getBean(OrderService.class);

// Query profile
import io.javalibs.spring.env.Profiles;

if (!Profiles.isProd(environment)) {
    log.info("Payload debug: {}", payload);
}
```

Kích hoạt profile theo quy ước trong `application.yml`:

```yaml
spring:
  profiles:
    active: local
```

## Ghi đè & mở rộng

Module không tự đăng ký bean nào nên không có `@ConditionalOnMissingBean` — ứng dụng toàn quyền quyết định có tạo bean hay không, ngưỡng bao nhiêu, đăng ký ở profile nào (ví dụ chỉ bật `SlowBeanInitializationLogger` khi `@Profile("local")`).

## Testing

- `Profiles`: dùng `MockEnvironment` của `spring-test`:

  ```java
  MockEnvironment env = new MockEnvironment();
  env.setActiveProfiles("local");
  assertThat(Profiles.isLocalOrTest(env)).isTrue();
  assertThat(Profiles.isProd(env)).isFalse();
  ```

- `ApplicationContextProvider`: trong unit test không có Spring, có thể gọi trực tiếp `setApplicationContext(...)` với một `StaticApplicationContext`/mock rồi assert `ApplicationContextProvider.getBean(...)`. Chú ý: field là static — test song song hoặc test chạy sau sẽ **nhìn thấy context của test trước**; reset bằng cách set lại context phù hợp ở `@BeforeEach`.
- `SlowBeanInitializationLogger`: gọi cặp `postProcessBeforeInitialization`/`postProcessAfterInitialization` bằng tay quanh một thao tác chậm giả lập, hoặc kiểm tra qua log appender. Xem `javalibs-spring/src/test/java/io/javalibs/spring/lifecycle/SlowBeanInitializationLoggerTest.java`.

## Lưu ý & bẫy thường gặp

- `ApplicationContextProvider.context()` ném `IllegalStateException` nếu gọi **trước khi context refresh xong** (ví dụ trong constructor của bean khác) hoặc khi quên đăng ký bean. Ưu tiên constructor injection; chỉ dùng lớp này cho legacy/static factory.
- Nhiều context trong cùng JVM (ví dụ test song song với context cache) sẽ **ghi đè lẫn nhau** vào field static — context set sau cùng thắng.
- Khai báo `SlowBeanInitializationLogger` bằng **static `@Bean` method**; nếu không, configuration class chứa nó bị khởi tạo sớm và một số bean tạo trước khi processor đăng ký sẽ không được đo.
- `SlowBeanInitializationLogger` chỉ đo pha initialization — bean chậm do constructor nặng hoặc do chờ kết nối trong injection sẽ không hiện trong log.
- `Profiles.isActive` dùng `matchesProfiles` nên nhận cả profile expression (`"local | test"`, `"!prod"`); truyền expression sai cú pháp sẽ ném exception từ Spring.
- Tên profile trong `Profiles` là quy ước chung của javalibs (ví dụ [javalibs-test](test.md) kích hoạt profile `test`) — dùng đúng các hằng số này thay vì string tự chế để tránh lệch nhau giữa các service.
