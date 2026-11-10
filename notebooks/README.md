# Danh mục notebook nghiên cứu

[English version](README_EN.md) · [Quay lại README](../README.md)

Thư mục này lưu các notebook đã chọn để tái lập quá trình chuẩn bị dữ liệu, huấn luyện, chưng cất, kiểm định và đóng gói mô hình. Các notebook được giữ theo từng nhánh nghiên cứu; output gốc và manifest phải được đối chiếu trước khi dùng số liệu.

## 1. Cấu trúc

```text
notebooks/
├── mscoco/       # MS COCO 2014
├── flickr30k/    # Flickr30K
└── mobile/       # ONNX, INT8, Android lock và benchmark
```

Mỗi notebook nên có:

- tên ngắn mô tả đúng công việc;
- phần input bắt buộc;
- seed và cấu hình khóa;
- ranh giới Train/Validation/Test;
- output, manifest và checksum khi áp dụng;
- trạng thái hoàn thành hoặc lý do dừng.

## 2. Cách sử dụng

1. Mở notebook trên Kaggle.
2. Gắn đúng output notebook/dataset được liệt kê trong phần **Input bắt buộc**.
3. Chọn Accelerator và Internet theo ghi chú của notebook.
4. Chạy **Save Version** thay vì chỉ chạy phiên tương tác.
5. Tải toàn bộ output cùng log.
6. Kiểm tra manifest, checkpoint, seed và trạng thái Test trước khi chuyển bước.

Không gắn thêm input không cần thiết. Input dư có thể làm một phép tìm kiếm theo tên/hash trả về nhiều kết quả và khiến gate thất bại.

## 3. Quy tắc khoa học

- Train dùng dữ liệu huấn luyện.
- Validation dùng chọn checkpoint, early stopping và khóa phương pháp.
- Test chỉ mở một lần sau khi phương pháp đã khóa.
- Không điều chỉnh siêu tham số bằng kết quả Test.
- Mỗi kết quả tổng hợp phải truy ngược được tới seed và checkpoint.
- So sánh KD với baseline phải dùng cùng kiến trúc và ngân sách tương ứng.
- Giá trị hiển thị theo thang ×100 phải được ghi rõ; tệp máy đọc nên giữ giá trị gốc.

## 4. Quy tắc đặt tên

Notebook mới nên dùng:

```text
<dataset>-<teacher-student>-<method>-<stage>.ipynb
```

Ví dụ:

```text
mscoco-t1s1-hybrid-aggregate.ipynb
f30k-t3s3-int8-validation.ipynb
```

Tránh đưa tên người dùng, đường dẫn máy cá nhân hoặc mã phiên Kaggle vào tên.

## 5. Tái lập ngoài Kaggle

Notebook có thể giả định đường dẫn `/kaggle/input` và `/kaggle/working`. Khi chạy ở nơi khác, đặt biến môi trường hoặc sửa cell cấu hình đầu tiên; không thay đường dẫn rải rác trong nhiều cell.

Trước khi công bố:

- xuất notebook đã chạy;
- lưu log;
- lưu `requirements` hoặc phiên bản thư viện;
- lưu checksum input/output;
- xác nhận không chứa secret hoặc dữ liệu tài khoản.

## 6. Giới hạn

Thư mục này là bản đóng gói phục vụ tái lập, không phải toàn bộ lịch sử thử nghiệm. Các output được sinh tự động không nên sửa tay vì sẽ làm mất dấu vết kiểm định.
