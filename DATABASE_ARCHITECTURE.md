# Kiến trúc dữ liệu Vision Caption 1.6

## 1. Phân vai lưu trữ

PostgreSQL của backend là nguồn dữ liệu trung tâm và có thẩm quyền. Cơ sở dữ
liệu này lưu tài khoản, phiên xác thực, RBAC, nhật ký hoạt động, media, lịch sử
chú thích, yêu thích và tùy chọn người dùng.

SQLite `vision_caption.db` trên Android chỉ đảm nhiệm ba vai trò:

- cache dữ liệu cần dùng khi ngoại tuyến;
- tùy chọn cục bộ của ứng dụng và thiết bị;
- hàng đợi `sync_outbox` để gửi thay đổi lên PostgreSQL khi có kết nối.

APK không chứa tài khoản PostgreSQL. Ứng dụng chỉ gọi FastAPI qua HTTPS; API
xác thực JWT, kiểm tra quyền rồi mới đọc hoặc ghi PostgreSQL.

## 2. Mô hình quan hệ PostgreSQL

| Bảng | Khóa chính | Quan hệ chính |
|---|---|---|
| `users` | `id` UUID | Gốc của tài khoản; username/email duy nhất |
| `permission_definitions` | `permission_key` | Danh mục `RESOURCE:ACTION` |
| `user_permissions` | `(user_id, permission_key)` | FK tới user và định nghĩa quyền |
| `activity_events` | `id` UUID | FK tới user; audit đăng nhập/nghiệp vụ |
| `media_objects` | `id` UUID | FK chủ sở hữu; thumbnail có kiểm soát dung lượng |
| `caption_history` | `id` UUID | FK user và media; chứa kết quả/metric mô hình |
| `user_preferences` | `user_id` | Quan hệ một-một với user |
| `revoked_tokens` | `jti` | Danh sách JWT bị thu hồi |
| `sync_receipts` | `operation_id` UUID | Chống áp dụng lặp thao tác đồng bộ |

Các bảng liên kết dùng foreign key và chỉ mục theo user, thời gian, trạng thái
yêu thích. Tìm kiếm audit có chỉ mục GIN toàn văn.

## 3. Ràng buộc, trigger và function

- `citext` bảo đảm username/email duy nhất không phân biệt hoa thường.
- `prevent_user_delete` chặn hard-delete mọi tài khoản. Quản trị chỉ được
  khóa/mở khóa bằng `is_active`.
- `set_updated_at` cập nhật thời điểm sửa bản ghi.
- `audit_user_status_change` tự ghi audit khi khóa hoặc mở khóa tài khoản.
- `cleanup_orphan_media_after_caption_delete` dọn media không còn tham chiếu.
- `set_user_active`, `replace_user_permissions` và `upsert_caption` đóng gói
  thao tác nghiệp vụ theo giao dịch.
- `v_user_activity_summary` và `v_user_caption_summary` cung cấp số liệu tổng
  hợp cho Super Admin.

## 4. Đồng bộ và phân tách dữ liệu

Mỗi dữ liệu nghiệp vụ đều gắn `user_id`; backend lấy user từ JWT, không tin
`user_id` do ứng dụng tự gửi. Media được đồng bộ trước caption. Mỗi thao tác có
`operation_id` để retry an toàn, sau đó mới được đánh dấu hoàn tất trong
SQLite.

Guest có ID cục bộ cố định nhưng không có quyền tạo `HISTORY` hoặc `FAVORITE`.
Kết quả chú thích của Guest chỉ tồn tại trên màn hình hiện tại và không được
đưa vào SQLite hay PostgreSQL. Khi đổi tài khoản, Android nạp cache theo đúng
owner và đồng bộ với dữ liệu trung tâm của tài khoản đó.

## 5. Vị trí triển khai

- Backend, Docker Compose và schema: `D:\KLCN\backend`
- Schema PostgreSQL: `D:\KLCN\backend\sql\001_schema.sql`
- Hướng dẫn cài đặt: `D:\KLCN\backend\README.md`
- SQLite/cache Android: `VisionDatabase.kt`
- API và hàng đợi đồng bộ: `CentralApiClient.kt`, `AppStorage.kt`
