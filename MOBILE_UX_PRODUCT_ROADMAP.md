# Lộ trình mini-app Mobile Image Captioning

[English version](MOBILE_UX_PRODUCT_ROADMAP_EN.md) · [Quay lại README](README.md)

Tài liệu này ghi nhận trạng thái sản phẩm ở phiên bản 1.7.0 và các hạng mục cần kiểm chứng trước khi phát hành rộng rãi. Nội dung không thay thế issue tracker hoặc kết quả kiểm thử.

## 1. Đã có trong phiên bản hiện tại

- Điều hướng năm tab: Lịch sử, Yêu thích, Chụp, Mô hình và Cá nhân.
- Chọn mô hình theo hai nhóm chính/thử nghiệm.
- Hiển thị tên Teacher–Student, dữ liệu, phương pháp KD và CIDEr-D.
- Suy luận ONNX trên thiết bị.
- Dịch Anh–Việt, Text-to-Speech và chia sẻ ảnh kèm chú thích.
- Lịch sử và yêu thích lưu cục bộ.
- Giao diện Việt/Anh và sáng/tối.
- Đăng ký, đăng nhập, Google Sign-In và quên mật khẩu qua API.
- Chức năng quản trị tài khoản theo quyền.

## 2. Danh mục mô hình đang hiển thị

Mô hình chính:

1. MS COCO 2014 — ExpansionNet v2 → MobileNetV3-Large — Sequence KD.
2. MS COCO 2014 — ExpansionNet v2 → MobileNetV3-Large — Dense-Mapped Logit-KD.
3. MS COCO 2014 — ExpansionNet v2 → MobileNetV3-Large — Hybrid KD.
4. MS COCO 2014 — BLIP Base → MobileViT-S — Sequence KD.
5. MS COCO 2014 — SigLIP ViT-B/16 → TinyViT-5M — Direct Logit-KD.

Mô hình thử nghiệm:

1. V1 FP32.
2. MS COCO 2014 — CLIP ViT-B/32 → MobileNetV3-Large — Direct Logit-KD.
3. Flickr30K — ExpansionNet v2 → MobileNetV3-Large — Sequence KD.
4. Flickr30K — ExpansionNet v2 → MobileNetV3-Large — Dense-Mapped Logit-KD.
5. Flickr30K — ExpansionNet v2 → MobileNetV3-Large — Hybrid KD.
6. Flickr30K — BLIP Base → MobileViT-S — Sequence KD.
7. Flickr30K — SigLIP ViT-B/16 → TinyViT-5M — Direct Logit-KD.

## 3. Việc cần hoàn tất trước khi phát hành

### Mức P0

- Kiểm thử đăng nhập trên Emulator, USB và Wi-Fi với hướng dẫn API mới.
- Kiểm thử mã đặt lại mật khẩu gửi tới đúng email.
- Kiểm thử khóa, mở khóa, chỉnh sửa và xóa tài khoản.
- Xác nhận mọi model registry trỏ tới asset thật.
- Kiểm thử cold start, xoay màn hình, cấp/thu hồi quyền ảnh và máy ảnh.
- Kiểm tra màn hình nhỏ, cỡ chữ lớn và chuỗi dài ở cả hai ngôn ngữ.

### Mức P1

- Chạy benchmark thống nhất trên thiết bị thật cho S1, S2 và S3.
- Ghi nhận p50/p95, PSS, kích thước gói và nhiệt độ theo cùng giao thức.
- Kiểm thử chia sẻ ảnh/caption trên nhiều ứng dụng đích.
- Kiểm thử tải lại mô hình khi chuyển liên tục giữa các checkpoint.
- Bổ sung kiểm thử accessibility cho nhãn nút và vùng chạm.

### Mức P2

- Cải thiện màn hình so sánh mô hình.
- Thêm xuất lịch sử do người dùng chủ động kích hoạt.
- Chuẩn hóa chính sách quyền riêng tư và thời hạn lưu dữ liệu.
- Đánh giá cập nhật model package tách khỏi APK nếu cần giảm kích thước cài đặt.

## 4. Tiêu chí hoàn thành

Một hạng mục chỉ được đánh dấu hoàn tất khi có:

1. mã nguồn đã merge;
2. test tự động hoặc biên bản test thủ công có thể lặp lại;
3. ảnh/log đầu ra;
4. tài liệu người dùng cập nhật;
5. không còn lỗi P0 liên quan.

## 5. Nguyên tắc trình bày kết quả

- Phân biệt rõ mô hình chính và mô hình thử nghiệm.
- Không gọi một nhánh là cải thiện nếu CIDEr-D thấp hơn baseline.
- Không dùng số đo Emulator như kết quả thiết bị thật.
- Không suy diễn đồng bộ nền hoặc chức năng chưa có test.
- Ghi rõ tập dữ liệu, seed, checkpoint và dạng lượng tử hóa khi báo cáo.
