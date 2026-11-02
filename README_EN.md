# Vision Caption

**[Tiếng Việt](README.md) | English**

Vision Caption is an Android research mini-app developed by a team to
evaluate and deploy compact image-captioning models. It accepts camera or
gallery images, generates an English caption on the device, translates it into
Vietnamese, reads either language aloud, and shares a composed image containing
both captions. The application also includes per-account history and favorites,
Vietnamese/English UI, light and dark themes, account administration, and a
model registry with experiment metadata.

Current Android version: **1.7.0**.

> This is a research application, not a safety- or medical-grade image
> interpretation tool. Metrics shown in the UI are Validation results scaled
> by 100 for readability; they are not accuracy percentages. Caption quality
> varies with the input image, dataset, and selected model.

### 1. Main capabilities

- Generate captions from camera and gallery images.
- Run ONNX inference locally with ONNX Runtime Android.
- Switch between primary and experimental model groups.
- Use a consistent model-pair label: `Tn: <Teacher> -> Sn: <Student>`.
- Decode captions with Beam Search (`beam size = 3`).
- Translate English captions into Vietnamese with on-device ML Kit. The first
  translation may require a network connection to download the language pack.
- Read English and Vietnamese text with Android Text-to-Speech.
- Share the source image together with its English and Vietnamese captions.
- Display tensor contracts, preprocessing, model files, runtime, and experiment
  metrics for the selected model.
- Authenticate with username/email and password or Google Credential Manager.
- Continue as Guest when account-backed features are not required.
- Store account data centrally in PostgreSQL and use SQLite as the Android
  cache/offline store.
- Provide resource/action RBAC and Super Admin account management.

### 2. System architecture

```text
Camera / Gallery image
        |
        v
Android app (Jetpack Compose)
        |
        +-- ONNX Runtime Android
        |     +-- visual encoder
        |     +-- visual memory
        |     +-- Transformer decoder
        |     +-- Beam Search
        |
        +-- SQLite cache and sync outbox
        |
        +-- development HTTP / production HTTPS API
                    |
                    v
                 FastAPI
                    |
                    v
                PostgreSQL
```

Full-resolution images are processed on Android. An authenticated account may
sync permitted caption metadata and thumbnails. The backend derives the user
from the JWT instead of trusting a client-supplied `user_id`. The APK never
connects directly to PostgreSQL and does not contain database credentials.

### 3. Development requirements

| Component | Requirement |
|---|---|
| Operating system | Windows 10/11 64-bit |
| Android Studio | Version compatible with Android Gradle Plugin 9.4 |
| Android SDK | Compile SDK 36 |
| JDK | JDK 17 |
| Docker | Docker Desktop with WSL 2 backend |
| Android tools | `adb`, emulator or 64-bit physical device |
| Source control | Git and Git LFS |

Recommended host resources are at least 16 GB RAM and 25 GB free disk space.
An emulator should use a Google Play x86_64 system image with at least 4 GB RAM.
For latency, memory, and thermal measurements, use a physical arm64 device;
emulator measurements are smoke-test evidence only.

### 4. Quick setup

Clone the repository and retrieve model assets:

```powershell
git clone https://github.com/danhhuit/image-captioning-distillation.git
cd image-captioning-distillation
git lfs install
git lfs pull
```

Start the backend:

```powershell
cd "D:\KLCN\backend"
Set-ExecutionPolicy -Scope Process Bypass
.\setup.ps1
```

Verify the API:

```powershell
Invoke-RestMethod http://127.0.0.1:8000/health
```

For an emulator test session, the helper script starts/checks the services and
verifies the selected ADB device:

```powershell
cd "D:\KLCN\src"
.\scripts\start_android_test_environment.ps1 `
  -DeviceSerial "emulator-5554"
```

Build and install the debug APK:

```powershell
.\gradlew.bat :app:assembleDebug
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

The Android emulator reaches the host API through
`http://10.0.2.2:8000`. A USB device can use `adb reverse` and
`http://localhost:8000`. A physical device on the local network must use the
host computer's LAN address and a firewall rule that exposes only the API port
to the trusted network.

### 5. Docker, FastAPI, and PostgreSQL

