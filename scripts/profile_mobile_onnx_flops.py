from __future__ import annotations

import csv
import json
import sys
import warnings
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
TOOL_ROOT = ROOT / ".tools" / "onnx_tool"
if TOOL_ROOT.exists():
    sys.path.insert(0, str(TOOL_ROOT))

import onnx_tool  # noqa: E402


OUTPUT_DIR = (
    ROOT
    / "output_codex"
    / "04_thuc_nghiem"
    / "mobile_compute_cost"
)
MAX_GENERATED_TOKENS = 30
BEAM_SIZE = 3
TWO_FLOP_OPS = {
    "Conv",
    "ConvInteger",
    "Gemm",
    "MatMul",
    "MatMulInteger",
    "QLinearConv",
    "QLinearMatMul",
}


MODELS = [
    {
        "model_id": "MSCOCO_S1",
        "group": "Student — MS COCO 2014",
        "component": "S1: MobileNetV3-Large + bộ giải mã Transformer",
        "parameters": 8_990_352,
        "encoder": ROOT / "output_v2/mscoco-hybrid/KLCN_V2_HYB_ONNX_FP32/exports/hybrid_kd_s1_seed2026_encoder_fp32.onnx",
        "decoder": ROOT / "output_v2/mscoco-hybrid/KLCN_V2_HYB_ONNX_FP32/exports/hybrid_kd_s1_seed2026_decoder_fp32.onnx",
    },
    {
        "model_id": "MSCOCO_S2",
        "group": "Student — MS COCO 2014",
        "component": "S2: MobileViT-S + bộ giải mã Transformer",
        "parameters": 10_874_112,
        "encoder": ROOT / "output_v2/phase7/07g/phase7g_t2_s2_ort_mobile_export_preflight/exports/encoder_fp32.onnx",
        "decoder": ROOT / "output_v2/phase7/07g/phase7g_t2_s2_ort_mobile_export_preflight/exports/decoder_fp32.onnx",
    },
    {
        "model_id": "MSCOCO_S3",
        "group": "Student — MS COCO 2014",
        "component": "S3: TinyViT-5M + bộ giải mã Transformer",
        "parameters": 10_925_684,
        "encoder": ROOT / "output_v2/phase7/07g/phase7g_t3_s3_ort_mobile_export_preflight/exports/encoder_fp32.onnx",
        "decoder": ROOT / "output_v2/phase7/07g/phase7g_t3_s3_ort_mobile_export_preflight/exports/decoder_fp32.onnx",
    },
    {
        "model_id": "F30K_S1",
        "group": "Student — Flickr30K",
        "component": "S1: MobileNetV3-Large + bộ giải mã Transformer",
        "parameters": 8_315_984,
        "encoder": ROOT / "output_v2/mob/KLCN_V2_MOB_F30K_T1S1_Seq_ONNX_FP32/exports/f30k_t1s1_seq_encoder_fp32.onnx",
        "decoder": ROOT / "output_v2/mob/KLCN_V2_MOB_F30K_T1S1_Seq_ONNX_FP32/exports/f30k_t1s1_seq_decoder_fp32.onnx",
    },
    {
        "model_id": "F30K_S2",
        "group": "Student — Flickr30K",
        "component": "S2: MobileViT-S + bộ giải mã Transformer",
        "parameters": 10_199_744,
        "encoder": ROOT / "output_v2/mob/KLCN_V2_MOB_F30K_T2S2_Seq_ONNX_FP32/exports/f30k_t2s2_seq_encoder_fp32.onnx",
        "decoder": ROOT / "output_v2/mob/KLCN_V2_MOB_F30K_T2S2_Seq_ONNX_FP32/exports/f30k_t2s2_seq_decoder_fp32.onnx",
    },
    {
        "model_id": "F30K_S3",
        "group": "Student — Flickr30K",
        "component": "S3: TinyViT-5M + bộ giải mã Transformer",
        "parameters": 10_251_316,
        "encoder": ROOT / "output_v2/f30k/KLCN_V2_F30K_T3S3_ONNX_FP32/f30k_t3s3_seed42_encoder_fp32.onnx",
        "decoder": ROOT / "output_v2/f30k/KLCN_V2_F30K_T3S3_ONNX_FP32/f30k_t3s3_seed42_decoder_fp32.onnx",
    },
]


def profile_graph(path: Path) -> dict[str, float | int]:
    if not path.is_file():
        raise FileNotFoundError(path)
    model = onnx_tool.loadmodel(str(path))
    graph = model.graph
    graph.graph_reorder_nodes()
    graph.shape_infer()
    graph.profile()

    operations = 0.0
    macs = 0.0
    for node in graph.nodemap.values():
        node_cost = float(node.macs[0])
        if node.op_type in TWO_FLOP_OPS:
            macs += node_cost
            operations += 2.0 * node_cost
        else:
            operations += node_cost
    return {
        "macs": int(round(macs)),
        "operations": int(round(operations)),
    }


