# Image Captioning Distillation

## 1. Giới thiệu

Dự án này thực hiện bài toán **Image Captioning** bằng cách kết hợp các mô hình **Visual Encoder** với **Transformer Decoder** để sinh mô tả tự động cho ảnh.

Trong giai đoạn thực nghiệm tuần 02 và tuần 03, dự án tập trung đánh giá bốn mô hình encoder:

- ResNet-50 + Transformer Decoder
- EfficientNet-B3 + Transformer Decoder
- ViT-B/16 + Transformer Decoder
- CLIP-ViT-B/16 + Transformer Decoder

Mục tiêu chính là xây dựng pipeline hoàn chỉnh từ dữ liệu ảnh đến caption, huấn luyện mô hình, lưu checkpoint, sinh caption và đánh giá kết quả bằng các chỉ số phổ biến trong Image Captioning.

---

## 2. Mục tiêu thực nghiệm

Dự án hướng đến các mục tiêu sau:

- Xây dựng pipeline Image Captioning end-to-end.
- Trích xuất đặc trưng ảnh bằng nhiều visual encoder khác nhau.
- Huấn luyện Transformer Decoder để sinh caption.
- Lưu và tải checkpoint cho từng mô hình.
- Sinh caption bằng Beam Search.
- Đánh giá kết quả bằng BLEU-1, BLEU-4, METEOR, ROUGE-L và CIDEr.
- So sánh hiệu quả giữa CNN, Vision Transformer và CLIP-ViT.
- Xác định mô hình phù hợp để làm teacher model cho giai đoạn Knowledge Distillation.

---

## 3. Bộ dữ liệu sử dụng

### 3.1 Flickr8K

Ở tuần 02, dự án thực nghiệm trên bộ dữ liệu Flickr8K.

| Tập dữ liệu | Số ảnh |
|---|---:|
| Train | 6.000 |
| Validation | 1.000 |
| Test | 1.000 |

Thông tin cấu hình:

| Tham số | Giá trị |
|---|---:|
| Vocabulary size | 2.556 |
| Batch size | 128 |
| Epoch tối đa | 20 |
| Learning rate | 0.0003 |
| Beam size | 3 |
| Seed | 42 |

---

### 3.2 MS COCO 2017

Ở tuần 03, pipeline được mở rộng lên bộ dữ liệu MS COCO 2017.

| Tập dữ liệu | Số ảnh |
|---|---:|
| Train | 118.287 |
| Validation | 2.500 |
| Test | 2.500 |

Thông tin cấu hình:

| Tham số | Giá trị |
|---|---:|
| Vocabulary size | 10.312 |
| Batch size | 64 |
| Epoch | 20 |
| Learning rate | 0.0001 |
| Beam size | 3 |
| MAX_LEN | 30 |
| MIN_FREQ | 5 |
| Seed | 42 |

Ghi chú:

- Toàn bộ tập `train2017` được dùng để huấn luyện.
- Tập `val2017` được chia thành 2.500 ảnh validation và 2.500 ảnh test.
- Việc chia dữ liệu sử dụng `seed=42` để đảm bảo kết quả có thể tái lập.
- Vocabulary chỉ được xây dựng từ caption của tập train.

---

## 4. Môi trường thực nghiệm

### Flickr8K

| Thành phần | Thông tin |
|---|---|
| Nền tảng | Google Colab |
| GPU | NVIDIA Tesla T4 |
| Python | 3.12.13 |
| PyTorch | 2.11.0+cu128 |
| TorchVision | 0.26.0+cu128 |

### MS COCO 2017

| Thành phần | Thông tin |
|---|---|
| Nền tảng | Kaggle |
| GPU | Tesla T4 - 14.56 GB VRAM |
| PyTorch | 2.10.0+cu128 |
| TorchVision | 0.25.0+cu128 |

---

## 5. Pipeline thực hiện

Pipeline Image Captioning trong dự án gồm các bước chính:

```text
Ảnh đầu vào
    ↓
Tiền xử lý ảnh
    ↓
Visual Encoder
    ↓
Feature ảnh
    ↓
Projection về d_model = 256
    ↓
Transformer Decoder
    ↓
Beam Search
    ↓
Caption
