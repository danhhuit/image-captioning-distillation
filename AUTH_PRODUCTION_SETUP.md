# Cấu hình xác thực cho môi trường phát hành

[English version](AUTH_PRODUCTION_SETUP_EN.md) · [Quay lại README](README.md)

Tài liệu này hướng dẫn cấu hình đăng nhập thường, Google Sign-In và đặt lại mật khẩu cho ứng dụng Mobile Image Captioning 1.7.0. Các giá trị khóa và fingerprint phải được tạo từ môi trường của người triển khai; không dùng lại thông tin của máy phát triển khác.

## 1. Chuẩn bị backend

Từ thư mục gốc repository:

```powershell
Set-Location .\backend
Copy-Item .env.example .env
```

Mở `.env` và cấu hình ít nhất:

```dotenv
JWT_SECRET=<chuỗi-ngẫu-nhiên-dài>
ADMIN_PASSWORD=<mật-khẩu-quản-trị-ban-đầu>
GOOGLE_WEB_CLIENT_ID=<web-client-id>.apps.googleusercontent.com
SMTP_HOST=<máy-chủ-smtp>
SMTP_PORT=587
SMTP_USERNAME=<tài-khoản-gửi>
SMTP_PASSWORD=<mật-khẩu-ứng-dụng>
SMTP_FROM_EMAIL=<địa-chỉ-người-gửi>
```

Khởi động:

```powershell
docker compose up -d --build
docker compose ps
```

Không đưa `ADMIN_PASSWORD`, `JWT_SECRET` hoặc mật khẩu SMTP vào APK.

## 2. Tạo fingerprint cho Android

Mở terminal tại thư mục Android:

```powershell
Set-Location .\src
.\gradlew.bat signingReport
```

Ghi lại SHA-1 và SHA-256 của đúng biến thể:

- `debug` khi chạy từ Android Studio;
- `release` khi phát hành APK/AAB;
- fingerprint của Play App Signing khi phân phối qua Google Play.

Mỗi khóa ký tạo fingerprint khác nhau. Vì vậy, giá trị trong tài liệu hoặc trên máy khác không thể thay thế kết quả `signingReport` của bạn.

## 3. Cấu hình Google Cloud

1. Mở Google Cloud Console và chọn dự án.
2. Cấu hình OAuth consent screen.
3. Tạo **Android OAuth client**:
   - package name: `com.klcn.mobilecaptioning`;
   - SHA-1: fingerprint của biến thể cần chạy.
4. Tạo **Web OAuth client**.
5. Dùng Web Client ID cho cả ứng dụng và biến `GOOGLE_WEB_CLIENT_ID` của backend.
6. Nếu có nhiều khóa ký, tạo Android OAuth client tương ứng cho từng SHA-1.

Sau khi thay client:

```powershell
Set-Location .\backend
docker compose up -d --force-recreate api

Set-Location ..\src
.\gradlew.bat clean :app:assembleDebug
```

Xóa dữ liệu ứng dụng trên thiết bị để tránh token cũ:

```powershell
adb shell pm clear com.klcn.mobilecaptioning
```

## 4. Đăng nhập bằng username hoặc email

API chấp nhận định danh đăng nhập theo hợp đồng backend. Trước khi kiểm thử:

1. xác nhận API trả về trạng thái khỏe tại `/health`;
2. đăng ký một tài khoản mới;
3. kiểm tra email/username trong PostgreSQL;
4. thử mật khẩu đúng và sai;
5. thử tài khoản đang hoạt động và tài khoản đã khóa;
6. xác nhận thông báo trên ứng dụng chỉ mô tả đúng kết quả thao tác.

Mật khẩu chỉ được lưu dưới dạng băm ở backend. Không ghi mật khẩu thô vào log.

## 5. Đặt lại mật khẩu qua email

Luồng hợp lệ:

1. người dùng nhập email đã tồn tại;
2. API tạo mã dùng một lần có thời hạn;
3. SMTP gửi mã tới email đó;
4. người dùng nhập mã, mật khẩu mới và xác nhận mật khẩu;
5. API kiểm tra mã rồi cập nhật mật khẩu đã băm;
6. mã được đánh dấu đã dùng hoặc hết hạn.

Áp dụng migration nếu cần:

```powershell
Set-Location .\backend
docker compose exec -T postgres psql `
  -U <POSTGRES_USER> `
  -d <POSTGRES_DB> `
  -f /docker-entrypoint-initdb.d/002_password_reset.sql
```

Thay placeholder bằng giá trị trong `.env`.

Kiểm tra log:

```powershell
docker compose logs -f api
```

Không hiển thị mã đặt lại mật khẩu trực tiếp trên giao diện trong môi trường phát hành.

## 6. Chọn địa chỉ API

| Môi trường | URL |
|---|---|
| Android Emulator | `http://10.0.2.2:8000` |
| Điện thoại USB với `adb reverse` | `http://127.0.0.1:8000` |
| Điện thoại cùng Wi-Fi | `http://<IPv4-máy-tính>:8000` |
| Phát hành | URL HTTPS công khai |

USB:

```powershell
adb reverse tcp:8000 tcp:8000
```

Không dùng `10.0.2.2` trên điện thoại thật.

## 7. Danh sách kiểm tra trước khi phát hành

- [ ] Dùng release keystore và bảo quản ngoài repository.
- [ ] Khai báo đúng SHA của release key hoặc Play App Signing.
- [ ] Dùng HTTPS.
- [ ] Đổi toàn bộ khóa mẫu trong `.env`.
- [ ] Không đóng gói khóa quản trị hoặc mật khẩu SMTP trong APK.
- [ ] Kiểm thử đăng ký, đăng nhập, Google Sign-In và đăng xuất.
- [ ] Kiểm thử mã hết hạn, mã sai và mã đã sử dụng.
- [ ] Kiểm thử email chưa đăng ký.
- [ ] Kiểm thử khóa/mở khóa và xóa tài khoản.
- [ ] Xác nhận log không chứa mật khẩu, mã đặt lại hoặc token đầy đủ.

## 8. Xử lý lỗi nhanh

### Google báo cấu hình không hợp lệ

- chạy lại `signingReport`;
- kiểm tra package name;
- kiểm tra SHA của đúng biến thể;
- kiểm tra Web Client ID ở Android và backend;
- chờ vài phút để cấu hình OAuth được cập nhật.

### Ứng dụng không kết nối API

- mở `/health` từ máy tính và điện thoại;
- kiểm tra URL theo loại thiết bị;
- chạy lại `adb reverse` khi dùng USB;
- kiểm tra tường lửa khi dùng Wi-Fi;
- xem `docker compose logs -f api`.

### Email không tới

- kiểm tra email đã tồn tại;
- kiểm tra SMTP host/port/TLS;
- dùng mật khẩu ứng dụng;
- kiểm tra thư rác;
- kiểm tra lỗi xác thực SMTP trong log API.
