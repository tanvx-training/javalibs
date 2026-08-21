# javalibs-storage

Chuẩn hóa object storage cho toàn hệ thống trên nền MinIO/S3: một abstraction `ObjectStorage` duy nhất cho thao tác server-side (put/get/stat/delete) và presigned URL cho upload/download trực tiếp từ browser, ký đúng host mà browser thực sự gọi.

## Kiến trúc submodule

| Submodule | Vai trò |
|---|---|
| `javalibs-storage-core` | Java thuần: `ObjectStorage` (abstraction), `MinioObjectStorage` (impl MinIO với 2 client), `ObjectStat`, `StorageException`, `ContentDispositions` (RFC 5987) |
| `javalibs-storage-spring-boot-autoconfigure` | `JavalibsStorageAutoConfiguration` dựng bean `javalibsObjectStorage` từ `javalibs.storage.*`; opt-in vì cần MinIO đang chạy |
| `javalibs-storage-spring-boot-starter` | Điểm chạm của client — chỉ `pom.xml`, gom core + autoconfigure + MinIO SDK |

Không có tầng `-spring` riêng: `MinioObjectStorage` đã đủ nhỏ để nằm chung `-core` mà không kéo Spring vào.

## Thuộc tính cấu hình

| Thuộc tính | Mặc định | Ý nghĩa |
|---|---|---|
| `javalibs.storage.enabled` | `false` | Bật auto-configuration (opt-in vì cần MinIO đang chạy) |
| `javalibs.storage.endpoint` | — | Endpoint nội bộ cho thao tác server-side |
| `javalibs.storage.external-endpoint` | *(rỗng → dùng `endpoint`)* | Endpoint public để ký presigned URL |
| `javalibs.storage.region` | `us-east-1` | Region ký cho cả 2 client — bắt buộc đặt tường minh để presign không rơi vào tra cứu region qua mạng tới `external-endpoint` (xem Javadoc `MinioObjectStorage`) |
| `javalibs.storage.access-key` / `.secret-key` | — | Credentials |
| `javalibs.storage.bucket` | — | Bucket dùng cho mọi thao tác |
| `javalibs.storage.ensure-bucket` | `false` | Tự tạo bucket lúc khởi động nếu chưa có |
| `javalibs.storage.presign-put-expiry` | `10m` | Hạn presigned URL PUT |
| `javalibs.storage.presign-get-expiry` | `5m` | Hạn presigned URL GET |

Bean `javalibsObjectStorage` (kiểu `ObjectStorage`) luôn `@ConditionalOnMissingBean` — service tự định nghĩa `ObjectStorage` thì thư viện tự lùi. Chi tiết dùng: xem [README của starter](javalibs-storage-spring-boot-starter/README.md) và [docs/modules/storage.md](../docs/modules/storage.md).
