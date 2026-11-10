# Kiến trúc dữ liệu

[English version](DATABASE_ARCHITECTURE_EN.md) · [Quay lại README](README.md)

Ứng dụng 1.7.0 tách dữ liệu thành hai phạm vi: dữ liệu cục bộ phục vụ suy luận ngoại tuyến và dữ liệu tài khoản do backend quản lý.

## 1. Dữ liệu trên thiết bị

Room/SQLite lưu:

- lịch sử chú thích;
- đường dẫn hoặc URI ảnh do ứng dụng quản lý;
- chú thích tiếng Anh;
- bản dịch tiếng Việt khi có;
- trạng thái yêu thích;
- mô hình đã chọn và thời điểm tạo.

Suy luận ảnh không phụ thuộc PostgreSQL. Trọng số ONNX và vocabulary được đóng gói theo registry của ứng dụng.

## 2. Dữ liệu trên backend

PostgreSQL lưu dữ liệu liên quan đến:

- tài khoản và trạng thái hoạt động;
- vai trò và quyền;
- phiên/token theo hợp đồng API;
- yêu cầu đặt lại mật khẩu;
- nhật ký quản trị cần thiết.

Schema ban đầu nằm trong:

```text
backend/sql/001_schema.sql
backend/sql/002_password_reset.sql
```

## 3. Luồng dữ liệu

```text
Ảnh
  → tiền xử lý trên Android
  → encoder/decoder ONNX
  → chú thích tiếng Anh
  → dịch và đọc nội dung trên thiết bị
  → lưu lịch sử cục bộ

Tài khoản
  → FastAPI
  → xác thực và phân quyền
  → PostgreSQL
```

Không mô tả ứng dụng là tự động đồng bộ nền nếu chức năng đó chưa có kiểm thử xác nhận. Mọi tính năng đồng bộ trong tương lai phải nêu rõ thời điểm kích hoạt, dữ liệu truyền đi và cơ chế xử lý lỗi.

## 4. Kết nối PostgreSQL

Với Docker Compose mặc định:

| Trường | Nguồn giá trị |
|---|---|
| Host | `localhost` |
| Port | `POSTGRES_PORT`, mặc định `15432` |
| Database | `POSTGRES_DB` |
| Username | `POSTGRES_USER` |
| Password | `POSTGRES_PASSWORD` |

Không hard-code thông tin này trong mã nguồn hoặc tài liệu triển khai. Đọc từ `<repo>/backend/.env`.

## 5. Migration

Khởi tạo container:

```powershell
Set-Location .\backend
docker compose up -d --build
```

Áp dụng lại migration đặt lại mật khẩu:

```powershell
docker compose exec -T postgres psql `
  -U <POSTGRES_USER> `
  -d <POSTGRES_DB> `
  -f /docker-entrypoint-initdb.d/002_password_reset.sql
```

## 6. Sao lưu

```powershell
Set-Location .\backend
docker compose exec -T postgres pg_dump `
  -U <POSTGRES_USER> `
  -d <POSTGRES_DB> > backup.sql
```

Không lưu bản sao chứa dữ liệu thật trong repository.

## 7. Nguyên tắc bảo vệ dữ liệu

- Mật khẩu chỉ được lưu dưới dạng băm.
- Mã đặt lại mật khẩu có thời hạn và chỉ dùng một lần.
- Cổng PostgreSQL chỉ bind vào localhost trong cấu hình phát triển.
- API công khai phải dùng HTTPS.
- Tệp `.env`, bản sao cơ sở dữ liệu và token không được commit.
- Chỉ thu thập dữ liệu cần thiết cho chức năng đã công bố.
