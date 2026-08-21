# javalibs-storage-spring-boot-starter

Starter chuẩn hóa object storage cho microservices: **MinIO/S3** với thao tác server-side (put/get/stat/delete) và **presigned URL** cho upload/download trực tiếp từ browser.

## Cài đặt

```xml
<dependency>
  <groupId>io.javalibs</groupId>
  <artifactId>javalibs-storage-spring-boot-starter</artifactId>
</dependency>
```

Tính năng cần hạ tầng (MinIO đang chạy) nên **mặc định tắt** — phải bật chủ động bằng `javalibs.storage.enabled=true`.

## Cấu hình

| Thuộc tính | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `javalibs.storage.enabled` | boolean | `false` | Bật auto-configuration (opt-in vì cần MinIO đang chạy) |
| `javalibs.storage.endpoint` | String | — | Endpoint nội bộ dùng cho thao tác server-side (put/get/stat/delete) |
| `javalibs.storage.external-endpoint` | String | *(rỗng → dùng `endpoint`)* | Endpoint public mà browser gọi để ký presigned URL |
| `javalibs.storage.region` | String | `us-east-1` | Region cả 2 client dùng để ký — bắt buộc tường minh, không được bỏ trống (xem mục "Vì sao có external-endpoint" bên dưới) |
| `javalibs.storage.access-key` | String | — | Access key |
| `javalibs.storage.secret-key` | String | — | Secret key |
| `javalibs.storage.bucket` | String | — | Bucket dùng cho mọi thao tác |
| `javalibs.storage.ensure-bucket` | boolean | `false` | Tự tạo bucket lúc khởi động nếu chưa có (mở kết nối lúc boot) |
| `javalibs.storage.presign-put-expiry` | Duration | `10m` | Hạn presigned URL cho PUT |
| `javalibs.storage.presign-get-expiry` | Duration | `5m` | Hạn presigned URL cho GET |

## Ví dụ `application.yml`

```yaml
javalibs:
  storage:
    enabled: true
    endpoint: http://minio:9000                # container nội bộ, service gọi trực tiếp
    external-endpoint: http://localhost:5173    # origin browser thực sự gọi để presign
    region: us-east-1                           # region MinIO server (mặc định của MinIO); đổi nếu cluster cấu hình khác
    access-key: ${MINIO_ACCESS_KEY}
    secret-key: ${MINIO_SECRET_KEY}
    bucket: orders-service
    ensure-bucket: true
```

## Sử dụng

Inject thẳng `ObjectStorage` — bean `javalibsObjectStorage` được auto-configuration đăng ký (`@ConditionalOnMissingBean`, tự lùi nếu ứng dụng đã tự khai bean `ObjectStorage` khác):

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
}
```

## Vì sao có `external-endpoint` riêng

Chữ ký SigV4 gắn với **host mà request thực sự đi tới**. Trong Docker/K8s, service gọi MinIO qua tên nội bộ (vd `http://minio:9000`), nhưng presigned URL trả về browser phải trỏ tới origin browser gọi được (vd `https://minio.example.com` hoặc `http://localhost:9000` khi dev local). Dùng chung một endpoint cho cả hai thì presigned URL ký cho host nội bộ sẽ bị MinIO từ chối khi browser gọi qua host public — do đó module giữ **hai `MinioClient`**: một ký cho thao tác server-side (`endpoint`), một chỉ dùng để ký presigned URL (`external-endpoint`, fallback về `endpoint` khi không đặt — phù hợp môi trường single-host như dev local).

**Vì sao `region` bắt buộc:** khi `MinioClient` được dựng mà không có `region`, SDK MinIO sẽ tự tra cứu region bằng một lệnh gọi HTTP thật (`GetBucketLocation`) tới chính endpoint của client đó, thực hiện ngay lần đầu ký presigned URL. Với client `signer`, endpoint đó là `external-endpoint` — thường là origin browser gọi được nhưng **không** reachable từ backend (vd `http://localhost:5173` chỉ đúng trên máy dev, container backend không gọi ra được). Thiếu `region`, mọi `presignPut`/`presignGet` sẽ ném lỗi mạng ngay lần gọi đầu dù về bản chất việc ký URL là tính toán cục bộ thuần túy. Đặt `region` tường minh loại bỏ hoàn toàn cuộc gọi tra cứu đó — `signer` không bao giờ mở kết nối. Mặc định `us-east-1` khớp region mặc định của MinIO server; chỉ cần đổi khi cluster MinIO/S3 thật sự cấu hình region khác.
