# javalibs-storage

> Chuẩn hóa object storage cho toàn platform trên nền **MinIO/S3**: một abstraction `ObjectStorage` cho thao tác server-side (put/get/stat/delete) và presigned URL để browser upload/download trực tiếp, ký đúng host mà browser thực sự gọi tới.

## Artifacts

| artifactId | Packaging | Mô tả |
|---|---|---|
| `javalibs-storage` | pom | Parent gộp 3 submodule |
| `javalibs-storage-core` | jar | Thuần Java: `ObjectStorage` (abstraction), `MinioObjectStorage` (impl MinIO với 2 client), `ObjectStat` (record `size`/`contentType`), `StorageException`, `ContentDispositions` (RFC 5987). Package `io.javalibs.storage` |
| `javalibs-storage-spring-boot-autoconfigure` | jar | `JavalibsStorageAutoConfiguration` + `JavalibsStorageProperties` (namespace `javalibs.storage.*`). Package `io.javalibs.storage.autoconfigure` |
| `javalibs-storage-spring-boot-starter` | jar | Starter: core + autoconfigure + `io.minio:minio`. Không chứa code Java |

Không có tầng `-spring` riêng: `MinioObjectStorage` không cần bất kỳ dependency Spring nào để hoạt động nên nằm gọn trong `-core`.

## Khi nào dùng / không dùng

**Dùng khi:**

- Service cần lưu file (bằng chứng, tài liệu, ảnh...) trên MinIO/S3 và muốn một API thống nhất thay vì gọi SDK MinIO trực tiếp ở nhiều nơi.
- Cần cấp URL cho browser tự upload/download thẳng lên storage (presigned URL) thay vì proxy nội dung qua backend — giảm tải băng thông service.
- Nhiều service dùng chung một MinIO cluster nhưng có endpoint nội bộ (container network) khác với endpoint public browser gọi được.

**Không dùng khi:**

- Cần thao tác nâng cao của SDK MinIO (versioning, lifecycle policy, multipart thủ công, bucket policy...) — module này chỉ bọc 5 thao tác cơ bản, dùng thẳng `MinioClient` khi cần hơn thế.
- Object cần xử lý ngay trong request (resize ảnh, virus scan...) trước khi lưu — đó là logic nghiệp vụ của service, module chỉ lo lưu trữ.

## Thành phần chính

### `ObjectStorage` (`javalibs-storage-core`)

Interface trung tâm — key là chuỗi do caller sở hữu, implementation không diễn giải hay viết lại key:

```java
public interface ObjectStorage {
    void put(String key, String contentType, long contentLength, InputStream content);
    InputStream get(String key);                       // caller phải close stream
    Optional<ObjectStat> stat(String key);              // empty khi không tồn tại
    void delete(String key);                            // xóa key không tồn tại là no-op
    String presignPut(String key);
    String presignGet(String key, String contentDisposition);
}
```

`ObjectStat` là `record ObjectStat(long size, String contentType)`.

### `MinioObjectStorage` — thiết kế 2 client (`javalibs-storage-core`)

Implementation MinIO của `ObjectStorage`, giữ **hai `MinioClient`** thay vì một:

- `client` — dựng từ `endpoint` (nội bộ), dùng cho mọi thao tác server-side: `put`/`get`/`stat`/`delete`/`ensureBucket`.
- `signer` — dựng từ `externalEndpoint` (public), dùng **duy nhất** để ký `presignPut`/`presignGet`.

**Vì sao cần hai client, không dùng chung một endpoint:** chữ ký SigV4 của presigned URL gắn chặt với host trong URL đó — client MinIO ký URL bằng chính endpoint nó được khởi tạo. Trong Docker/K8s, service gọi MinIO qua tên nội bộ (vd `http://minio:9000`), nhưng nếu presigned URL cũng ký với host đó thì browser (chạy ngoài mạng nội bộ) sẽ nhận về một URL trỏ tới `minio:9000` — không resolve được, hoặc nếu resolve được thì domain SigV4 vẫn không khớp origin browser gọi. `externalEndpoint` cho phép presigned URL ký đúng origin browser gọi được (vd `https://storage.example.com` hoặc `http://localhost:9000` khi dev local), trong khi thao tác server-side vẫn đi qua endpoint nội bộ nhanh hơn và không phụ thuộc DNS public. Khi không đặt `externalEndpoint`, nó fallback về `endpoint` — đúng cho môi trường single-host (dev local, test).

