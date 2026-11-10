# Mobile Image Captioning

[English version](README_EN.md)

Ứng dụng Android nghiên cứu về sinh chú thích ảnh trên thiết bị di động. Ứng dụng cho phép chọn ảnh từ thư viện hoặc máy ảnh, sinh chú thích tiếng Anh bằng ONNX Runtime, dịch sang tiếng Việt, đọc nội dung bằng giọng nói và lưu lịch sử trên thiết bị. Các chức năng tài khoản sử dụng API FastAPI và PostgreSQL khi người dùng cấu hình máy chủ.

Phiên bản hiện tại: **1.7.0** (`versionCode 8`).

> Đây là mini-app phục vụ nghiên cứu và kiểm chứng triển khai. Các chỉ số thực nghiệm trong giao diện là số liệu tham chiếu của từng checkpoint; chúng không thay thế việc đánh giá lại trên thiết bị và dữ liệu mục tiêu.

## 1. Chức năng chính

- Sinh chú thích ảnh ngoại tuyến bằng ONNX Runtime Android.
- Chọn giữa các mô hình chính và mô hình thử nghiệm.
- Hiển thị tập dữ liệu, phương pháp chưng cất tri thức, checkpoint, số tham số và CIDEr-D.
- Dịch chú thích Anh–Việt bằng ML Kit.
- Đọc chú thích bằng Text-to-Speech theo ngôn ngữ đang chọn.
- Lưu lịch sử, mục yêu thích và ảnh trên thiết bị.
- Chia sẻ ảnh kèm chú thích.
- Giao diện tiếng Việt và tiếng Anh; hỗ trợ chế độ sáng, tối và theo thiết bị.
- Đăng ký, đăng nhập, Google Sign-In, quên mật khẩu và quản trị tài khoản qua API.

## 2. Kiến trúc thư mục

Sau khi sao chép repository, các đường dẫn quan trọng là:

```text
image-captioning-distillation/
├── src/                    # Ứng dụng Android
│   ├── app/
│   ├── notebooks/          # Notebook đã chọn để tái lập nghiên cứu
│   ├── scripts/            # Script kiểm thử và benchmark
│   └── README.md
├── backend/                # FastAPI, PostgreSQL và Docker Compose
│   ├── app/
│   ├── sql/
│   ├── docker-compose.yml
│   └── README.md
└── ...
```

Trong tài liệu này:

- `<repo>` là thư mục gốc của repository.
- `<android>` là `<repo>/src`.
- `<backend>` là `<repo>/backend`.

Không cần đổi tên thư mục hoặc đặt repository ở một ổ đĩa cố định.

## 3. Yêu cầu cài đặt

### 3.1. Cho ứng dụng Android

- Windows 10/11, macOS hoặc Linux.
- Android Studio có Android SDK 36.
- JDK 23 hoặc JDK do Android Studio quản lý tương thích với Gradle toolchain 23.
- Git và Git LFS.
- Thiết bị Android API 26 trở lên hoặc Android Emulator.

### 3.2. Cho API

- Docker Desktop có Docker Compose v2.
- PowerShell 7 trên Windows nếu dùng script `setup.ps1`.
- Một địa chỉ email SMTP nếu muốn gửi mã quên mật khẩu thật.

### 3.3. Phiên bản kỹ thuật chính

| Thành phần | Phiên bản |
|---|---:|
| Android compile/target SDK | 36 |
| Android min SDK | 26 |
| Gradle Wrapper | 9.6.0 |
| Gradle JVM toolchain | 23 |
| Kotlin | 2.2.10 |
| Jetpack Compose BOM | 2026.02.01 |
| ONNX Runtime Android | 1.29.0 |
| ML Kit Translate | 17.0.3 |
| Python trong Docker | 3.12 |
| PostgreSQL trong Docker | 18 |

## 4. Tải mã nguồn

Mở terminal trong thư mục bạn muốn lưu dự án:

```powershell
git clone https://github.com/danhhuit/image-captioning-distillation.git
Set-Location image-captioning-distillation
git lfs install
git lfs pull
```

Kiểm tra các tệp mô hình lớn:

```powershell
git lfs ls-files
```

Nếu một tệp `.onnx` chỉ có vài byte hoặc bắt đầu bằng dòng `version https://git-lfs.github.com`, Git LFS chưa tải nội dung thật. Chạy lại `git lfs pull`.

## 5. Khởi động API bằng Docker

### 5.1. Cách nhanh trên Windows

Từ thư mục gốc của repository:

```powershell
Set-Location .\backend
.\setup.ps1
```

Script sẽ:

1. tạo `.env` từ `.env.example` nếu chưa có;
2. sinh khóa bí mật ngẫu nhiên cho môi trường cục bộ;
3. build image API;
4. khởi động PostgreSQL và FastAPI;
5. áp dụng migration cho chức năng đặt lại mật khẩu;
6. kiểm tra endpoint sức khỏe.

