# Cấu hình xác thực Vision Caption

## 1. Trạng thái hiện tại

- Đăng nhập thường: `username` hoặc `email` + mật khẩu.
- Đăng ký: họ tên, username duy nhất, email duy nhất, mật khẩu mạnh và xác
  nhận mật khẩu.
- Đăng nhập Google: Android Credential Manager + Google ID.
- Số điện thoại không còn là phương thức đăng nhập.
- Super Admin có RBAC, thống kê và nhật ký hoạt động của tất cả tài khoản.
- PostgreSQL phía backend là nguồn dữ liệu trung tâm cho tài khoản, quyền,
  audit log, lịch sử, yêu thích, tùy chọn và metadata ảnh.
- SQLite `vision_caption.db` chỉ là cache ngoại tuyến, tùy chọn thiết bị và
  hàng đợi đồng bộ. Backend luôn là nguồn quyết định quyền cuối cùng.
- Chế độ Khách chỉ được tạo chú thích và xem thông tin mô hình; không được lưu
  lịch sử hoặc yêu thích.

## 2. Cấu hình Google Sign-In cho bản debug

### Bước 1 — Tạo dự án và màn hình đồng ý OAuth

1. Mở Google Cloud Console, tạo hoặc chọn dự án dành riêng cho môi trường test.
2. Vào **Google Auth Platform** và cấu hình **Branding**, **Audience** và
   **Data Access**.
3. Với ứng dụng đang thử nghiệm, chọn trạng thái Testing và thêm tài khoản thử
   nghiệm nếu giao diện quản trị yêu cầu.
4. Chỉ dùng các scope cơ bản `openid`, `email`, `profile` cho đăng nhập.

### Bước 2 — Tạo Android OAuth client

Tạo client loại **Android** với:

- Package name: `com.klcn.mobilecaptioning`
- Debug SHA-1 trên máy hiện tại:
  `68:E8:18:49:9F:D9:3E:2F:53:3A:80:00:9B:02:D5:A0:96:EB:D0:B0`
- Debug SHA-256 trên máy hiện tại:
  `3D:9E:BB:03:9E:86:2F:06:68:0D:5F:0B:E0:60:33:E1:62:FE:5E:4F:77:CD:08:CF:4F:B3:13:E1:D6:EA:06:B3`

Có thể kiểm tra lại bằng PowerShell:

```powershell
& "$env:JAVA_HOME\bin\keytool.exe" -list -v `
  -alias androiddebugkey `
  -keystore "$env:USERPROFILE\.android\debug.keystore" `
  -storepass android -keypass android
```

Khi ký APK/AAB phát hành, tạo thêm Android OAuth client cho **release SHA-1**.
Nếu dùng Play App Signing, cũng phải khai báo SHA-1 của App Signing certificate
trên Play Console; SHA của upload key không thay thế cho SHA này.

### Bước 3 — Tạo Web OAuth client

1. Trong cùng project, tạo OAuth client loại **Web application**.
2. Sao chép **Web Client ID** (không dùng Android Client ID).
3. Mở `app/src/main/res/values/strings.xml` và thay:

```xml
<string name="google_web_client_id">WEB_CLIENT_ID.apps.googleusercontent.com</string>
```

Mã nguồn truyền giá trị này vào `GetSignInWithGoogleOption.Builder(...)`, đúng
luồng dành cho nút **Đăng nhập bằng Google**. Android Client ID chỉ dùng để
Google đối chiếu package/SHA; không đặt Android Client ID vào `strings.xml`.

### Bước 4 — Build và kiểm thử

1. Sync Gradle, Clean/Rebuild project rồi cài lại ứng dụng.
2. Nếu vừa thay OAuth client, gỡ bản APK cũ, cài lại và chờ Google đồng bộ cấu
   hình (thường vài phút, đôi khi vài giờ).
3. Chọn **Đăng nhập bằng Google**.
4. Kiểm thử: tài khoản mới, tài khoản đã cấp quyền, hủy bottom sheet, thiết bị
   có nhiều tài khoản và trường hợp không có Google Play services.
5. Nếu consent screen ở trạng thái **Testing**, thêm đúng email trên emulator
   vào **Audience → Test users** và bảo đảm lời nhắc Sign in with Google không
   bị tắt trong cài đặt tài khoản Google.

## 3. Yêu cầu production

1. Dùng project OAuth riêng cho production.
2. Cấu hình tên, logo, email hỗ trợ, trang chủ, chính sách riêng tư và điều
   khoản dịch vụ đúng với thương hiệu Vision Caption.
3. Xác minh domain và hoàn tất brand verification khi phát hành công khai.
4. Gửi Google ID token đến backend qua HTTPS. Backend phải xác minh chữ ký,
   `aud`, `iss`, `exp` và nonce trước khi cấp session riêng của hệ thống.
5. Không hard-code tài khoản/mật khẩu Super Admin trong APK production.
6. Lưu mật khẩu bằng thuật toán thích hợp phía server; áp dụng rate limit,
   khóa tạm thời, xác minh email và quy trình khôi phục qua email.
7. Ghi audit log bất biến cho đăng nhập, tạo mô tả, thay đổi quyền và thao tác
   quản trị; tránh ghi token, mật khẩu hoặc dữ liệu bí mật.

## 4. Checklist nghiệm thu

- Username và email không trùng, kiểm tra không phân biệt hoa thường.
- Mọi trường đăng ký đều được validate; mật khẩu tối thiểu 8 ký tự có chữ hoa,
  chữ thường và số.
- Quên mật khẩu nằm ngay dưới ô mật khẩu và chỉ nhận username/email.
- Google Sign-In hoạt động với debug SHA và release/Play Signing SHA.
- Guest không có quyền lưu lịch sử hoặc yêu thích.
- Mỗi user chỉ đọc/ghi lịch sử, yêu thích và tùy chọn gắn với `user_id` của họ.
- Super Admin xem được tổng quan, hoạt động theo user và không thể bị xóa.
- API backend vẫn từ chối thao tác trái quyền dù UI bị can thiệp.

## 5. Tài liệu chính thức

- Android Credential Manager — Sign in with Google:
  https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation
- Google Cloud — quản lý OAuth client cho Android:
  https://support.google.com/cloud/answer/15549257
- Google OAuth production readiness:
  https://developers.google.com/identity/protocols/oauth2/production-readiness/policy-compliance
- Google OAuth brand verification:
  https://developers.google.com/identity/protocols/oauth2/production-readiness/brand-verification