Constructor: `MinioObjectStorage(String endpoint, String externalEndpoint, String accessKey, String secretKey, String bucket, Duration putExpiry, Duration getExpiry)`. `ensureBucket()` tạo bucket nếu chưa có — an toàn gọi lại nhiều lần, nhưng **mở một kết nối tới MinIO** nên không gọi ngầm định trong constructor.

### `ContentDispositions` (`javalibs-storage-core`)

Dựng giá trị header `Content-Disposition` an toàn cho tên file không thuần ASCII (tiếng Việt có dấu...): vừa có `filename` ASCII fallback vừa có `filename*` mã hoá RFC 5987.

- `static String attachment(String fileName)` — ép browser tải xuống.
- `static String inline(String fileName)` — hiển thị trực tiếp (PDF, ảnh) nếu browser hỗ trợ.

Dùng làm tham số `contentDisposition` của `presignGet` — MinIO trả header này qua override `response-content-disposition` trong URL, không cần set lúc `put`.

### `StorageException` (`javalibs-storage-core`)

`RuntimeException` bọc mọi lỗi từ storage (network, auth, object không tồn tại lúc `get`). `stat()` **không** ném exception khi thiếu key — trả `Optional.empty()`; chỉ `get()` ném khi key không tồn tại.

### Auto-configuration (`javalibs-storage-spring-boot-autoconfigure`)

| Class | Bean | Điều kiện |
|---|---|---|
| `JavalibsStorageAutoConfiguration` | `javalibsObjectStorage` (`ObjectStorage`) | `@ConditionalOnClass(MinioClient)`; `javalibs.storage.enabled=true` (mặc định `false`); `@ConditionalOnMissingBean(ObjectStorage)` |

Điểm mấu chốt:

- **Opt-in mặc định**: `javalibs.storage.enabled` mặc định `false` vì tính năng cần MinIO đang chạy — khác với các module "nhẹ" (cache, web...) mặc định bật. Đúng quy ước chung của platform cho tính năng cần hạ tầng.
- **`ensure-bucket` mặc định `false`**: dựng bean `javalibsObjectStorage` **không** mở kết nối nào (constructor `MinioClient.builder()...build()` không gọi network); chỉ khi `ensure-bucket=true` thì auto-configuration mới gọi `storage.ensureBucket()` lúc dựng bean, mở một kết nối kiểm tra/tạo bucket ngay lúc khởi động context.
- **`external-endpoint` rỗng → fallback `endpoint`**: xử lý ngay trong `javalibsObjectStorage()`, không cần cấu hình gì thêm cho môi trường single-host.
- MinIO đến transitively qua `javalibs-storage-core` (không có dependency `minio` trực tiếp trong `-autoconfigure`), nhưng vẫn guard bằng `@ConditionalOnClass(MinioClient.class)` — đúng nguyên tắc "autoconfigure không giả định thư viện thứ ba luôn có mặt".

## Cấu hình

Toàn bộ property bind vào `JavalibsStorageProperties` (`javalibs.storage.*`):

| Thuộc tính | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `javalibs.storage.enabled` | boolean | `false` | Bật auto-configuration (opt-in vì cần MinIO đang chạy) |
| `javalibs.storage.endpoint` | String | — | Endpoint nội bộ cho thao tác server-side |
| `javalibs.storage.external-endpoint` | String | *(rỗng → dùng `endpoint`)* | Endpoint public để ký presigned URL |
| `javalibs.storage.access-key` | String | — | Access key |
| `javalibs.storage.secret-key` | String | — | Secret key |
| `javalibs.storage.bucket` | String | — | Bucket dùng cho mọi thao tác |
| `javalibs.storage.ensure-bucket` | boolean | `false` | Tự tạo bucket lúc khởi động nếu chưa có (mở kết nối lúc boot) |
| `javalibs.storage.presign-put-expiry` | Duration | `10m` | Hạn presigned URL cho PUT |
| `javalibs.storage.presign-get-expiry` | Duration | `5m` | Hạn presigned URL cho GET |