def decoder_batch(path: Path) -> int:
    import onnx

    model = onnx.load(str(path), load_external_data=False)
    tensor = model.graph.input[0].type.tensor_type
    first = tensor.shape.dim[0]
    return int(first.dim_value or 1)


def maximum_decoder_calls(batch: int) -> int:
    if batch >= BEAM_SIZE:
        return MAX_GENERATED_TOKENS
    return 1 + BEAM_SIZE * (MAX_GENERATED_TOKENS - 1)


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    rows = []
    warnings.filterwarnings("ignore", category=UserWarning, module="onnx_tool")

    for item in MODELS:
        encoder = profile_graph(item["encoder"])
        decoder = profile_graph(item["decoder"])
        batch = decoder_batch(item["decoder"])
        calls = maximum_decoder_calls(batch)
        maximum_caption_operations = (
            encoder["operations"] + calls * decoder["operations"]
        )
        rows.append(
            {
                "model_id": item["model_id"],
                "group": item["group"],
                "component": item["component"],
                "parameters": item["parameters"],
                "parameters_m": round(item["parameters"] / 1_000_000, 6),
                "encoder_gflops": round(encoder["operations"] / 1_000_000_000, 6),
                "decoder_gflops_per_call": round(decoder["operations"] / 1_000_000_000, 6),
                "decoder_fixed_batch": batch,
                "maximum_decoder_calls": calls,
                "maximum_caption_gflops": round(maximum_caption_operations / 1_000_000_000, 6),
                "maximum_generated_tokens": MAX_GENERATED_TOKENS,
                "beam_size": BEAM_SIZE,
                "encoder_onnx": str(item["encoder"]),
                "decoder_onnx": str(item["decoder"]),
            }
        )

    generated_at = datetime.now(timezone.utc).isoformat()
    payload = {
        "schema_version": "mobile_onnx_flops_profile_v1",
        "generated_at_utc": generated_at,
        "status": "passed",
        "convention": {
            "multiply_accumulate": "1 MAC = 2 FLOPs",
            "scope": "one encoder call plus maximum autoregressive decoder calls",
            "maximum_generated_tokens": MAX_GENERATED_TOKENS,
            "beam_size": BEAM_SIZE,
            "warning": "Static upper bound, not a physical-device runtime counter.",
        },
        "models": rows,
    }

    json_path = OUTPUT_DIR / "MOBILE_ONNX_FLOPS_PROFILE.json"
    csv_path = OUTPUT_DIR / "MOBILE_ONNX_FLOPS_PROFILE.csv"
    md_path = OUTPUT_DIR / "MOBILE_ONNX_FLOPS_PROFILE.md"
    json_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

    with csv_path.open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)

    lines = [
        "# Hồ sơ FLOPs của các Student triển khai trên thiết bị di động",
        "",
        "Quy ước: 1 MAC = 2 FLOPs. FLOPs/caption là cận trên tĩnh với Beam-3, tối đa 30 token; không phải bộ đếm phần cứng trên điện thoại.",
        "",
        "| Nhóm thành phần | Thành phần | Parameters (M) | Encoder (GFLOPs) | Decoder/lần gọi (GFLOPs) | Số lần gọi decoder tối đa | FLOPs/caption tối đa (G) |",
        "|---|---|---:|---:|---:|---:|---:|",
    ]
    for row in rows:
        lines.append(
            "| {group} | {component} | {parameters_m:.2f} | {encoder_gflops:.3f} | "
            "{decoder_gflops_per_call:.3f} | {maximum_decoder_calls} | {maximum_caption_gflops:.2f} |".format(**row)
        )
    lines.extend(
        [
            "",
            "FLOPs không thay đổi theo CE, Sequence KD, Direct Logit-KD, Dense-Mapped Logit-KD hoặc Hybrid KD khi kiến trúc Student và cấu hình giải mã không đổi.",
        ]
    )
    md_path.write_text("\n".join(lines) + "\n", encoding="utf-8")

    print(f"JSON: {json_path}")
    print(f"CSV : {csv_path}")
    print(f"MD  : {md_path}")
    for row in rows:
        print(
            f"{row['model_id']}: {row['maximum_caption_gflops']:.2f} GFLOPs/caption "
            f"(encoder={row['encoder_gflops']:.3f}, decoder/call={row['decoder_gflops_per_call']:.3f}, calls={row['maximum_decoder_calls']})"
        )


if __name__ == "__main__":
    main()