Kiểm tra trạng thái:

```powershell
docker compose ps
docker compose logs -f api
```

Mở tài liệu API:

```text
http://localhost:8000/docs
```

Kiểm tra sức khỏe:

```powershell
Invoke-RestMethod http://localhost:8000/health
```

### 5.2. Cách thủ công trên mọi nền tảng

```bash
cd backend
cp .env.example .env
docker compose up -d --build
docker compose ps
```

Trên PowerShell, dùng:

```powershell
Set-Location .\backend
Copy-Item .env.example .env
docker compose up -d --build
docker compose ps
```

Trước khi dùng ngoài máy cá nhân, mở `.env` và thay toàn bộ mật khẩu, khóa JWT, khóa quản trị và thông tin SMTP.

### 5.3. Dừng hoặc làm mới container

```powershell
docker compose down
docker compose up -d
```

Chỉ dùng lệnh sau khi chấp nhận xóa dữ liệu PostgreSQL cục bộ:

```powershell
docker compose down -v
```

## 6. Cấu hình địa chỉ API cho Android

Ứng dụng đọc `BuildConfig.CENTRAL_API_BASE_URL`. Giá trị mặc định dành cho Android Emulator là:

```text
http://10.0.2.2:8000
```

`10.0.2.2` là địa chỉ đặc biệt để máy ảo Android truy cập `localhost` của máy tính. Địa chỉ này không dùng cho điện thoại thật.

### 6.1. Android Emulator

Giữ giá trị mặc định, khởi động API và kiểm tra:

```powershell
adb shell ping -c 1 10.0.2.2
```

### 6.2. Điện thoại kết nối USB

Cách ổn định nhất khi phát triển:

```powershell
adb devices
adb reverse tcp:8000 tcp:8000
```

Sau đó đặt API URL thành:

```text
http://127.0.0.1:8000
```

Lệnh `adb reverse` cần chạy lại sau khi ngắt cáp, khởi động lại thiết bị hoặc khởi động lại ADB.

### 6.3. Điện thoại và máy tính cùng Wi-Fi

1. Tìm địa chỉ IPv4 của máy tính.
2. Bảo đảm API lắng nghe trên `0.0.0.0:8000`.
3. Cho phép cổng TCP 8000 qua tường lửa trong mạng riêng.
4. Đặt URL thành `http://<IP-máy-tính>:8000`.
5. Mở URL `/health` bằng trình duyệt trên điện thoại trước khi chạy ứng dụng.

Ví dụ cấu hình Gradle cục bộ:

```properties
CENTRAL_API_BASE_URL=http://192.168.1.20:8000
```

Không commit địa chỉ mạng riêng, mật khẩu hoặc khóa bí mật vào Git.

## 7. Cấu hình `.env`

Sao chép tệp mẫu:

```powershell
Set-Location .\backend
Copy-Item .env.example .env
```

Các nhóm biến quan trọng:

