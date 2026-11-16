"""ONNX quantization candidate contracts — policy + synthetic model smoke."""

from __future__ import annotations

import json
from pathlib import Path

import numpy as np
import onnx
import pytest
from onnx import TensorProto, helper, numpy_helper

from scanbooks_engine.quantize import (
    ALLOWED_CANDIDATES,
    FORBIDDEN_DEFAULT,
    StaticInt8Forbidden,
    evaluate_outputs,
    quantize_dynamic_int8,
    quantize_weight_only_int8,
    refuse_static_int8_default,
    run_candidate_matrix,
)

BENCH = Path(__file__).resolve().parents[1] / "benches" / "quantize_candidates.json"


def _tiny_matmul_onnx(path: Path) -> None:
    """1×4 → Linear(4→2) MatMul+Add — enough for dynamic / MatMulNBits."""
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


def test_policy_static_int8_forbidden_default():
    assert "static_int8" in FORBIDDEN_DEFAULT
    assert "weight_only_int8" in ALLOWED_CANDIDATES
    assert "dynamic_int8" in ALLOWED_CANDIDATES
    with pytest.raises(StaticInt8Forbidden):
        refuse_static_int8_default()


def test_dynamic_and_weight_only_on_tiny_model(tmp_path: Path):
    src = tmp_path / "tiny.onnx"
    _tiny_matmul_onnx(src)
    dyn = tmp_path / "tiny_dyn.onnx"
    wo = tmp_path / "tiny_wo.onnx"
    quantize_dynamic_int8(src, dyn)
    quantize_weight_only_int8(src, wo)
    assert dyn.exists() and dyn.stat().st_size > 0
    assert wo.exists() and wo.stat().st_size > 0
    x = np.ones((1, 4), dtype=np.float32)
    metrics = evaluate_outputs(src, dyn, {"X": x})
    assert metrics["max_abs"] < 0.05
    metrics_wo = evaluate_outputs(src, wo, {"X": x})
    assert metrics_wo["max_abs"] < 0.05


def test_run_candidate_matrix_skips_missing(tmp_path: Path):
    report = run_candidate_matrix(tmp_path / "missing", tmp_path / "out", models=("geotr",))
    assert report["models"]["geotr"]["status"] == "skipped_missing_fp32"
    assert report["policy"]["static_int8"] == "forbidden_default"


def test_bench_json_snapshot_aligned():
    data = json.loads(BENCH.read_text())
    assert data["ticket"].startswith("0be42506")
    assert data["policy"]["static_int8"] == "forbidden_default"
    assert set(data["allowed_candidates"]) >= {"weight_only_int8", "dynamic_int8"}
    geo = data["models"]["geotr"]
    assert geo["fp32_mb"] == 86.42
    assert geo["dynamic_int8"]["mb"] < geo["fp32_mb"]
    assert geo["weight_only_int8"]["mb"] < geo["fp32_mb"]
    assert geo["dynamic_int8"]["max_abs"] < 0.01
    assert geo["weight_only_int8"]["max_abs"] < 0.01
    assert data["recommendation"]["geotr"] in ("dynamic_int8", "weight_only_int8", "either")
    assert data["recommendation"]["static_int8"] == "do_not_adopt_as_default"
    assert "IllTr" in data["notes"]["illtr_graph"] or "illtr" in data["notes"]["illtr_graph"].lower()
