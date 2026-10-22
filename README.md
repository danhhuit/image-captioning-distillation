# Vision Caption

Ứng dụng Android sinh chú thích tiếng Anh cho ảnh bằng các mô hình Image
Captioning chạy trực tiếp trên thiết bị. Vision Caption hỗ trợ ảnh từ thư viện
hoặc camera, dịch EN→VI trên thiết bị, đọc chú thích bằng giọng Anh/Việt,
chia sẻ ảnh đã ghép chú thích, nhiều mô hình ONNX tối ưu cho mobile, lịch sử
và yêu thích theo từng tài khoản, giao diện Việt/Anh, chế độ sáng/tối, đăng
nhập Google và màn hình quản trị người dùng.

Phiên bản Android hiện tại: **1.7.0**.

> Đây là sản phẩm nghiên cứu. Các chỉ số hiển thị trong ứng dụng là kết quả
> Validation, không phải phần trăm độ chính xác. Chất lượng caption có thể thay
> đổi theo ảnh và mô hình được chọn.

## Mục lục

1. [Tính năng chính](#1-tính-năng-chính)
2. [Kiến trúc hệ thống](#2-kiến-trúc-hệ-thống)
3. [Yêu cầu máy phát triển](#3-yêu-cầu-máy-phát-triển)
4. [Yêu cầu thiết bị Android](#4-yêu-cầu-thiết-bị-android)
5. [Cấu trúc mã nguồn](#5-cấu-trúc-mã-nguồn)
6. [Cài đặt nhanh](#6-cài-đặt-nhanh)
7. [Thiết lập Docker, PostgreSQL và FastAPI](#7-thiết-lập-docker-postgresql-và-fastapi)
8. [Cấu hình Android kết nối backend](#8-cấu-hình-android-kết-nối-backend)
9. [Cấu hình đăng nhập Google](#9-cấu-hình-đăng-nhập-google)
10. [Build và cài ứng dụng](#10-build-và-cài-ứng-dụng)
11. [Hướng dẫn sử dụng](#11-hướng-dẫn-sử-dụng)
12. [Tài khoản và phân quyền](#12-tài-khoản-và-phân-quyền)
13. [Mô hình có trong ứng dụng](#13-mô-hình-có-trong-ứng-dụng)
14. [Dữ liệu và chế độ ngoại tuyến](#14-dữ-liệu-và-chế-độ-ngoại-tuyến)
15. [Kiểm thử](#15-kiểm-thử)
16. [Vận hành Docker](#16-vận-hành-docker)
17. [Kết nối PostgreSQL bằng DBeaver](#17-kết-nối-postgresql-bằng-dbeaver)
18. [Xử lý lỗi thường gặp](#18-xử-lý-lỗi-thường-gặp)
19. [Bảo mật và giới hạn hiện tại](#19-bảo-mật-và-giới-hạn-hiện-tại)

## 1. Tính năng chính

- Sinh caption từ ảnh trong thư viện hoặc ảnh chụp bằng camera.
- Inference ngoại tuyến trên thiết bị với ONNX Runtime Android.
- Bốn lựa chọn runtime:
  - V1 FP32 fallback;
  - T1 → S1 INT8;
  - T2 → S2 INT8;
  - T3 → S3 INT8.
- Beam Search với beam size 3.
- Dịch caption tiếng Anh sang tiếng Việt bằng ML Kit on-device; lần đầu cần
  mạng để tải language pack, các lần sau có thể dịch ngoại tuyến.
- Đọc caption tiếng Anh và bản dịch tiếng Việt bằng Android Text-to-Speech.
- Chia sẻ một ảnh tổng hợp gồm ảnh nguồn, caption tiếng Anh và bản dịch Việt.
- Xem tensor contract, preprocessing, latency và thông tin mô hình.
- Đăng nhập bằng username/email và mật khẩu.
- Đăng nhập Google bằng Android Credential Manager.
- Chế độ Khách không bắt buộc đăng nhập.
- Lịch sử và yêu thích tách biệt theo từng tài khoản.
- PostgreSQL là nguồn dữ liệu trung tâm; SQLite là cache/offline store.
- Đồng bộ lại dữ liệu khi thiết bị có kết nối.
- RBAC theo tài nguyên và thao tác.
- Super Admin có thể:
  - tìm kiếm, lọc và phân trang danh sách người dùng;
  - khóa/mở khóa tài khoản;
  - cập nhật họ tên hoặc đặt lại mật khẩu người dùng;
  - cấu hình quyền;
  - xem nhật ký hoạt động toàn hệ thống.
- Giao diện tiếng Việt/English và Light/Dark/System theme.
- Thanh điều hướng 5 mục: Lịch sử, Yêu thích, Chụp, Mô hình và Cá nhân.

## 2. Kiến trúc hệ thống

```text
Ảnh từ Camera/Thư viện
          │
          ▼
 Android App — Jetpack Compose
          │
          ├── ONNX Runtime Android
          │     ├── Encoder
          │     ├── Visual memory
          │     ├── Transformer Decoder
          │     └── Beam Search
          │
          ├── SQLite: cache, tùy chọn, sync outbox
          │
          └── HTTPS/HTTP development API
                    │
                    ▼
               FastAPI
                    │
                    ▼
               PostgreSQL
```

Nguyên tắc dữ liệu:

- Ảnh đầy đủ được xử lý trực tiếp trên Android.
- Tài khoản đăng nhập có thể đồng bộ caption, metadata và thumbnail được phép
  lên backend.
- Backend xác định user từ JWT, không tin `user_id` do client gửi.
- Dữ liệu của mỗi tài khoản được phân tách bằng `user_id`.
- Guest chỉ tạo caption và xem thông tin mô hình; không lưu lịch sử hoặc yêu
  thích.
- APK không kết nối trực tiếp PostgreSQL và không chứa mật khẩu database.

## 3. Yêu cầu máy phát triển

### Bắt buộc

| Thành phần | Yêu cầu |
|---|---|
| Hệ điều hành | Windows 10/11 64-bit |
| Git | Git for Windows |
| Android Studio | Bản hỗ trợ Android Gradle Plugin 9.4 |
| Android SDK | Compile SDK 36 |
| JDK | JDK 17 |
| Docker | Docker Desktop có Docker Compose v2 |
| Ảo hóa | Bật virtualization; Docker Desktop dùng WSL 2 |
| Dung lượng trống | Tối thiểu 10 GB cho SDK, Docker images và build cache |

### Khuyến nghị

- CPU 4 nhân/8 luồng trở lên.
- RAM máy tính 16 GB trở lên.
- Ổ SSD.
- Dùng Git LFS để tải/lưu các model `.onnx`.
- Android Emulator image có Google Play nếu kiểm thử Google Sign-In.

Kiểm tra nhanh:

```powershell
git --version
git lfs version
java -version
docker --version
docker compose version
adb version
```

Project sử dụng Gradle Wrapper nên không cần cài Gradle toàn hệ thống.

## 4. Yêu cầu thiết bị Android

Thông số được khóa trong project:

| Thuộc tính | Giá trị |
|---|---|
| Minimum Android | Android 8.0 — API 26 |
| Target/Compile SDK | API 36 |
| Camera | Không bắt buộc; có thể chỉ dùng thư viện |
| Kết nối mạng | Cần cho tài khoản/đồng bộ; inference vẫn chạy trên máy |
| Google Play services | Cần nếu đăng nhập Google |
| Text-to-Speech | Cần cài giọng English và Vietnamese để đọc cả hai ngôn ngữ |

Thiết bị khuyến nghị:

- Android 11 trở lên.
- Kiến trúc 64-bit.
- RAM 4 GB trở lên.
- Còn trống ít nhất 500 MB để cài APK, giải nén native libraries, cache và dữ
  liệu người dùng.
- Thiết bị thật nên có Google Play services cập nhật.

Cấu hình emulator đề xuất:

```text
Device profile : Pixel 7 hoặc tương đương
System image   : API 35/36, Google Play, x86_64
RAM            : 4 GB
Internal disk  : 6 GB trở lên
Graphics       : Hardware/Automatic
Camera         : Webcam hoặc Virtual Scene
```

Nếu máy phát triển ít RAM, đóng các emulator không sử dụng và chỉ nạp một mô
hình trong ứng dụng tại một thời điểm.

## 5. Cấu trúc mã nguồn

Workspace phát triển hiện tại gồm hai thư mục ngang hàng:

```text
D:\KLCN\
├── src\                         # Android repository này
│   ├── app\
│   │   └── src\main\
│   │       ├── assets\          # ONNX models, vocabulary, contracts
│   │       ├── java\            # Kotlin/Compose/runtime/auth/storage
│   │       └── res\             # Strings, icons, XML, theme
│   ├── gradle\
│   ├── tools\
│   └── README.md
│
└── backend\                     # Backend companion
    ├── app\main.py              # FastAPI
    ├── sql\001_schema.sql       # PostgreSQL schema
    ├── docker-compose.yml
    ├── Dockerfile
    ├── setup.ps1
    └── .env.example
```

Nếu chỉ clone repository Android, cần lấy thêm backend companion từ chủ dự án
và đặt tại `D:\KLCN\backend`, hoặc điều chỉnh các lệnh trong tài liệu theo vị
trí backend thực tế.

Các file Android quan trọng:

| File | Vai trò |
|---|---|
| `MainActivity.kt` | Điều phối chọn ảnh và inference |
| `Phase7KV2CaptionEngine.kt` | Runtime ba mô hình V2 INT8 |
| `VisionCaptionApp.kt` | UI Jetpack Compose và điều hướng |
| `AuthModels.kt` | Đăng nhập, Google Identity và session |
| `CentralApiClient.kt` | Gọi FastAPI/JWT |
| `VisionDatabase.kt` | SQLite cache và offline store |
| `AppStorage.kt` | Lịch sử, tùy chọn và sync outbox |

## 6. Cài đặt nhanh

### Bước 1 — Clone và lấy model assets

```powershell
git clone https://github.com/danhhuit/image-captioning-distillation.git
cd image-captioning-distillation
git lfs install
git lfs pull
```

Kiểm tra các file model:

```powershell
Get-ChildItem .\app\src\main\assets\v2 -Recurse -Filter *.onnx
```

Phải có đủ sáu file encoder/decoder trong:

```text
app/src/main/assets/v2/t1_s1/
app/src/main/assets/v2/t2_s2/
app/src/main/assets/v2/t3_s3/
```

Nếu file `.onnx` chỉ có vài trăm byte và chứa nội dung Git LFS pointer, chạy
lại `git lfs pull`.

### Bước 2 — Khởi động backend

```powershell
cd "D:\KLCN\backend"
Set-ExecutionPolicy -Scope Process Bypass
.\setup.ps1
```

### Bước 3 — Kiểm tra API

```powershell
Invoke-RestMethod http://127.0.0.1:8000/health
```

Kết quả:

```text
status database
------ --------
ok     postgresql
```

### Bước 4 — Build Android

```powershell
cd "D:\KLCN\src"
.\gradlew.bat :app:assembleDebug
```

### Bước 5 — Cài APK

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

Với emulator, giữ cấu hình API mặc định:

```xml
<string name="central_api_base_url">http://10.0.2.2:8000</string>
```

## 7. Thiết lập Docker, PostgreSQL và FastAPI

### 7.1 Cài Docker Desktop

1. Cài Docker Desktop.
2. Bật virtualization trong BIOS/UEFI nếu Docker yêu cầu.
3. Bật WSL 2 backend.
4. Khởi động Docker Desktop.
5. Chờ trạng thái Docker Engine sẵn sàng.

Kiểm tra:

```powershell
docker info
docker compose version
```

### 7.2 Khởi tạo backend lần đầu

```powershell
cd "D:\KLCN\backend"
Set-ExecutionPolicy -Scope Process Bypass
.\setup.ps1
```

Script sẽ:

1. kiểm tra Docker Engine;
2. tạo `.env` từ `.env.example`;
3. sinh ngẫu nhiên mật khẩu PostgreSQL, JWT secret và mật khẩu Admin;
4. build FastAPI image;
5. chạy PostgreSQL;
6. áp dụng `sql/001_schema.sql` với volume mới;
7. chạy API và đợi health check.

Không commit file `.env`.

### 7.3 Kiểm tra container

```powershell
docker compose ps
```

Kết quả mong muốn:

```text
postgres   healthy
api        healthy
```

Kiểm tra chi tiết:

```powershell
Invoke-RestMethod http://127.0.0.1:8000/health
docker compose exec postgres psql `
  -U vision_app `
  -d vision_caption `
  -c "\dt"
```

Swagger/OpenAPI:

```text
http://127.0.0.1:8000/docs
```

### 7.4 Cấu hình `.env`

File `D:\KLCN\backend\.env` có dạng:

```dotenv
POSTGRES_DB=vision_caption
POSTGRES_USER=vision_app
POSTGRES_PASSWORD=<database-password>
POSTGRES_PORT=15432

API_PORT=8000
JWT_SECRET=<at-least-32-random-bytes>
GOOGLE_WEB_CLIENT_ID=<web-client-id>.apps.googleusercontent.com

ADMIN_EMAIL=superadmin@visioncaption.app
ADMIN_USERNAME=superadmin
ADMIN_PASSWORD=<admin-password>

CORS_ORIGINS=*
```

Phân biệt:

- `POSTGRES_PASSWORD`: chỉ dùng cho PostgreSQL/DBeaver.
- `ADMIN_PASSWORD`: dùng đăng nhập Super Admin trong app.
- `JWT_SECRET`: chỉ backend sử dụng.
- `GOOGLE_WEB_CLIENT_ID`: phải trùng Web Client ID trong Android.

Sau khi sửa `.env`:

```powershell
docker compose up -d --build
docker compose ps
```

### 7.5 Cổng mặc định

| Dịch vụ | Địa chỉ trên máy phát triển |
|---|---|
| FastAPI | `127.0.0.1:8000` |
| PostgreSQL | `127.0.0.1:15432` |
| PostgreSQL trong Docker network | `postgres:5432` |
| API từ Android Emulator | `10.0.2.2:8000` |

PostgreSQL không được ứng dụng Android truy cập trực tiếp.

## 8. Cấu hình Android kết nối backend

Địa chỉ mặc định nằm tại:

```text
app/src/main/res/values/strings.xml
```

### 8.1 Android Emulator

Giữ:

```xml
<string name="central_api_base_url">http://10.0.2.2:8000</string>
```

`10.0.2.2` là địa chỉ đặc biệt để emulator truy cập loopback của máy host.
Ứng dụng 1.7.0 cũng cho phép đổi URL tại **Thêm → Kết nối máy chủ**, không cần
sửa mã nguồn và build lại.

### 8.2 Thiết bị thật qua USB — khuyến nghị khi phát triển

Kết nối thiết bị, bật USB debugging rồi chạy:

```powershell
adb devices
adb reverse tcp:8000 tcp:8000
```

Đổi tạm URL trong `strings.xml`:

```xml
<string name="central_api_base_url">http://localhost:8000</string>
```

Build và cài lại APK. Cách này không cần công khai API ra mạng LAN.

Sau khi ngắt USB hoặc reboot thiết bị, có thể cần chạy lại `adb reverse`.

### 8.3 Thiết bị thật qua Wi-Fi/LAN

Chỉ dùng trong mạng phát triển tin cậy:

1. Đặt `API_BIND_HOST=0.0.0.0` trong `D:\KLCN\backend\.env` và chạy
   `docker compose up -d --build`.
2. Mở đúng port trên Windows Firewall.
3. Trên điện thoại mở **Thêm → Kết nối máy chủ**, nhập IP máy tính, ví dụ
   `http://192.168.1.10:8000`, rồi chọn **Lưu và kiểm tra**.
4. Mở cùng URL `/health` trên trình duyệt điện thoại để xác nhận đường truyền.

Debug build có cấu hình network riêng cho HTTP LAN. Main/release config vẫn
chặn cleartext để tránh vô tình phát hành bản không an toàn.

Production phải dùng HTTPS; không bật cleartext toàn cục và không công khai
cổng PostgreSQL.

## 9. Cấu hình đăng nhập Google

### 9.1 Tạo OAuth consent screen

Trong Google Cloud Console:

1. tạo/chọn project;
2. cấu hình Branding, Audience và Data Access;
3. nếu app ở trạng thái Testing, thêm email kiểm thử;
4. chỉ yêu cầu `openid`, `email`, `profile`.

### 9.2 Tạo Android OAuth Client

Thông tin Android:

```text
Package name: com.klcn.mobilecaptioning
```

Lấy SHA debug trên đúng máy build:

```powershell
& "$env:JAVA_HOME\bin\keytool.exe" -list -v `
  -alias androiddebugkey `
  -keystore "$env:USERPROFILE\.android\debug.keystore" `
  -storepass android `
  -keypass android
```

Tạo Android OAuth Client với package name và SHA-1 vừa lấy.

### 9.3 Tạo Web OAuth Client

Tạo thêm client loại **Web application** trong cùng Google Cloud project.

Đặt Web Client ID vào:

```text
app/src/main/res/values/strings.xml
```

```xml
<string name="google_web_client_id">
    PROJECT_NUMBER-ID.apps.googleusercontent.com
</string>
```

Đồng thời đặt cùng giá trị trong backend `.env`:

```dotenv
GOOGLE_WEB_CLIENT_ID=PROJECT_NUMBER-ID.apps.googleusercontent.com
```

Không dùng Android Client ID hoặc Client Secret tại hai vị trí trên.

Build lại và gỡ/cài lại app nếu vừa đổi OAuth:

```powershell
.\gradlew.bat :app:assembleDebug
adb uninstall com.klcn.mobilecaptioning
adb install .\app\build\outputs\apk\debug\app-debug.apk
```

Hướng dẫn chi tiết hơn: [AUTH_PRODUCTION_SETUP.md](AUTH_PRODUCTION_SETUP.md).

### 9.4 Cấu hình email quên mật khẩu

Chức năng **Quên mật khẩu** dùng backend trung tâm, không sinh hoặc hiển thị
mã xác minh trong ứng dụng. OTP gồm 6 chữ số được gửi đến đúng email đã lưu
trong tài khoản `EMAIL`.

Với Gmail:

1. bật **Xác minh 2 bước** cho tài khoản gửi thư;
2. tạo **App Password** có tên `Vision Caption`;
3. thêm cấu hình sau vào `D:\KLCN\backend\.env`:

```dotenv
PASSWORD_RESET_MINUTES=10
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your-email@gmail.com
SMTP_PASSWORD=GOOGLE_APP_PASSWORD_16_KY_TU
SMTP_FROM_EMAIL=your-email@gmail.com
SMTP_FROM_NAME=Vision Caption
SMTP_SECURITY=starttls
```

Không dùng mật khẩu Gmail thông thường, không đưa App Password vào Git và
không thêm dấu cách vào App Password. Áp dụng migration và khởi động lại:

```powershell
cd D:\KLCN\backend
Set-ExecutionPolicy -Scope Process Bypass
.\setup.ps1
```

Để kiểm thử, đăng ký một tài khoản bằng hộp thư thật, chọn **Quên mật khẩu**,
nhập đúng email đã tồn tại trong hệ thống, sau đó nhập OTP, mật khẩu mới và
xác nhận mật khẩu. Username, email chưa đăng ký và tài khoản Google không có
mật khẩu cục bộ đều không được chấp nhận ở luồng này.
OTP hết hạn sau 10 phút, dùng một lần và bị khóa sau 5 lần nhập sai.

## 10. Build và cài ứng dụng

### 10.1 Android Studio

1. Mở Android Studio.
2. Chọn **Open** và mở `D:\KLCN\src`.
3. Chọn Gradle JDK 17.
4. Cài Android SDK 36 nếu được yêu cầu.
5. Chờ Gradle Sync hoàn tất.
6. Tạo hoặc chọn emulator.
7. Chọn cấu hình `app`.
8. Bấm **Run**.

### 10.2 PowerShell

Build debug:

```powershell
cd "D:\KLCN\src"
.\gradlew.bat :app:assembleDebug
```

APK:

```text
app\build\outputs\apk\debug\app-debug.apk
```

Cài:

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

Build release phục vụ nghiên cứu:

```powershell
.\gradlew.bat :app:assembleRelease
```

> Release hiện tại ký bằng debug key và tắt optimization để phục vụ benchmark
> nghiên cứu. Không dùng build này để phát hành Google Play.

## 11. Hướng dẫn sử dụng

### 11.1 Khởi động lần đầu

1. Đảm bảo backend đang chạy nếu muốn đăng nhập/đồng bộ.
2. Mở ứng dụng.
3. Chọn:
   - đăng nhập bằng username/email;
   - đăng ký tài khoản;
   - đăng nhập Google;
   - hoặc tiếp tục với tư cách Khách.

### 11.2 Tạo chú thích ảnh

1. Mở tab **Chụp/Capture**.
2. Chọn **Thư viện/Gallery** hoặc **Máy ảnh/Camera**.
3. Chọn/chụp một ảnh.
4. Bấm **Chú thích ảnh/Generate caption**.
5. Chờ model xử lý.
6. Xem caption tiếng Anh và bản dịch tiếng Việt ngay bên dưới.
7. Chọn **EN** hoặc **VI** để nghe; thiết bị cần có voice tương ứng.
8. Chọn **Chia sẻ** để gửi ảnh đã ghép cả caption Anh và bản dịch Việt.

ML Kit tải language pack EN/VI trong lần dịch đầu tiên. Ảnh không được gửi
đến dịch vụ dịch; sau khi model ngôn ngữ đã tải, phần dịch chạy trên thiết bị.

### 11.3 Chọn mô hình

Có thể đổi mô hình trực tiếp tại màn hình **Chụp**, tab **Mô hình**, hoặc
**Thêm → Mô hình**.

- V1 FP32 là fallback mặc định.
- Ba model V2 sử dụng ONNX INT8.
- Lần đầu đổi model có thể mất thêm thời gian để nạp encoder/decoder.
- Ứng dụng kiểm tra SHA-256 của model trước khi tạo ONNX session.

### 11.4 Lịch sử và yêu thích

- User/Admin có thể lưu lịch sử, đánh dấu yêu thích, tìm kiếm và phân trang.
- Dữ liệu được tách theo tài khoản.
- Guest không lưu lịch sử hoặc yêu thích.
- Đổi tài khoản sẽ nạp đúng cache của tài khoản mới.

### 11.5 Giao diện và ngôn ngữ

Nút **+** cố định ở góc dưới trái mở hai điều khiển nhanh:

- đổi Việt/English bằng cờ Việt Nam/Hoa Kỳ;
- đổi Light/Dark theme.

Chọn **×** để thu gọn. Mở **Thêm (…)** trong menu để truy cập tài khoản, mô
hình, cấu hình API, cài đặt và quản trị nếu có quyền.

## 12. Tài khoản và phân quyền

### User

- Đăng ký bằng họ tên, username, email và mật khẩu.
- Username dài 3–24 ký tự, bắt đầu bằng chữ.
- Mật khẩu tối thiểu 8 ký tự, có chữ hoa, chữ thường và số.
- Đăng nhập bằng username hoặc email.
- Username/email là duy nhất, không phân biệt hoa thường.

### Guest

- Có thể tạo caption.
- Có thể xem thông tin mô hình.
- Không lưu lịch sử/yêu thích.
- Không dùng chức năng quản trị.

### Super Admin

Tài khoản được backend tạo từ:

```text
D:\KLCN\backend\.env
```

Đăng nhập bằng:

```dotenv
ADMIN_USERNAME=...
ADMIN_PASSWORD=...
```

Không đưa tài khoản/mật khẩu Admin vào mã nguồn, README, ảnh chụp hoặc commit.

Quản trị viên có thể khóa/mở khóa tài khoản nhưng không xóa user. PostgreSQL
có trigger chặn hard-delete; Super Admin cũng được bảo vệ khỏi khóa/xóa.

## 13. Mô hình có trong ứng dụng

| Runtime | Encoder Student | Quantization | Validation CIDEr-D | BLEU-4 |
|---|---|---|---:|---:|
| V1 FP32 | Baseline fallback | FP32 | Tham chiếu nội bộ | — |
| T1 → S1 | MobileNetV3-Large | Encoder + Decoder INT8 | 1.1092 | 0.3464 |
| T2 → S2 | MobileViT-S | Encoder + Decoder INT8 | 1.0207 | 0.3237 |
| T3 → S3 | TinyViT-5M | Decoder INT8 | 1.0940 | 0.3434 |

Teacher tương ứng:

- T1: ExpansionNet v2.
- T2: BLIP.
- T3: SigLIP ViT-B/16.

Các model V2 dùng:

```text
Input image       : 1 × 3 × 224 × 224
Visual memory     : 1 × 49 × 256
Vocabulary        : 10.080 tokens
Sequence length   : 31
Beam size         : 3
Minimum words     : 2
```

Các metric trên là Validation metric. Held-out Test của nghiên cứu vẫn được
niêm phong và không được dùng để quảng cáo chất lượng production.

## 14. Dữ liệu và chế độ ngoại tuyến

### PostgreSQL

Lưu dữ liệu trung tâm:

- users;
- user permissions;
- activity events;
- caption history;
- favorites;
- thumbnails/media;
- user preferences;
- revoked JWT;
- sync receipts.

### SQLite Android

Lưu:

- cache theo tài khoản;
- tùy chọn thiết bị;
- lịch sử cục bộ đã đồng bộ/đang chờ;
- `sync_outbox` khi offline;
- session cache.

### Khi mất mạng

- Inference ONNX vẫn hoạt động.
- Tài khoản đã cache có thể dùng một số chức năng cục bộ.
- Thay đổi được đưa vào hàng đợi và đồng bộ khi API hoạt động lại.
- Quyền từ backend vẫn là nguồn quyết định cuối cùng sau khi kết nối lại.

Chi tiết: [DATABASE_ARCHITECTURE.md](DATABASE_ARCHITECTURE.md).

## 15. Kiểm thử

### Backend

```powershell
cd "D:\KLCN\backend"
docker compose ps
Invoke-RestMethod http://127.0.0.1:8000/health
docker compose logs --tail 100 api
```

### Android unit test

```powershell
cd "D:\KLCN\src"
.\gradlew.bat :app:testDebugUnitTest
```

### Build gate

```powershell
.\gradlew.bat :app:assembleDebug
```

### Instrumented test

Khởi động emulator hoặc kết nối thiết bị:

```powershell
adb devices
.\gradlew.bat :app:connectedDebugAndroidTest
```

### Checklist thủ công

- Đăng ký username/email mới.
- Kiểm tra username/email trùng.
- Đăng nhập bằng username và bằng email.
- Đăng nhập Google.
- Đăng xuất và đăng nhập lại.
- Guest không lưu lịch sử/yêu thích.
- User A không thấy dữ liệu User B.
- Khóa user từ Admin và xác minh user mất quyền truy cập.
- Cập nhật quyền và refresh session.
- Chạy caption với cả bốn runtime.
- Chuyển Việt/Anh và Light/Dark trên mọi màn hình.
- Tắt API, tạo caption, bật lại API và kiểm tra đồng bộ.

## 16. Vận hành Docker

Chạy:

```powershell
cd "D:\KLCN\backend"
docker compose up -d
```

Dừng nhưng giữ dữ liệu:

```powershell
docker compose stop
```

Chạy lại:

```powershell
docker compose start
```

Xem log:

```powershell
docker compose logs -f api
docker compose logs -f postgres
```

Build lại API sau khi sửa code:

```powershell
docker compose up -d --build api
```

Xóa container nhưng giữ volume:

```powershell
docker compose down
```

### Cảnh báo xóa dữ liệu

Lệnh sau xóa PostgreSQL volume:

```powershell
.\setup.ps1 -Recreate
```

Chỉ dùng cho môi trường test khi đã sao lưu hoặc chắc chắn không cần dữ liệu.

### Sao lưu

```powershell
docker compose exec postgres `
  pg_dump -U vision_app -d vision_caption -Fc `
  -f /tmp/vision_caption.backup

docker compose cp `
  postgres:/tmp/vision_caption.backup `
  .\vision_caption.backup
```

### Phục hồi

```powershell
docker compose cp `
  .\vision_caption.backup `
  postgres:/tmp/vision_caption.backup

docker compose exec postgres `
  pg_restore -U vision_app -d vision_caption `
  --clean --if-exists `
  /tmp/vision_caption.backup
```

Không thực hiện restore vào database production trước khi đã kiểm thử trên
database tạm.

## 17. Kết nối PostgreSQL bằng DBeaver

1. Chạy backend bằng Docker.
2. Mở DBeaver.
3. Chọn **New Database Connection → PostgreSQL**.
4. Nhập:

| Trường | Giá trị |
|---|---|
| Host | `127.0.0.1` |
| Port | `15432` |
| Database | `vision_caption` |
| Username | `vision_app` |
| Password | `POSTGRES_PASSWORD` trong backend `.env` |

5. Chọn **Test Connection**.
6. Cho phép tải PostgreSQL JDBC driver nếu được hỏi.
7. Chọn **Finish**.

Kiểm tra:

```sql
SELECT current_database(), current_user, version();

SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_type = 'BASE TABLE'
ORDER BY table_name;

SELECT *
FROM v_user_activity_summary
ORDER BY last_activity_at DESC NULLS LAST;
```

Mật khẩu DBeaver là `POSTGRES_PASSWORD`, không phải `ADMIN_PASSWORD`.

## 18. Xử lý lỗi thường gặp

### `No credentials available` khi đăng nhập Google

Kiểm tra:

- emulator/device đã đăng nhập tài khoản Google;
- system image có Google Play;
- Android OAuth Client đúng package và SHA-1;
- Web Client ID cùng Google Cloud project;
- `strings.xml` và backend `.env` dùng cùng Web Client ID;
- email nằm trong Test users nếu consent screen ở Testing;
- đã gỡ và cài lại APK sau khi đổi OAuth.

### Android không kết nối được API

```powershell
docker compose ps
Invoke-RestMethod http://127.0.0.1:8000/health
docker compose logs --tail 100 api
```

Kiểm tra URL:

- emulator: `http://10.0.2.2:8000`;
- thiết bị USB với `adb reverse`: `http://localhost:8000`;
- không dùng `localhost` trên emulator để trỏ host.

### API trả lỗi 500

```powershell
cd "D:\KLCN\backend"
docker compose logs --tail 200 api
docker compose logs --tail 100 postgres
```

Kiểm tra `.env`, database health và schema. Không hiển thị raw exception hoặc
secret cho người dùng cuối.

### Không tìm thấy model hoặc SHA-256 không khớp

```powershell
git lfs pull
Get-ChildItem .\app\src\main\assets\v2 -Recurse -Filter *.onnx
```

Không thay trực tiếp model ONNX khác tên nhưng giữ contract cũ. Model, tensor
contract, preprocessing và SHA-256 phải được cập nhật cùng nhau.

### Build dùng sai Java

Trong Android Studio chọn Gradle JDK 17. Kiểm tra:

```powershell
java -version
.\gradlew.bat --version
```

### Thiếu RAM hoặc inference chậm

- Đóng ứng dụng/emulator không cần thiết.
- Tăng RAM emulator lên 4 GB.
- Chọn model nhỏ hơn.
- Tránh chạy nhiều inference đồng thời.
- Thử trên thiết bị thật 64-bit.

### Database không có bảng sau khi sửa schema

`001_schema.sql` chỉ tự chạy khi PostgreSQL volume còn trống. Với database đã
có dữ liệu, cần tạo migration mới. Không dùng `-Recreate` chỉ để áp dụng thay
đổi schema production.

## 19. Bảo mật và giới hạn hiện tại

- Không commit `.env`, keystore, JWT secret, mật khẩu hoặc Google Client
  Secret.
- Android chỉ gọi API; không đưa credential PostgreSQL vào APK.
- Production phải dùng HTTPS và reverse proxy.
- Không công khai cổng PostgreSQL ra Internet.
- Cần release signing key riêng và Play App Signing trước khi phát hành.
- Release build hiện tại dùng debug signing, chỉ dành cho nghiên cứu.
- Google OAuth production cần release/Play Signing SHA-1.
- Physical-device benchmark cho các model V2 chưa được đánh dấu hoàn tất
  trong registry hiện tại.
- Caption hiện sinh bằng tiếng Anh.
- Chỉ số Validation không phải cam kết chất lượng cho mọi ảnh.
- Held-out Test của nghiên cứu vẫn niêm phong.

Tài liệu liên quan:

- [AUTH_PRODUCTION_SETUP.md](AUTH_PRODUCTION_SETUP.md)
- [DATABASE_ARCHITECTURE.md](DATABASE_ARCHITECTURE.md)
- [MOBILE_UX_PRODUCT_ROADMAP.md](MOBILE_UX_PRODUCT_ROADMAP.md)
