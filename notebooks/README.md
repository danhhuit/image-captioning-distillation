# Danh mục notebook thực nghiệm

## Cấu trúc

```text
notebooks/
├── flickr30k/                         # 63 notebook
├── mscoco/                            # 113 notebook
│   ├── core_pipeline/
│   │   ├── phase1/
│   │   ├── phase2/
│   │   ├── phase3/
│   │   ├── phase4/
│   │   ├── phase5/
│   │   └── phase6/
│   ├── dense_mapped_logit_kd/
│   └── hybrid/
├── mobile/                            # 55 notebook
│   ├── legacy_phase7/
│   └── expanded_models/
├── MANIFEST.csv
└── COLLECTIONS.csv
```

## Phân loại

| Nhóm | Số notebook | Nội dung chính |
|---|---:|---|
| `flickr30k` | 63 | Dữ liệu, baseline Student, Teacher handoff, Sequence KD, Direct Logit-KD, Dense-Mapped Logit-KD, Hybrid KD, kiểm định, ONNX và INT8 trên Flickr30K |
| `mscoco` | 113 | Quy trình MS COCO 2014, baseline Teacher–Student, các nhánh KD, Dense-Mapped Logit-KD, kết quả định tính và Hybrid KD |
| `mobile` | 55 | Xuất mô hình, kiểm tra ONNX, lượng tử hóa INT8, khóa package Android và kiểm định chất lượng trước triển khai |

## Tệp kiểm kê

- `MANIFEST.csv`: đường dẫn tương đối, dung lượng và SHA-256 hiện tại của từng notebook.
- `COLLECTIONS.csv`: nguồn dữ liệu ban đầu, nhóm đích và số notebook trong từng collection.