The initial `D:\KLCN\backend\setup.ps1` run creates `.env` from the example,
generates secrets, builds the FastAPI image, starts PostgreSQL, applies the
initial schema to a new volume, and waits for health checks.

```powershell
cd "D:\KLCN\backend"
docker compose ps
docker compose logs --tail 100 api
Invoke-RestMethod http://127.0.0.1:8000/health
```

Default development endpoints:

| Service | Host address |
|---|---|
| FastAPI | `127.0.0.1:8000` |
| Swagger/OpenAPI | `http://127.0.0.1:8000/docs` |
| PostgreSQL | `127.0.0.1:15432` |
| PostgreSQL inside Compose | `postgres:5432` |
| API from Android Emulator | `10.0.2.2:8000` |

Important `.env` values have separate purposes:

- `POSTGRES_PASSWORD` is used by PostgreSQL and DBeaver.
- `ADMIN_PASSWORD` is the Super Admin application password.
- `JWT_SECRET` is backend-only.
- `GOOGLE_WEB_CLIENT_ID` must match the Android configuration.

Do not commit `.env`. Use `docker compose stop/start` to preserve containers
and data. `docker compose down` removes containers but retains the named volume.
`setup.ps1 -Recreate` deletes the PostgreSQL volume and is only appropriate for
a disposable test environment after required data has been backed up.

### 6. Google Sign-In and password recovery

Google Sign-In requires an OAuth consent screen, an Android OAuth client for
the application package and signing SHA-1, and a Web OAuth client. The Web
Client ID must be identical in Android resources and backend `.env`. A testing
consent screen also requires each tester to be listed as a test user.

Password recovery only accepts an email already registered in the system. In
production, the backend sends a time-limited verification code to that address.
The user must provide the code, a new password, and the same password again for
confirmation. Configure the SMTP values in backend `.env`; never expose SMTP
credentials in the APK or source control.

### 7. Selecting and interpreting models

Open **Primary models** or **Experimental models** with the arrow button, then
select a card. The next caption request uses that model. Each card presents:

1. the concrete Teacher -> Student pair;
2. the training dataset;
3. the KD method;
4. baseline CIDEr-D -> distilled CIDEr-D.

All values below are Validation CIDEr-D scaled by 100.

#### Primary models

| Teacher -> Student | Dataset | KD method | Baseline | KD |
|---|---|---|---:|---:|
| T1: ExpansionNet v2 -> S1: MobileNetV3-Large | MSCOCO 2014 | Sequence KD | 98.58 | 110.92 |
| T1: ExpansionNet v2 -> S1: MobileNetV3-Large | MSCOCO 2014 | Dense-Mapped Logit-KD | 98.58 | 108.45 |
| T1: ExpansionNet v2 -> S1: MobileNetV3-Large | MSCOCO 2014 | Hybrid Sequence + Dense-Mapped Logit-KD | 98.58 | 109.95 |
| T2: BLIP Base -> S2: MobileViT-S | MSCOCO 2014 | Sequence KD | 99.32 | 102.07 |
| T3: SigLIP ViT-B/16 -> S3: TinyViT-5M | MSCOCO 2014 | Direct Logit-KD | 107.48 | 109.40 |

#### Experimental models

This table follows the exact UI order requested for comparative testing.

| Teacher -> Student | Dataset | KD method | Baseline | KD |
|---|---|---|---:|---:|
| V1 FP32 | MSCOCO 2014 | FP32 reference runtime | — | — |
| T1: CLIP ViT-B/32 -> S1: MobileNetV3-Large | MSCOCO 2014 | Direct Logit-KD | 98.73 | 100.73 |
| T1: ExpansionNet v2 -> S1: MobileNetV3-Large | Flickr30K | Sequence KD | 47.62 | 38.49 |
| T1: ExpansionNet v2 -> S1: MobileNetV3-Large | Flickr30K | Dense-Mapped Logit-KD | 47.62 | 36.93 |
| T1: ExpansionNet v2 -> S1: MobileNetV3-Large | Flickr30K | Hybrid Sequence + Dense-Mapped Logit-KD | 47.70 | 38.07 |
| T2: BLIP Base -> S2: MobileViT-S | Flickr30K | Sequence KD | 50.10 | 42.33 |
| T3: SigLIP ViT-B/16 -> S3: TinyViT-5M | Flickr30K | Direct Logit-KD | 57.07 | 61.71 |

