"""ONNX quantization candidates for DocTr exports (M1).

Allowed: weight-only INT8 (MatMulNBits bits=8), dynamic INT8.
Forbidden as default: static INT8 (activation calibration) — GeoTr flow risk.
"""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
from typing import Any

import numpy as np

from scanbooks_engine.contracts import MEASURED

ALLOWED_CANDIDATES = ("weight_only_int8", "dynamic_int8", "mixed_exclude_flow_head")
FORBIDDEN_DEFAULT = ("static_int8",)

TICKET_ID = "0be42506-ebb3-4175-909c-3eb656a3307e"

# Frozen CPU ORT smoke (seed=0) from export_prototype ONNX under /tmp/doctr-onnx.
# Re-measure with: scanbooks-quantize-candidates --onnx-dir … --out-dir …
FROZEN_MEASUREMENTS: dict[str, Any] = {
    "geotr": {
        "fp32_mb": 86.42,
        "dynamic_int8": {
            "mb": 23.49,
            "max_abs": 0.0037404298782348633,
            "mean_abs": 0.000526959600392729,
            "load_run_s": 0.26,
        },
        "weight_only_int8": {
            "mb": 37.45,
            "max_abs": 0.0004938244819641113,
            "mean_abs": 0.00012161299673607573,
            "load_run_s": 0.25,
        },
        "status": "ok",
    },
    "illtr": {
        "fp32_mb": 66.29,
        "dynamic_int8": {
            "mb": 42.32,
            "max_abs": 0.009683221578598022,
            "mean_abs": 0.0016955056926235557,
            "load_run_s": 3.63,
        },
        "weight_only_int8": {
            "mb": 35.13,
            "max_abs": 0.0062674880027771,
            "mean_abs": 0.0010416271397843957,
            "load_run_s": 3.76,
        },
        "status": "ok",
    },
    "seg": {
        "fp32_mb": 4.39,
        "dynamic_int8": {
            "mb": 1.39,
            "max_abs": 0.9737850427627563,
            "mean_abs": 0.07751336693763733,
            "load_run_s": 0.08,
        },
        "weight_only_int8": {
            "mb": 4.39,
            "max_abs": 0.0,
            "mean_abs": 0.0,
            "load_run_s": 0.09,
            "note": "MatMulNBits no-op — U2NETP is Conv-heavy",
        },
        "status": "ok",
    },
}


class StaticInt8Forbidden(RuntimeError):
    """Raised when static INT8 is requested as a default deployment path."""


def refuse_static_int8_default() -> None:
    raise StaticInt8Forbidden(
        "static INT8 (activation calibration) must not be the default GeoTr/IllTr "
        "deployment path — use weight-only INT8, dynamic INT8, or mixed (exclude flow head)"
    )


def quantize_dynamic_int8(model_input: str | Path, model_output: str | Path, **kwargs: Any) -> Path:
    from onnxruntime.quantization import QuantType, quantize_dynamic

    out = Path(model_output)
    out.parent.mkdir(parents=True, exist_ok=True)
    opts = {
        "weight_type": QuantType.QInt8,
        "per_channel": True,
        **kwargs,
    }
    quantize_dynamic(str(model_input), str(out), **opts)
    return out


def quantize_weight_only_int8(
    model_input: str | Path,
    model_output: str | Path,
    *,
    bits: int = 8,
    block_size: int = 128,
    is_symmetric: bool = True,
) -> Path:
    import onnx
    from onnxruntime.quantization.matmul_nbits_quantizer import MatMulNBitsQuantizer
    from onnxruntime.quantization.quant_utils import QuantFormat

    if bits != 8:
        raise ValueError("M1 candidate is weight-only INT8 (bits=8); other widths are out of scope")
    out = Path(model_output)
    out.parent.mkdir(parents=True, exist_ok=True)
    q = MatMulNBitsQuantizer(
        str(model_input),
        bits=bits,
        block_size=block_size,
        is_symmetric=is_symmetric,
        quant_format=QuantFormat.QOperator,
    )
    q.process()
    onnx.save(q.model.model, str(out))
    return out


