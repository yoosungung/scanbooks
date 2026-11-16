"""ARCHITECTURE §5 resolution / I/O contracts (engine-side constants)."""

from __future__ import annotations

GEO_NET_SIZE = 288
ILL_PATCH_SIZE = 128
ILL_OVERLAP_RATIO = 0.125
ILL_OVERLAP_PX = int(ILL_PATCH_SIZE * ILL_OVERLAP_RATIO)  # 16
WORK_LONG_EDGE = 1024
FLOW_SCALE_DIV = 286.8
FLOW_SCALE_MUL = 0.99

# §3.1 — margin as fraction of the longer image side (engine-confirmed).
CROP_MARGIN_RATIO = 0.05

# §1.3 / §3 — app default page pipeline (crop is ROI; geo→finger→ill after).
PIPELINE_DEFAULT_ORDER = ("crop", "geo", "finger", "ill", "done")

# Measured from official DocTr checkpoints (HF Space mirror of Google Drive weights).
# Source: engine/benches/export_prototype.json
MEASURED = {
    "geotr": {
        "params": 25_747_330,
        "fp16_mb": 49.11,
        "in_nchw": (1, 3, GEO_NET_SIZE, GEO_NET_SIZE),
        "out_nchw": (1, 2, GEO_NET_SIZE, GEO_NET_SIZE),
        "dtype": "float32",
    },
    "illtr": {
        "params": 12_907_872,
        "fp16_mb": 24.62,
        "in_nchw": (1, 3, ILL_PATCH_SIZE, ILL_PATCH_SIZE),
        "out_nchw": (1, 3, ILL_PATCH_SIZE, ILL_PATCH_SIZE),
        "dtype": "float32",
    },
    "seg": {
        "params": 1_131_181,
        "fp16_mb": 2.16,
        "in_nchw": (1, 3, GEO_NET_SIZE, GEO_NET_SIZE),
        "out_nchw": (1, 1, GEO_NET_SIZE, GEO_NET_SIZE),
        "dtype": "float32",
    },
}

RESIDENT_FP16_MB_GEO_ILL_SEG = 75.89
RESIDENT_FP16_BUDGET_MB = 80.0