The lower Flickr30K KD values are retained as experimental findings; they must
not be presented as improvements. Metrics from different datasets are not
directly comparable. Model selection remains based on the locked Validation
protocol, while Held-out Test results are not used to tune the method.

### 8. Using the application

1. Start the backend when sign-in or synchronization is required.
2. Sign in, register, use Google Sign-In, or continue as Guest.
3. Open **Capture**, choose **Gallery** or **Camera**, and select an image.
4. Select the required model and generate a caption.
5. Review the English caption and Vietnamese translation.
6. Select **EN** or **VI** for speech, or **Share** to export the composed image.

Authenticated users can store history and favorites. Guest sessions can run
captioning and inspect models but do not persist account history or favorites.
The floating `+` control exposes language and theme shortcuts; `×` closes it.

### 9. Testing

Backend and Android gates:

```powershell
cd "D:\KLCN\backend"
docker compose ps
Invoke-RestMethod http://127.0.0.1:8000/health

cd "D:\KLCN\src"
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

Manual checks should cover duplicate username/email rejection, password and
Google sign-in, sign-out, Guest restrictions, account data isolation, Admin
lock/unlock and permissions, every available model, Vietnamese/English on all
active screens, light/dark themes, offline captioning, and synchronization after
the API returns.

Android emulator benchmarking is a functional smoke test only. Reportable
latency, PSS memory, and thermal values require the locked physical-device
protocol and the same model package, image set, warm-up count, and measured-run
count used in the experiment record.

### 10. DBeaver connection

Create a PostgreSQL connection with:

| Field | Value |
|---|---|
| Host | `127.0.0.1` |
| Port | `15432` |
| Database | `vision_caption` |
| Username | `vision_app` |
| Password | `POSTGRES_PASSWORD` from backend `.env` |

The DBeaver password is not `ADMIN_PASSWORD`. Android must never connect to
this database endpoint directly.

### 11. Troubleshooting

- **App reports that it cannot connect:** verify Docker health and
  `http://127.0.0.1:8000/health`; use `10.0.2.2` on the emulator, not
  `localhost`. For USB, run `adb reverse tcp:8000 tcp:8000`.
- **Google reports no credentials:** use a Google Play system image, sign into
  Google on the device, verify package/SHA-1 and Web Client ID, then reinstall
  the APK after changing OAuth settings.
- **Model or SHA-256 mismatch:** run `git lfs pull` and keep the ONNX file,
  tensor contract, preprocessing metadata, vocabulary, and hash registry in
  sync.
- **Wrong Java version:** set Android Studio Gradle JDK to JDK 17 and check
  `java -version` and `.\gradlew.bat --version`.
- **Slow inference or low memory:** close unused emulators, allocate 4 GB RAM
  to the emulator, avoid concurrent inference, or validate on a physical arm64
  device.
- **Database schema did not change:** create a migration for an existing
  volume. Do not recreate a production database merely to apply a schema edit.

### 12. Security and current limitations

- Never commit `.env`, keystores, JWT secrets, passwords, or OAuth secrets.
- Use HTTPS and a reverse proxy outside local development.
- Do not expose PostgreSQL to the public Internet.
- Create a dedicated release signing key and use Play App Signing before store
  distribution; the current research release uses debug signing.
- Production Google OAuth must include the release/Play signing SHA-1.
- Caption generation currently produces English text; Vietnamese is an
  on-device translation of that result.
- Validation metrics are not a guarantee for arbitrary images.
- Research model cards and experiment outputs must retain their dataset,
  checkpoint, quantization, and evaluation context.

Related documents:

- [AUTH_PRODUCTION_SETUP.md](AUTH_PRODUCTION_SETUP.md)
- [DATABASE_ARCHITECTURE.md](DATABASE_ARCHITECTURE.md)
- [MOBILE_UX_PRODUCT_ROADMAP.md](MOBILE_UX_PRODUCT_ROADMAP.md)
