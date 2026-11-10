# Production authentication setup

[Bản tiếng Việt](AUTH_PRODUCTION_SETUP.md) · [Back to README](README_EN.md)

This guide covers password authentication, Google Sign-In, and email password reset for Mobile Image Captioning 1.7.0. Generate credentials and signing fingerprints in your own environment; never reuse values copied from another developer's machine.

## 1. Prepare the backend

From the repository root:

```powershell
Set-Location .\backend
Copy-Item .env.example .env
```

Configure at least:

```dotenv
JWT_SECRET=<long-random-value>
ADMIN_PASSWORD=<initial-admin-password>
GOOGLE_WEB_CLIENT_ID=<web-client-id>.apps.googleusercontent.com
SMTP_HOST=<smtp-host>
SMTP_PORT=587
SMTP_USERNAME=<sender-account>
SMTP_PASSWORD=<application-password>
SMTP_FROM_EMAIL=<sender-address>
```

Start the services:

```powershell
docker compose up -d --build
docker compose ps
```

Never place `ADMIN_PASSWORD`, `JWT_SECRET`, or SMTP credentials in the APK.

## 2. Generate Android fingerprints

```powershell
Set-Location .\src
.\gradlew.bat signingReport
```

Record SHA-1 and SHA-256 for the correct variant:

- `debug` for Android Studio runs;
- `release` for a distributed APK/AAB;
- Play App Signing fingerprints for Google Play releases.

Every signing key has different fingerprints. Documentation values cannot replace the output generated on your machine.

## 3. Configure Google Cloud

1. Select a Google Cloud project.
2. Configure the OAuth consent screen.
3. Create an **Android OAuth client** for:
   - package: `com.klcn.mobilecaptioning`;
   - SHA-1: the target build's fingerprint.
4. Create a **Web OAuth client**.
5. Use its Web Client ID in both Android and backend `GOOGLE_WEB_CLIENT_ID`.
6. Create another Android client for each additional signing SHA-1.

After changing clients:

```powershell
Set-Location .\backend
docker compose up -d --force-recreate api

Set-Location ..\src
.\gradlew.bat clean :app:assembleDebug
adb shell pm clear com.klcn.mobilecaptioning
```

## 4. Username or email sign-in

Before testing:

1. confirm `/health` succeeds;
2. register a new account;
3. inspect its email/username in PostgreSQL;
4. test correct and incorrect passwords;
5. test active and locked accounts;
6. confirm messages describe only the result of the requested action.

Passwords must be stored as hashes. Never log plaintext passwords.

## 5. Email password reset

The valid flow:

1. accepts an email already registered;
2. creates a time-limited one-time code;
3. delivers it through SMTP;
4. accepts the code, new password, and confirmation;
5. verifies the code and updates the password hash;
6. marks the code as used or expired.

Apply the migration when required:

```powershell
Set-Location .\backend
docker compose exec -T postgres psql `
  -U <POSTGRES_USER> `
  -d <POSTGRES_DB> `
  -f /docker-entrypoint-initdb.d/002_password_reset.sql
```

Use values from `.env` for the placeholders.

```powershell
docker compose logs -f api
```

Do not display reset codes in the production interface.

## 6. Select the API URL

| Environment | URL |
|---|---|
| Android Emulator | `http://10.0.2.2:8000` |
| USB phone with `adb reverse` | `http://127.0.0.1:8000` |
| Phone on the same Wi-Fi | `http://<computer-ipv4>:8000` |
| Production | Public HTTPS URL |

```powershell
adb reverse tcp:8000 tcp:8000
```

Do not use `10.0.2.2` on a physical phone.

## 7. Release checklist

- [ ] Use a release keystore stored outside the repository.
- [ ] Register the release or Play App Signing SHA.
- [ ] Use HTTPS.
- [ ] Replace every sample secret.
- [ ] Keep admin and SMTP credentials out of the APK.
- [ ] Test registration, sign-in, Google Sign-In, and sign-out.
- [ ] Test expired, incorrect, and reused reset codes.
- [ ] Test an unregistered email.
- [ ] Test account locking, unlocking, and deletion.
- [ ] Confirm logs contain no passwords, reset codes, or complete tokens.

## 8. Troubleshooting

### Google configuration error

- rerun `signingReport`;
- verify the package name and build variant;
- verify the Web Client ID in Android and the backend;
- allow time for OAuth changes to propagate.

### The app cannot reach the API

- open `/health` from the computer and phone;
- use the URL for the current device type;
- rerun `adb reverse` for USB;
- check the firewall for Wi-Fi access;
- inspect `docker compose logs -f api`.

### Reset email does not arrive

- confirm the email is registered;
- verify SMTP host, port, and TLS;
- use an application password;
- check spam;
- inspect API logs for SMTP authentication failures.