def evaluate_outputs(
    fp32_path: str | Path,
    quant_path: str | Path,
    feeds: dict[str, np.ndarray],
) -> dict[str, float]:
    import onnxruntime as ort

    base = ort.InferenceSession(str(fp32_path), providers=["CPUExecutionProvider"]).run(None, feeds)[0]
    out = ort.InferenceSession(str(quant_path), providers=["CPUExecutionProvider"]).run(None, feeds)[0]
    diff = np.abs(base.astype(np.float64) - out.astype(np.float64))
    return {
        "max_abs": float(diff.max()),
        "mean_abs": float(diff.mean()),
    }


def _input_shape(model: str) -> tuple[int, ...]:
    return MEASURED[model]["in_nchw"]


def run_candidate_matrix(
    onnx_dir: str | Path,
    out_dir: str | Path,
    *,
    models: tuple[str, ...] = ("geotr", "illtr", "seg"),
    seed: int = 0,
    measure: bool = True,
) -> dict[str, Any]:
    """Quantize available FP32 ONNX files and optionally ORT-smoke vs FP32."""
    onnx_dir = Path(onnx_dir)
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    models_out: dict[str, Any] = {}

    for name in models:
        src = onnx_dir / f"{name}.onnx"
        if not src.exists():
            models_out[name] = {"status": "skipped_missing_fp32"}
            continue
        row: dict[str, Any] = {
            "fp32_mb": round(src.stat().st_size / (1024 * 1024), 2),
            "status": "ok",
        }
        dyn = out_dir / f"{name}_dynamic_int8.onnx"
        wo = out_dir / f"{name}_weight_only_int8.onnx"
        quantize_dynamic_int8(src, dyn)
        quantize_weight_only_int8(src, wo)
        row["dynamic_int8"] = {"mb": round(dyn.stat().st_size / (1024 * 1024), 2)}
        row["weight_only_int8"] = {"mb": round(wo.stat().st_size / (1024 * 1024), 2)}
        if measure:
            rng = np.random.RandomState(seed)
            x = rng.randn(*_input_shape(name)).astype(np.float32)
            feeds = {"input": x}
            for kind, path in (("dynamic_int8", dyn), ("weight_only_int8", wo)):
                row[kind].update(evaluate_outputs(src, path, feeds))
        models_out[name] = row

    return build_report(models_out)


def build_report(models: dict[str, Any] | None = None) -> dict[str, Any]:
    models = models if models is not None else {k: dict(v) for k, v in FROZEN_MEASUREMENTS.items()}
    return {
        "ticket": TICKET_ID,
        "policy": {"static_int8": "forbidden_default"},
        "allowed_candidates": list(ALLOWED_CANDIDATES),
        "tooling": {
            "dynamic": "onnxruntime.quantization.quantize_dynamic (QInt8, per_channel)",
            "weight_only": "MatMulNBitsQuantizer bits=8 block_size=128 symmetric QOperator",
            "ort": "CPUExecutionProvider smoke; EP latency → later benches/latency.md",
        },
        "models": models,
        "recommendation": {
            "geotr": "either",
            "geotr_note": (
                "dynamic ~3.7× smaller (23.5 vs 86.4 MB) with max_abs≈0.004; "
                "weight-only tighter error (≈5e-4) at 37.5 MB — pick after latency.md"
            ),
            "illtr": "weight_only_int8",
            "illtr_note": "WO smaller+lower error than dynamic; still blocked by huge export graph",
            "seg": "keep_fp16_or_fp32",
            "seg_note": "WO no-op (Conv); dynamic shrinks but sigmoid max_abs≈0.97 — not default",
            "static_int8": "do_not_adopt_as_default",
            "mixed_exclude_flow_head": "candidate_if_flow_head_degrades — not measured this ticket",
        },
        "notes": {
            "illtr_graph": (
                "IllTr naive ONNX still ~123k nodes (export_prototype); quantize size OK but "
                "load time dominates — simplify IllTr forward before app integration"
            ),
            "static_risk": (
                "static INT8 calibrates activations offline; GeoTr flow regression / "
                "Transformer sensitivity → ARCHITECTURE forbids as default"
            ),
            "fp16": "weight-only FP16 already measured (sum geo+ill+seg 75.89 MB) — keep as baseline",
        },
    }


