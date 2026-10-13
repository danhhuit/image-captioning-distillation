# Vision Caption Mobile — UX/Product roadmap

## Phiên bản 1.4.0 đã triển khai

- Luồng tài khoản: đăng nhập, đăng ký Email, đăng nhập SĐT bằng OTP demo,
  quên mật khẩu và tiếp tục với tư cách Khách.
- Google Sign-In qua Android Credential Manager. Cần thay
  `google_web_client_id` bằng OAuth Web Client ID thật trước khi sử dụng.
- Ba vai trò: `SUPER_ADMIN`, `USER`, `GUEST`.
- RBAC theo tài nguyên và bốn thao tác `READ / CREATE / UPDATE / DELETE`.
- Super Admin cố định có toàn quyền và màn hình cấu hình quyền từng User.
- Guest được tạo caption và xem Teacher–Student Lab, nhưng không lưu lịch sử,
  yêu thích hoặc dùng quản trị.
- Loading overlay cho các thao tác xác thực.
- Teacher–Student Lab:
  - RAM tổng/khả dụng của thiết bị;
  - số logical CPU cores;
  - PSS hiện tại của ứng dụng;
  - dung lượng thật của Student V1 ONNX trong APK;
  - số tham số và footprint FP32 lý thuyết của Teacher C;
  - latency inference Student gần nhất nếu đã có lịch sử.

## Lưu ý bảo mật trước production

Kho tài khoản hiện tại là implementation demo cục bộ. Mật khẩu được băm bằng
PBKDF2-HMAC-SHA256 và dữ liệu auth bị loại khỏi Android backup, nhưng client
mobile không phải nơi phù hợp để làm nguồn phân quyền đáng tin cậy.

Trước khi phát hành:

1. Chuyển tài khoản, OTP và RBAC sang backend/Firebase Authentication.
2. Xác minh Google ID token ở backend.
3. Không hiển thị OTP trên màn hình; gửi bằng Firebase Phone Auth/SMS gateway.
4. Không hard-code mật khẩu Super Admin trong APK.
5. Phát hành bằng production signing key và bật Play Integrity.
6. Ghi audit log bất biến cho thay đổi quyền và thao tác quản trị.

## Chức năng nên phát triển tiếp

### Ưu tiên P0 — Hoàn thiện trải nghiệm cốt lõi

1. **Onboarding ba bước**
   - giới thiệu caption ngoại tuyến;
   - giải thích quyền Camera/Thư viện;
   - lựa chọn Guest hoặc đăng nhập.
2. **Trợ năng**
   - đọc caption bằng Text-to-Speech;
   - Dynamic Type, TalkBack labels và tương phản WCAG;
   - thao tác một tay và vùng chạm tối thiểu 48 dp.
3. **Quyền riêng tư rõ ràng**
   - màn hình “Dữ liệu nào nằm trên máy”;
   - xóa ảnh, caption hoặc toàn bộ dữ liệu;
   - khóa ứng dụng bằng sinh trắc học cho tài khoản đã đăng nhập.
4. **Trạng thái lỗi có hướng dẫn**
   - ảnh không đọc được;
   - thiếu RAM;
   - model chưa sẵn sàng;
   - Google/SMS chưa cấu hình.

### Ưu tiên P1 — Tăng giá trị cho người dùng

1. Đọc caption bằng giọng nói và điều khiển tốc độ đọc.
2. Dịch caption Việt–Anh, vẫn giữ caption gốc để đối chiếu.
3. Cho phép người dùng sửa caption và đánh dấu “hữu ích/chưa đúng”.
4. Tìm kiếm, gắn thẻ và tạo album cho lịch sử caption.
5. Xuất caption dưới dạng text, ảnh có overlay hoặc tệp JSON.
6. Batch caption nhiều ảnh với hàng đợi và progress riêng từng ảnh.
7. Đồng bộ tùy chọn giữa thiết bị sau khi có backend.

### Ưu tiên P2 — Thể hiện giá trị nghiên cứu Teacher–Student

1. Benchmark Student trên thiết bị:
   - cold/warm latency;
   - peak PSS;
   - CPU time và wall-clock time;
   - pin/nhiệt độ trước–sau;
   - kích thước model.
2. Bảng so sánh Validation Teacher/Student/KD từ artifact đã kiểm chứng:
   CIDEr-D, BLEU-4, METEOR, ROUGE-L và CE.
3. Chế độ “Giải thích mô hình” với pipeline:
   `Ảnh → Encoder → Visual memory → Decoder → Beam Search → Caption`.
4. A/B model selector khi Student KD mới được export:
   - V1 baseline;
   - Student KD;
   - không đưa Teacher lên mobile nếu chưa có benchmark và memory gate.
5. Cập nhật model có chữ ký, checksum và khả năng rollback.

### Ưu tiên P3 — Quản trị

1. Role templates: Viewer, Contributor, Moderator và Custom.
2. Nhóm người dùng và kế thừa quyền.
3. Audit log: ai đổi quyền gì, lúc nào, trước/sau.
4. Khóa/mở tài khoản, reset phiên đăng nhập và yêu cầu đổi mật khẩu.
5. Feature flags cho chức năng thử nghiệm.

## Nguyên tắc UX

- Một tác vụ chính trên mỗi màn hình.
- Không hiển thị metric nghiên cứu như phần trăm nếu metric không phải phần trăm.
- Phân biệt rõ **đo thực tế trên thiết bị** và **số liệu tham chiếu/lý thuyết**.
- Loading dài phải có mô tả công việc; không chỉ dùng spinner vô nghĩa.
- Guest luôn có đường dẫn nâng cấp lên User nhưng không bị ép đăng nhập ngay.
- Mọi thao tác xóa hoặc thay quyền cần xác nhận và phản hồi thành công/thất bại.