## Hướng dẫn sử dụng

### Dependency

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-storage-spring-boot-starter</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### `application.yml`

```yaml
javalibs:
  storage:
    enabled: true
    endpoint: http://minio:9000                # container nội bộ
    external-endpoint: http://localhost:5173    # origin browser thực sự gọi để presign
    access-key: ${MINIO_ACCESS_KEY}
    secret-key: ${MINIO_SECRET_KEY}
    bucket: orders-service
    ensure-bucket: true
```

### Code

```java
@Service
public class EvidenceService {

    private final ObjectStorage storage;

    public EvidenceService(ObjectStorage storage) {
        this.storage = storage;
    }

    public String uploadUrl(String key) {
        return storage.presignPut(key);
    }

    public String downloadUrl(String key, String fileName) {
        return storage.presignGet(key, ContentDispositions.attachment(fileName));
    }
}
```

## Ghi đè & mở rộng

- **`ObjectStorage` riêng**: khai báo bean `ObjectStorage` bất kỳ — auto-configuration `@ConditionalOnMissingBean` nên tự lùi.
- **Tắt hoàn toàn**: `javalibs.storage.enabled=false` (mặc định) — không bean nào được tạo, không kết nối MinIO nào được mở.
- **Nhiều bucket/nhiều MinIO cluster trong cùng service**: auto-configuration chỉ dựng một bean cho cấu hình `javalibs.storage.*` mặc định; cần thêm bucket/cluster khác thì tự khai `@Bean MinioObjectStorage` riêng (constructor là public, không cần Spring).

## Testing

- **Auto-configuration** (`JavalibsStorageAutoConfigurationTest`): `ApplicationContextRunner` — 4 hướng: inactive mặc định, active khi bật đủ property (không mở kết nối vì `ensure-bucket` mặc định `false`), backs off khi có bean `ObjectStorage` của người dùng, inactive khi `MinioClient` không có trên classpath (`FilteredClassLoader`).
- **`MinioObjectStorage`** (`MinioObjectStorageIT`, `javalibs-storage-core`): Testcontainers MinIO thật — round-trip put/stat/get/delete, `stat` rỗng + `get` ném `StorageException` cho key thiếu, `delete` key thiếu là no-op, presigned PUT/GET dùng `HttpClient` thật gọi thẳng URL trả về, và một test khoá riêng thiết kế 2-client: presign phải ký bằng `externalEndpoint`, không phải `endpoint` nội bộ — đổi nhầm client ký sẽ làm test này đỏ dù mọi IT khác vẫn xanh.
- **`ContentDispositionsTest`**: Java thuần — unit test trực tiếp.

## Lưu ý & bẫy thường gặp

- **Quên đặt `external-endpoint` trong môi trường nhiều host**: presigned URL sẽ ký bằng `endpoint` nội bộ — browser không gọi được (DNS không resolve hoặc chữ ký SigV4 sai host). Chỉ bỏ trống khi service và browser cùng thấy một host (dev local, single-host demo).
- **`ensure-bucket=true` mở kết nối lúc khởi động context** — nếu MinIO chưa sẵn sàng khi service start (thứ tự khởi động trong docker-compose/K8s), context sẽ fail refresh. Cân nhắc `ensure-bucket=false` + tạo bucket bằng IaC/migration riêng cho production.
- **`get()` ném exception cho key thiếu, `stat()` thì không** — dùng `stat()` trước nếu chỉ cần kiểm tra tồn tại, tránh dựa vào catch exception cho luồng bình thường.
- **Presigned URL có hạn cố định lúc dựng bean** (`presign-put-expiry`/`presign-get-expiry`) — không đổi được per-request; cần hạn khác nhau theo tình huống thì tự dựng thêm `MinioObjectStorage` khác hoặc gọi thẳng `MinioClient`.
- **Tên file tiếng Việt có dấu trong header tải xuống**: luôn dùng `ContentDispositions.attachment/inline` thay vì tự nối chuỗi `Content-Disposition` — tự làm dễ sinh header lỗi encoding với trình duyệt cũ.