| Nhóm | Biến tiêu biểu | Mục đích |
|---|---|---|
| PostgreSQL | `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Cơ sở dữ liệu |
| API | `API_BIND_HOST`, `API_PORT` | Địa chỉ và cổng API |
| Xác thực | `JWT_SECRET`, `ADMIN_PASSWORD` | Ký token và tài khoản quản trị ban đầu |
| Google | `GOOGLE_WEB_CLIENT_ID` | Kiểm tra Google ID token |
| Email | `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` | Gửi mã đặt lại mật khẩu |

Yêu cầu tối thiểu cho môi trường dùng chung:

- `POSTGRES_PASSWORD`, `JWT_SECRET` và `ADMIN_PASSWORD` phải là chuỗi mạnh, khác nhau.
- Không dùng giá trị mẫu trong môi trường công khai.
- Chỉ bật SMTP sau khi tạo mật khẩu ứng dụng hoặc thông tin xác thực riêng cho dịch vụ email.
- Không commit `.env`.

Xem hướng dẫn đầy đủ tại [backend/README.md](../backend/README.md).

## 8. Google Sign-In

Google Sign-In cần đúng `package name`, SHA-1/SHA-256 của khóa ký và Web Client ID.

Lấy fingerprint của bản debug trên máy của bạn:

```powershell
Set-Location .\src
.\gradlew.bat signingReport
```

Sau đó:

1. tạo hoặc chọn dự án trong Google Cloud Console;
2. cấu hình OAuth consent screen;
3. tạo Android OAuth client cho package `com.klcn.mobilecaptioning`;
4. nhập SHA-1 của biến thể đang chạy;
5. tạo Web OAuth client;
6. đặt Web Client ID vào cấu hình Android và `GOOGLE_WEB_CLIENT_ID` của backend;
7. build lại ứng dụng và khởi động lại API.

Không sao chép fingerprint từ máy của người phát triển khác. Bản release phải dùng fingerprint của release keystore.

Xem [AUTH_PRODUCTION_SETUP.md](AUTH_PRODUCTION_SETUP.md).

## 9. Quên mật khẩu qua email

Luồng đặt lại mật khẩu gồm:

1. người dùng nhập email đã tồn tại;
2. API tạo mã dùng một lần có thời hạn;
3. mã được gửi tới đúng email qua SMTP;
4. người dùng nhập mã, mật khẩu mới và xác nhận mật khẩu;
5. API kiểm tra mã rồi cập nhật mật khẩu đã băm.

Kiểm tra migration:

```powershell
Set-Location .\backend
docker compose exec -T postgres psql -U vision_app -d vision_caption -f /docker-entrypoint-initdb.d/002_password_reset.sql
```

Tên người dùng và cơ sở dữ liệu thực tế phải khớp với `.env`.

## 10. Build và chạy ứng dụng

### 10.1. Android Studio

1. Mở thư mục `src`, không mở toàn bộ repository.
2. Chờ Gradle Sync hoàn tất.
3. Chọn thiết bị hoặc máy ảo.
4. Chọn cấu hình `app`.
5. Nhấn **Run**.

### 10.2. Terminal

Trên Windows:

```powershell
Set-Location .\src
.\gradlew.bat clean :app:assembleDebug
.\gradlew.bat :app:installDebug
```

Trên macOS/Linux:

```bash
cd src
./gradlew clean :app:assembleDebug
./gradlew :app:installDebug
```

APK debug nằm tại:

```text
src/app/build/outputs/apk/debug/app-debug.apk
```

Build release:

```powershell
Set-Location .\src
.\gradlew.bat :app:assembleRelease
```

APK release chỉ nên phân phối sau khi đã cấu hình khóa ký, API URL HTTPS và các khóa OAuth của môi trường phát hành.

## 11. Sử dụng ứng dụng

1. Mở tab **Chụp**.
2. Chọn **Mô hình chính** hoặc **Mô hình thử nghiệm**.
3. Mở danh sách và chọn checkpoint.
4. Chọn ảnh từ thư viện hoặc chụp ảnh mới.
5. Chờ chú thích tiếng Anh và bản dịch tiếng Việt.
6. Dùng nút loa để đọc nội dung hoặc nút chia sẻ để gửi ảnh kèm chú thích.
7. Dùng **Lịch sử** và **Yêu thích** để xem lại kết quả.

Tài khoản chỉ cần thiết cho các chức năng đồng bộ với API. Suy luận ngoại tuyến vẫn hoạt động ở chế độ khách nếu gói mô hình đã có trong APK.

## 12. Danh mục mô hình

### 12.1. Mô hình chính

| Teacher → Student | Dữ liệu | Phương pháp | CIDEr-D cơ sở → KD |
|---|---|---|---:|
| ExpansionNet v2 → MobileNetV3-Large | MS COCO 2014 | Sequence KD | 98.58 → 110.92 |
| ExpansionNet v2 → MobileNetV3-Large | MS COCO 2014 | Dense-Mapped Logit-KD | 98.58 → 108.45 |
| ExpansionNet v2 → MobileNetV3-Large | MS COCO 2014 | Hybrid Sequence + Dense-Mapped Logit-KD | 98.58 → 109.95 |
| BLIP Base → MobileViT-S | MS COCO 2014 | Sequence KD | 99.32 → 102.07 |
| SigLIP ViT-B/16 → TinyViT-5M | MS COCO 2014 | Direct Logit-KD | 107.48 → 109.40 |

### 12.2. Mô hình thử nghiệm

| Teacher → Student | Dữ liệu | Phương pháp | CIDEr-D cơ sở → KD |
|---|---|---|---:|
| V1 FP32 | MS COCO 2014 | Mô hình tham chiếu | — |
| CLIP ViT-B/32 → MobileNetV3-Large | MS COCO 2014 | Direct Logit-KD | 98.73 → 100.73 |
| ExpansionNet v2 → MobileNetV3-Large | Flickr30K | Sequence KD | 47.62 → 38.49 |
| ExpansionNet v2 → MobileNetV3-Large | Flickr30K | Dense-Mapped Logit-KD | 47.62 → 36.93 |
| ExpansionNet v2 → MobileNetV3-Large | Flickr30K | Hybrid KD | 47.70 → 38.07 |
| BLIP Base → MobileViT-S | Flickr30K | Sequence KD | 50.10 → 42.33 |
| SigLIP ViT-B/16 → TinyViT-5M | Flickr30K | Direct Logit-KD | 57.07 → 61.71 |

Các nhánh Flickr30K có điểm KD thấp hơn baseline vẫn được giữ trong nhóm thử nghiệm để phục vụ phân tích, không được trình bày như một cải thiện.

## 13. Kiểm thử

Unit test:

```powershell
Set-Location .\src
.\gradlew.bat test
```

Instrumentation test trên máy ảo hoặc thiết bị:

```powershell
.\gradlew.bat connectedAndroidTest
```

Xem thiết bị:

```powershell
adb devices
```

Benchmark máy ảo chỉ dùng để kiểm tra luồng chạy. Không dùng độ trễ, RAM hoặc nhiệt độ từ máy ảo làm số liệu khoa học cho thiết bị thật.

## 14. Kết nối PostgreSQL bằng DBeaver

Với cấu hình Docker mặc định:

| Trường | Giá trị |
|---|---|
| Host | `localhost` |
| Port | giá trị `POSTGRES_PORT`, mặc định `15432` |
| Database | giá trị `POSTGRES_DB` |
| Username | giá trị `POSTGRES_USER` |
| Password | giá trị `POSTGRES_PASSWORD` |

Các bước:

1. Chọn **New Database Connection**.
2. Chọn **PostgreSQL**.
3. Nhập các giá trị từ `.env`.
4. Nhấn **Test Connection**.
5. Chấp nhận tải driver nếu DBeaver yêu cầu.
6. Mở schema `public` để xem bảng.

Không mở cổng PostgreSQL ra Internet. Cấu hình mặc định chỉ bind cổng DB vào `127.0.0.1`.

## 15. Sao lưu và khôi phục

Sao lưu:

```powershell
Set-Location .\backend
docker compose exec -T postgres pg_dump -U vision_app -d vision_caption > backup.sql
```

Khôi phục:

```powershell
Get-Content .\backup.sql | docker compose exec -T postgres psql -U vision_app -d vision_caption
```

Thay tên người dùng và cơ sở dữ liệu theo `.env` nếu đã đổi cấu hình.

## 16. Lỗi thường gặp

### Ứng dụng báo “Không thể kết nối”

- Kiểm tra `docker compose ps`.
- Mở `/health` từ máy tính.
- Với Emulator, dùng `10.0.2.2`, không dùng `localhost`.
- Với USB, chạy lại `adb reverse tcp:8000 tcp:8000`.
- Với Wi-Fi, kiểm tra IP, tường lửa và cùng mạng.
- Build lại ứng dụng sau khi đổi API URL.

### Google Sign-In báo sai thông tin

- Kiểm tra package name.
- Chạy `signingReport` và cập nhật đúng SHA.
- Kiểm tra Web Client ID ở cả Android và backend.
- Xóa dữ liệu ứng dụng sau khi thay OAuth client.

### Quên mật khẩu không gửi email

- Kiểm tra email tồn tại trong PostgreSQL.
- Kiểm tra biến SMTP và log API.
- Dùng mật khẩu ứng dụng thay vì mật khẩu tài khoản email chính.
- Kiểm tra thư rác và thời hạn của mã.

### Model không tải được

- Chạy `git lfs pull`.
- Kiểm tra tên asset và manifest.
- Không đổi tên riêng từng tệp ONNX nếu chưa cập nhật registry.

### Màn hình đen hoặc ứng dụng bị dừng

```powershell
adb logcat -c
adb logcat | Select-String "AndroidRuntime|mobilecaptioning"
```

Gửi phần stack trace đầu tiên khi báo lỗi.

## 17. Tài liệu bổ sung

- [Cấu hình xác thực](AUTH_PRODUCTION_SETUP.md)
- [Kiến trúc dữ liệu](DATABASE_ARCHITECTURE.md)
- [Lộ trình sản phẩm di động](MOBILE_UX_PRODUCT_ROADMAP.md)
- [Danh mục notebook](notebooks/README.md)
- [Hướng dẫn backend](../backend/README.md)

## 18. Bảo mật

- Không commit `.env`, keystore, mật khẩu, token hoặc khóa API.
- Không dùng HTTP cho môi trường phát hành.
- Không đưa khóa quản trị vào APK.
- Thay toàn bộ khóa mẫu trước khi triển khai.
- Chỉ thu thập dữ liệu cần thiết và thông báo rõ cho người dùng.
- Kiểm thử luồng xóa tài khoản, khóa tài khoản và đặt lại mật khẩu trước khi phát hành.

## 19. Giấy phép và trích dẫn

Repository chứa mã nghiên cứu, checkpoint và tài liệu có thể chịu các giấy phép khác nhau. Hãy kiểm tra giấy phép của từng mô hình, tập dữ liệu và thư viện trước khi phân phối lại hoặc sử dụng thương mại.
