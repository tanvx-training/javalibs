# javalibs-logging-spring-boot-starter

Starter "tất cả trong một" cho structured logging: JSON log theo schema cố định, che dữ liệu nhạy cảm, và access log HTTP — chỉ cần thêm một dependency.

## Cài đặt

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-logging-spring-boot-starter</artifactId>
</dependency>
```

Starter không có mã nguồn — gom `javalibs-logging-core`, `javalibs-logging-logback`, `javalibs-logging-spring` và `javalibs-logging-spring-boot-autoconfigure`. `logback-classic` và `spring-boot` là dependency **compile** sẵn có của `javalibs-logging-logback` (formatter implement interface của Boot trên `ILoggingEvent` của Logback), nên starter không khai báo lại — mọi ứng dụng Spring Boot đã có sẵn cả hai qua `spring-boot-starter-logging`.

## Bạn nhận được gì ngay khi thêm starter

- `HttpAccessLogFilter` tự đăng ký cho ứng dụng web servlet (order `LOWEST_PRECEDENCE - 10`), phát một dòng log kết thúc mỗi request kèm `request`/`response`, gắn MDC `userId`/`clientIp` cho mọi log phát ra trong request.
- Che dữ liệu nhạy cảm bật sẵn (`masking.enabled=true`) với denylist 26 key mặc định.
- Nếu `javalibs-web` cũng có mặt, `RequestLoggingFilter` cũ của nó tự lùi — không log đôi mỗi request.

## Cấu hình tối thiểu để bật JSON log

Mặc định output vẫn là log text thường (tốt cho console dev). Bật JSON — thường đặt trong `application-prod.yml`:

```yaml
javalibs:
  logging:
    json:
      enabled: true
    service: orders-api
```

Toàn bộ bảng thuộc tính (`javalibs.logging.*`), ví dụ đầu ra JSON, cách che dữ liệu nhạy cảm và các cảnh báo vận hành: xem [README của `javalibs-logging`](../README.md). Tham chiếu chi tiết từng field, luồng dữ liệu: [`docs/modules/logging.md`](../../docs/modules/logging.md).
