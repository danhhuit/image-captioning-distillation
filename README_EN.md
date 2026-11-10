# Mobile Image Captioning

[Bản tiếng Việt](README.md)

This Android research application generates image captions directly on a mobile device. Users can select or capture an image, run an ONNX model, translate the English caption into Vietnamese, listen to the result, and keep a local history. Account features use a FastAPI and PostgreSQL backend when a server is configured.

Current version: **1.7.0** (`versionCode 8`).

> This is a research mini-app for deployment validation. Metrics shown in the interface describe the corresponding checkpoints; they do not replace evaluation on the target device and dataset.

## 1. Main features

- Offline image captioning with ONNX Runtime Android.
- Selection between main and experimental models.
- Model details including dataset, knowledge-distillation method, checkpoint, parameter count, and CIDEr-D.
- English–Vietnamese translation with ML Kit.
- Text-to-Speech for the selected language.
- Local history, favourites, and image storage.
- Image sharing with the generated caption.
- Vietnamese and English interfaces with light, dark, and device themes.
- Registration, sign-in, Google Sign-In, password reset, and account administration through the API.

## 2. Repository layout

```text
image-captioning-distillation/
├── src/                    # Android application
│   ├── app/
│   ├── notebooks/          # Selected reproducibility notebooks
│   ├── scripts/            # Test and benchmark scripts
│   └── README.md
├── backend/                # FastAPI, PostgreSQL, and Docker Compose
│   ├── app/
│   ├── sql/
│   ├── docker-compose.yml
│   └── README.md
└── ...
```

The documentation uses these placeholders:

- `<repo>`: repository root;
- `<android>`: `<repo>/src`;
- `<backend>`: `<repo>/backend`.

The repository can be stored in any user-accessible directory.

## 3. Requirements

### 3.1. Android

- Windows 10/11, macOS, or Linux.
- Android Studio with Android SDK 36.
- JDK 23, or an Android Studio-managed JDK compatible with Gradle toolchain 23.
- Git and Git LFS.
- An Android API 26+ device or Android Emulator.

### 3.2. Backend

- Docker Desktop with Docker Compose v2.
- PowerShell 7 on Windows when using `setup.ps1`.
- SMTP credentials if real password-reset email delivery is required.

### 3.3. Main technical versions

| Component | Version |
|---|---:|
| Android compile/target SDK | 36 |
| Android min SDK | 26 |
| Gradle Wrapper | 9.6.0 |
| Gradle JVM toolchain | 23 |
| Kotlin | 2.2.10 |
| Jetpack Compose BOM | 2026.02.01 |
| ONNX Runtime Android | 1.29.0 |
| ML Kit Translate | 17.0.3 |
| Docker Python | 3.12 |
| Docker PostgreSQL | 18 |

## 4. Clone the repository

Open a terminal in the directory where you keep development projects:

```powershell
git clone https://github.com/danhhuit/image-captioning-distillation.git
Set-Location image-captioning-distillation
git lfs install
git lfs pull
```

Verify the large model files:

```powershell
git lfs ls-files
```

If an `.onnx` file is only a few bytes or begins with `version https://git-lfs.github.com`, run `git lfs pull` again.

## 5. Start the backend with Docker

### 5.1. Quick setup on Windows

From the repository root:

```powershell
Set-Location .\backend
.\setup.ps1
```

The script creates `.env` when needed, generates local secrets, builds the API image, starts PostgreSQL and FastAPI, applies the password-reset migration, and checks the health endpoint.

Inspect the services:

```powershell
docker compose ps
docker compose logs -f api
```

API documentation:

```text
http://localhost:8000/docs
```

Health check:

```powershell
Invoke-RestMethod http://localhost:8000/health
```

### 5.2. Manual setup

On macOS/Linux:

```bash
cd backend
cp .env.example .env
docker compose up -d --build
docker compose ps
```

On PowerShell:

```powershell
Set-Location .\backend
Copy-Item .env.example .env
docker compose up -d --build
docker compose ps
```

Replace all sample passwords, JWT secrets, administrator keys, and SMTP credentials before using a shared or public environment.

### 5.3. Stop or recreate containers

```powershell
docker compose down
docker compose up -d
```

The next command deletes the local PostgreSQL volume. Use it only when that data may be discarded:

```powershell
docker compose down -v
```

## 6. Configure the Android API address

The app reads `BuildConfig.CENTRAL_API_BASE_URL`. Its Android Emulator default is:

```text
http://10.0.2.2:8000
```

`10.0.2.2` maps the emulator to the host computer's `localhost`. It is not an address for physical phones.

### 6.1. Android Emulator

Keep the default URL, start the backend, and verify:

```powershell
adb shell ping -c 1 10.0.2.2
```