def render_md(data: dict[str, Any]) -> str:
    m = data["models"]
    lines = [
        "# ONNX quantization candidates (M1)",
        "",
        f"Ticket: `{data['ticket']}`  ",
        "Machine JSON: [quantize_candidates.json](quantize_candidates.json)",
        "",
        "## Policy",
        "",
        "- **Allowed:** weight-only INT8 (`MatMulNBits` bits=8), dynamic INT8, mixed (exclude attention/flow head — deferred).",
        "- **Forbidden as default:** static INT8 (activation calibration). Do not ship GeoTr/IllTr with static INT8 as the default path.",
        "",
        "## Tooling",
        "",
        f"- Dynamic: `{data['tooling']['dynamic']}`",
        f"- Weight-only: `{data['tooling']['weight_only']}`",
        f"- Eval: `{data['tooling']['ort']}`",
        "",
        "## Measured (CPU ORT smoke, seed=0)",
        "",
        "| Model | FP32 MB | dynamic INT8 MB | dyn max_abs | weight-only INT8 MB | WO max_abs |",
        "|---|---:|---:|---:|---:|---:|",
    ]
    for name in ("geotr", "illtr", "seg"):
        row = m[name]
        if row.get("status") != "ok":
            lines.append(f"| {name} | — | — | — | — | — |")
            continue
        d = row["dynamic_int8"]
        w = row["weight_only_int8"]
        lines.append(
            f"| {name} | {row['fp32_mb']} | {d['mb']} | {d['max_abs']:.4g} | {w['mb']} | {w['max_abs']:.4g} |"
        )
    rec = data["recommendation"]
    lines += [
        "",
        "## Recommendation",
        "",
        f"- **GeoTr:** `{rec['geotr']}` — {rec['geotr_note']}",
        f"- **IllTr:** `{rec['illtr']}` — {rec['illtr_note']}",
        f"- **Seg:** `{rec['seg']}` — {rec['seg_note']}",
        f"- **static INT8:** `{rec['static_int8']}`",
        f"- **mixed (exclude flow head):** {rec['mixed_exclude_flow_head']}",
        "",
        "## Notes",
        "",
        f"- IllTr graph: {data['notes']['illtr_graph']}",
        f"- Static risk: {data['notes']['static_risk']}",
        f"- FP16 baseline: {data['notes']['fp16']}",
        "",
        "## Re-run",
        "",
        "```bash",
        "cd engine && source .venv/bin/activate",
        "pip install -e '.[doctr,dev]'",
        "# needs FP32 ONNX from scanbooks-export-doctr",
        "scanbooks-quantize-candidates --onnx-dir /tmp/doctr-onnx --out-dir benches/onnx_quant --json-out benches/quantize_candidates.json",
        "pytest tests/test_quantize.py",
        "```",
        "",
        "Latency / CoreML / NNAPI EP rows remain for `benches/latency.md` (next M1 slice).",
        "",
    ]
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument(
        "--onnx-dir",
        default=os.environ.get("SCANBOOKS_ONNX_DIR", ""),
        help="Directory with geotr.onnx / illtr.onnx / seg.onnx",
    )
    p.add_argument("--out-dir", default="benches/onnx_quant")
    p.add_argument("--json-out", default="benches/quantize_candidates.json")
    p.add_argument("--md-out", default="benches/quantize_candidates.md")
    p.add_argument("--no-measure", action="store_true", help="Skip ORT smoke (size only)")
    p.add_argument(
        "--frozen",
        action="store_true",
        help="Write frozen bench snapshot without re-quantizing",
    )
    args = p.parse_args(argv)

    if args.frozen or not args.onnx_dir:
        data = build_report()
    else:
        data = run_candidate_matrix(args.onnx_dir, args.out_dir, measure=not args.no_measure)

    json_path = Path(args.json_out)
    md_path = Path(args.md_out)
    json_path.parent.mkdir(parents=True, exist_ok=True)
    json_path.write_text(json.dumps(data, indent=2) + "\n")
    md_path.write_text(render_md(data))
    print(json.dumps({"models": {k: v.get("status") for k, v in data["models"].items()}, "json": str(json_path)}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
