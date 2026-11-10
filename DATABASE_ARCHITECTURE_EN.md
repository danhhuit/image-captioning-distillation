# Data architecture

[Bản tiếng Việt](DATABASE_ARCHITECTURE.md) · [Back to README](README_EN.md)

Version 1.7.0 separates local offline-inference data from account data managed by the backend.

## 1. On-device data

Room/SQLite stores:

- caption history;
- an app-managed image path or URI;
- English captions;
- Vietnamese translations when available;
- favourite state;
- selected model and creation time.

Image inference does not depend on PostgreSQL. ONNX weights and vocabularies are packaged according to the app model registry.

## 2. Backend data

PostgreSQL stores:

- accounts and activation state;
- roles and permissions;
- sessions/tokens required by the API contract;
- password-reset requests;
- required administration records.

Initial schema files:

```text
backend/sql/001_schema.sql
backend/sql/002_password_reset.sql
```

## 3. Data flow

```text
Image
  → Android preprocessing
  → ONNX encoder/decoder
  → English caption
  → on-device translation and speech
  → local history

Account request
  → FastAPI
  → authentication and authorization
  → PostgreSQL
```

Do not describe automatic background synchronisation unless an implemented and tested feature supports that claim. Any future synchronisation feature must state its trigger, transmitted data, and failure handling.

## 4. PostgreSQL connection

Default Docker Compose connection:

| Field | Value source |
|---|---|
| Host | `localhost` |
| Port | `POSTGRES_PORT`, default `15432` |
| Database | `POSTGRES_DB` |
| Username | `POSTGRES_USER` |
| Password | `POSTGRES_PASSWORD` |

Read these values from `<repo>/backend/.env`; do not hard-code them in source or deployment documentation.

## 5. Migrations

```powershell
Set-Location .\backend
docker compose up -d --build
```

Reapply the password-reset migration:

```powershell
docker compose exec -T postgres psql `
  -U <POSTGRES_USER> `
  -d <POSTGRES_DB> `
  -f /docker-entrypoint-initdb.d/002_password_reset.sql
```

## 6. Backup

```powershell
Set-Location .\backend
docker compose exec -T postgres pg_dump `
  -U <POSTGRES_USER> `
  -d <POSTGRES_DB> > backup.sql
```

Do not store backups containing real user data in the repository.

## 7. Data-protection rules

- Store passwords only as hashes.
- Use time-limited, one-time password-reset codes.
- Bind the development PostgreSQL port to localhost.
- Use HTTPS for a public API.
- Never commit `.env`, database backups, or tokens.
- Collect only data required for disclosed features.