### 6.2. USB-connected phone

For local development:

```powershell
adb devices
adb reverse tcp:8000 tcp:8000
```

Use this API URL:

```text
http://127.0.0.1:8000
```

Run `adb reverse` again after unplugging the phone, restarting it, or restarting ADB.

### 6.3. Phone and computer on the same Wi-Fi network

1. Find the computer's local IPv4 address.
2. Ensure the API listens on `0.0.0.0:8000`.
3. Allow TCP port 8000 through the private-network firewall.
4. Set the URL to `http://<computer-ip>:8000`.
5. Open `/health` in the phone browser before starting the app.

Example local Gradle configuration:

```properties
CENTRAL_API_BASE_URL=http://192.168.1.20:8000
```

Do not commit private-network addresses, credentials, or secrets.

## 7. Configure `.env`

```powershell
Set-Location .\backend
Copy-Item .env.example .env
```

| Group | Typical variables | Purpose |
|---|---|---|
| PostgreSQL | `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Database connection |
| API | `API_BIND_HOST`, `API_PORT` | API binding and port |
| Authentication | `JWT_SECRET`, `ADMIN_PASSWORD` | Token signing and initial administrator account |
| Google | `GOOGLE_WEB_CLIENT_ID` | Google ID-token verification |
| Email | `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` | Password-reset delivery |

For a shared environment:

- use strong, different values for `POSTGRES_PASSWORD`, `JWT_SECRET`, and `ADMIN_PASSWORD`;
- replace every sample value;
- use an application password or dedicated SMTP credential;
- never commit `.env`.

See [backend/README_EN.md](../backend/README_EN.md).

## 8. Google Sign-In

Google Sign-In requires a matching package name, signing-certificate SHA-1/SHA-256, and Web Client ID.

Generate fingerprints on your own machine:

```powershell
Set-Location .\src
.\gradlew.bat signingReport
```

Then:

1. create or select a Google Cloud project;
2. configure the OAuth consent screen;
3. create an Android OAuth client for `com.klcn.mobilecaptioning`;
4. enter the SHA-1 for the build variant being tested;
5. create a Web OAuth client;
6. place the Web Client ID in both the Android configuration and backend `GOOGLE_WEB_CLIENT_ID`;
7. rebuild the app and restart the API.

Do not copy another developer's signing fingerprint. Release builds must use the release-keystore fingerprint.

See [AUTH_PRODUCTION_SETUP_EN.md](AUTH_PRODUCTION_SETUP_EN.md).

## 9. Email password reset

The password-reset flow:

1. accepts an email already registered in the database;
2. creates a time-limited one-time code;
3. delivers the code to that email through SMTP;
4. accepts the code, new password, and password confirmation;
5. verifies the code and stores the new password hash.

Check the migration:

```powershell
Set-Location .\backend
docker compose exec -T postgres psql -U vision_app -d vision_caption -f /docker-entrypoint-initdb.d/002_password_reset.sql
```

Adjust the database and user names to match `.env`.

## 10. Build and run

### 10.1. Android Studio

1. Open the `src` directory, not the whole repository.
2. Wait for Gradle Sync.
3. Select an emulator or physical device.
4. Select the `app` configuration.
5. Press **Run**.

### 10.2. Terminal

Windows:

```powershell
Set-Location .\src
.\gradlew.bat clean :app:assembleDebug
.\gradlew.bat :app:installDebug
```

macOS/Linux:

```bash
cd src
./gradlew clean :app:assembleDebug
./gradlew :app:installDebug
```

Debug APK:

```text
src/app/build/outputs/apk/debug/app-debug.apk
```

Release build:

```powershell
Set-Location .\src
.\gradlew.bat :app:assembleRelease
```

Distribute a release APK only after configuring release signing, an HTTPS API URL, and production OAuth clients.

## 11. Use the app

1. Open **Capture**.
2. Choose **Main models** or **Experimental models**.
3. Expand the list and select a checkpoint.
4. choose an image or take a photo;
5. wait for the English caption and Vietnamese translation;
6. use Text-to-Speech or share the image with its caption;
7. review results in **History** and **Favourites**.

An account is required only for server-backed features. Offline inference remains available in guest mode when the model package is included in the APK.

## 12. Model catalogue

### 12.1. Main models

| Teacher → Student | Dataset | Method | Baseline → KD CIDEr-D |
|---|---|---|---:|
| ExpansionNet v2 → MobileNetV3-Large | MS COCO 2014 | Sequence KD | 98.58 → 110.92 |
| ExpansionNet v2 → MobileNetV3-Large | MS COCO 2014 | Dense-Mapped Logit-KD | 98.58 → 108.45 |
| ExpansionNet v2 → MobileNetV3-Large | MS COCO 2014 | Hybrid Sequence + Dense-Mapped Logit-KD | 98.58 → 109.95 |
| BLIP Base → MobileViT-S | MS COCO 2014 | Sequence KD | 99.32 → 102.07 |
| SigLIP ViT-B/16 → TinyViT-5M | MS COCO 2014 | Direct Logit-KD | 107.48 → 109.40 |

### 12.2. Experimental models

| Teacher → Student | Dataset | Method | Baseline → KD CIDEr-D |
|---|---|---|---:|
| V1 FP32 | MS COCO 2014 | Reference model | — |
| CLIP ViT-B/32 → MobileNetV3-Large | MS COCO 2014 | Direct Logit-KD | 98.73 → 100.73 |
| ExpansionNet v2 → MobileNetV3-Large | Flickr30K | Sequence KD | 47.62 → 38.49 |
| ExpansionNet v2 → MobileNetV3-Large | Flickr30K | Dense-Mapped Logit-KD | 47.62 → 36.93 |
| ExpansionNet v2 → MobileNetV3-Large | Flickr30K | Hybrid KD | 47.70 → 38.07 |
| BLIP Base → MobileViT-S | Flickr30K | Sequence KD | 50.10 → 42.33 |
| SigLIP ViT-B/16 → TinyViT-5M | Flickr30K | Direct Logit-KD | 57.07 → 61.71 |

Flickr30K branches whose KD score is below their baseline remain experimental evidence and are not described as improvements.

## 13. Testing

Unit tests:

```powershell
Set-Location .\src
.\gradlew.bat test
```

Instrumentation tests:

```powershell
.\gradlew.bat connectedAndroidTest
```

Connected devices:

```powershell
adb devices
```

Emulator benchmarks are smoke tests only. Do not report emulator latency, RAM, or thermal data as physical-device scientific measurements.

## 14. Connect DBeaver to PostgreSQL

Default Docker connection:

| Field | Value |
|---|---|
| Host | `localhost` |
| Port | `POSTGRES_PORT`, default `15432` |
| Database | `POSTGRES_DB` |
| Username | `POSTGRES_USER` |
| Password | `POSTGRES_PASSWORD` |

1. Select **New Database Connection**.
2. Choose **PostgreSQL**.
3. Enter the values from `.env`.
4. Select **Test Connection**.
5. Allow DBeaver to download its driver when prompted.
6. Open the `public` schema.

Do not expose PostgreSQL directly to the Internet. The default database port is bound to `127.0.0.1`.

## 15. Backup and restore

Backup:

```powershell
Set-Location .\backend
docker compose exec -T postgres pg_dump -U vision_app -d vision_caption > backup.sql
```

Restore:

```powershell
Get-Content .\backup.sql | docker compose exec -T postgres psql -U vision_app -d vision_caption
```

Adjust the user and database names to match `.env`.

## 16. Troubleshooting

### “Unable to connect”

- Check `docker compose ps`.
- Open `/health` on the computer.
- Use `10.0.2.2`, not `localhost`, from the emulator.
- Run `adb reverse tcp:8000 tcp:8000` again for USB testing.
- Check the local IP and firewall for Wi-Fi testing.
- Rebuild after changing the API URL.

### Google Sign-In rejects the account

- Verify the package name.
- Run `signingReport` and update the correct SHA.
- Use the same Web Client ID in Android and the backend.
- Clear app data after changing OAuth clients.

### Password-reset email does not arrive

- Confirm that the email exists in PostgreSQL.
- Check SMTP variables and API logs.
- Use an application password instead of the main email password.
- Check spam and code expiry.

### Model asset cannot be loaded

- Run `git lfs pull`.
- Verify the asset name and manifest.
- Do not rename individual ONNX files without updating the registry.

### Black screen or process crash

```powershell
adb logcat -c
adb logcat | Select-String "AndroidRuntime|mobilecaptioning"
```

Capture the first complete stack trace.

## 17. Additional documentation

- [Authentication setup](AUTH_PRODUCTION_SETUP_EN.md)
- [Data architecture](DATABASE_ARCHITECTURE_EN.md)
- [Mobile product roadmap](MOBILE_UX_PRODUCT_ROADMAP_EN.md)
- [Notebook catalogue](notebooks/README_EN.md)
- [Backend guide](../backend/README_EN.md)

## 18. Security

- Never commit `.env`, keystores, passwords, tokens, or API keys.
- Use HTTPS outside local development.
- Never place the administrator key in the APK.
- Replace all sample secrets before deployment.
- Collect only necessary user data and disclose its use.
- Test account deletion, locking, and password reset before release.

## 19. Licensing and citation

The repository contains research code, checkpoints, and documentation that may use different licences. Review the licence of every model, dataset, and library before redistribution or commercial use.
