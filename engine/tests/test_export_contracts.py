"""Export prototype contracts — shapes, FP16 budget, ORT op notes."""

from __future__ import annotations

import json
from pathlib import Path

import pytest

from scanbooks_engine.contracts import (
    GEO_NET_SIZE,
    ILL_OVERLAP_PX,
    ILL_PATCH_SIZE,
    MEASURED,
    RESIDENT_FP16_BUDGET_MB,
    RESIDENT_FP16_MB_GEO_ILL_SEG,
)
from scanbooks_engine.measure import fp16_mb, inspect_onnx_ops, summarize_compatibility

BENCH = Path(__file__).resolve().parents[1] / "benches" / "export_prototype.json"


def test_resolution_constants_match_architecture():
    assert GEO_NET_SIZE == 288
    assert ILL_PATCH_SIZE == 128
    assert ILL_OVERLAP_PX == 16


def test_measured_io_shapes():
    assert MEASURED["geotr"]["in_nchw"] == (1, 3, 288, 288)
    assert MEASURED["geotr"]["out_nchw"] == (1, 2, 288, 288)
    assert MEASURED["illtr"]["in_nchw"] == (1, 3, 128, 128)
    assert MEASURED["illtr"]["out_nchw"] == (1, 3, 128, 128)
    assert MEASURED["seg"]["in_nchw"] == (1, 3, 288, 288)
    assert MEASURED["seg"]["out_nchw"] == (1, 1, 288, 288)


def test_fp16_budget_under_80mb():
    assert fp16_mb(MEASURED["geotr"]["params"]) == MEASURED["geotr"]["fp16_mb"]
    assert fp16_mb(MEASURED["illtr"]["params"]) == MEASURED["illtr"]["fp16_mb"]
    assert fp16_mb(MEASURED["seg"]["params"]) == MEASURED["seg"]["fp16_mb"]
    total = (
        MEASURED["geotr"]["fp16_mb"]
        + MEASURED["illtr"]["fp16_mb"]
        + MEASURED["seg"]["fp16_mb"]
    )
    assert round(total, 2) == RESIDENT_FP16_MB_GEO_ILL_SEG
    assert RESIDENT_FP16_MB_GEO_ILL_SEG <= RESIDENT_FP16_BUDGET_MB


def test_bench_json_snapshot_aligned():
    data = json.loads(BENCH.read_text())
    for name in ("geotr", "illtr", "seg"):
        assert data["stats"][name]["params"] == MEASURED[name]["params"]
        assert data["stats"][name]["in"] == list(MEASURED[name]["in_nchw"])
        assert data["stats"][name]["out"] == list(MEASURED[name]["out_nchw"])
    assert data["onnx"]["geotr"]["has_GridSample"] is False
    assert data["onnx"]["illtr"]["has_GridSample"] is False
    assert "LayerNormalization" in data["onnx"]["geotr"]["all_ops"]
    assert "Reshape" in data["onnx"]["geotr"]["all_ops"]
    assert "Reshape" in data["onnx"]["illtr"]["all_ops"]


def test_summarize_compatibility_without_gridsample():
    notes = summarize_compatibility(
        {"has_GridSample": False, "has_LayerNormalization": True, "has_Reshape": True, "watch": {"Reshape": 10}}
    )
    assert "absent" in notes["GridSample"]
    assert "present" in notes["LayerNormalization"]


def test_inspect_onnx_ops_optional():
    onnx_path = Path("/tmp/doctr-onnx/geotr.onnx")
    if not onnx_path.exists():
        pytest.skip("local ONNX artifact not present")
    rep = inspect_onnx_ops(onnx_path)
    assert rep["has_GridSample"] is False
    assert rep["has_LayerNormalization"] is True
    assert rep["has_Reshape"] is True
