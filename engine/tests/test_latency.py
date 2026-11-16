"""EP latency bench contracts — required rows + GeoTr dyn vs WO pick."""

from __future__ import annotations

import json
from pathlib import Path

import numpy as np
import onnx
import pytest
from onnx import TensorProto, helper, numpy_helper

from scanbooks_engine.latency import (
    REQUIRED_EP_ROWS,
    TICKET_ID,
    build_report,
    estimate_ill_patch_count,
    measure_inference_ms,
    pick_geotr_quant,
    render_md,
    to_fp16_model,
)

BENCH = Path(__file__).resolve().parents[1] / "benches" / "latency.json"


def _tiny_matmul_onnx(path: Path) -> None:
    w = numpy_helper.from_array(np.arange(8, dtype=np.float32).reshape(4, 2), name="W")
    b = numpy_helper.from_array(np.zeros(2, dtype=np.float32), name="B")
    graph = helper.make_graph(
        [
            helper.make_node("MatMul", ["X", "W"], ["Y0"]),
            helper.make_node("Add", ["Y0", "B"], ["Y"]),
        ],
        "tiny",
        [helper.make_tensor_value_info("X", TensorProto.FLOAT, [1, 4])],
        [helper.make_tensor_value_info("Y", TensorProto.FLOAT, [1, 2])],
        [w, b],
    )
    model = helper.make_model(graph, opset_imports=[helper.make_opsetid("", 17)])
    onnx.checker.check_model(model)
    onnx.save(model, str(path))


def test_required_ep_rows_contract():
    assert "cpu_fp32" in REQUIRED_EP_ROWS
    assert "cpu_weight_int8" in REQUIRED_EP_ROWS
    assert "coreml_fp16" in REQUIRED_EP_ROWS or "nnapi_fp16" in REQUIRED_EP_ROWS


def test_estimate_ill_patch_count_work_1024():
    # stride = 128 - 16 = 112 → 9×9 @ 1024×1024
    assert estimate_ill_patch_count(1024, 1024) == 81


def test_pick_geotr_quant_prefers_faster_within_error():
    assert (
        pick_geotr_quant(
            dyn_ms=120.0,
            wo_ms=200.0,
            dyn_max_abs=0.0037,
            wo_max_abs=0.0005,
            dyn_mb=23.5,
            wo_mb=37.5,
        )
        == "dynamic_int8"
    )
    assert (
        pick_geotr_quant(
            dyn_ms=250.0,
            wo_ms=180.0,
            dyn_max_abs=0.0037,
            wo_max_abs=0.0005,
            dyn_mb=23.5,
            wo_mb=37.5,
        )
        == "weight_only_int8"
    )


def test_measure_and_fp16_on_tiny(tmp_path: Path):
    src = tmp_path / "tiny.onnx"
    _tiny_matmul_onnx(src)
    fp16 = tmp_path / "tiny_fp16.onnx"
    to_fp16_model(src, fp16)
    assert fp16.exists() and fp16.stat().st_size > 0
    x = np.ones((1, 4), dtype=np.float32)
    row = measure_inference_ms(src, {"X": x}, providers=["CPUExecutionProvider"], warmup=1, repeats=2)
    assert row["ms"] >= 0
    assert row["status"] == "ok"


def test_bench_json_snapshot_aligned():
    assert BENCH.exists(), "run scanbooks-latency-bench --frozen (or measure) first"
    data = json.loads(BENCH.read_text())
    assert data["ticket"].startswith("3ad0ee61")
    rows = data["rows"]
    for key in ("cpu_fp32", "cpu_weight_int8"):
        assert key in rows
        assert rows[key]["geotr"]["status"] == "ok"
        assert rows[key]["geotr"]["ms"] > 0
    # CoreML FP16 or documented host fallback
    mobile = rows.get("coreml_fp16") or rows.get("nnapi_fp16")
    assert mobile is not None
    assert data["gates"]["geotr_e2e_s_budget"] == 2.5
    assert data["gates"]["illtr_e2e_s_budget"] == 3.5
    assert data["recommendation"]["geotr_quant"] in ("dynamic_int8", "weight_only_int8", "either", "fp32")
    assert "LayerNormalization" in data["fallback_notes"] or "fallback" in data["fallback_notes"]
    md = render_md(data)
    assert "CPU" in md and "weight" in md.lower()
    assert TICKET_ID[:8] in md


def test_build_report_frozen_shape():
    if not BENCH.exists():
        pytest.skip("latency.json not written yet")
    data = build_report()
    assert data["ticket"] == TICKET_ID
    assert "cpu_fp32" in data["rows"]
