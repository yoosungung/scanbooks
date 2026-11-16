"""EP latency benches for DocTr ONNX (M1) → benches/latency.{json,md}.

Required rows (ARCHITECTURE §5.4): CPU×FP32, CPU×weight-INT8, CoreML|NNAPI×FP16.
NNAPI is Android-only — on macOS hosts record CoreML FP16 + NNAPI deferred note.
"""

from __future__ import annotations

import argparse
import json
import os
import time
from pathlib import Path
from typing import Any

import numpy as np

from scanbooks_engine.contracts import (
    ILL_OVERLAP_PX,
    ILL_PATCH_SIZE,
    MEASURED,
    WORK_LONG_EDGE,
)
from scanbooks_engine.measure import inspect_onnx_ops, summarize_compatibility

TICKET_ID = "3ad0ee61-1d56-4846-9b4b-411aa1e5a301"

REQUIRED_EP_ROWS = ("cpu_fp32", "cpu_weight_int8", "coreml_fp16", "nnapi_fp16")

GEOTR_E2E_BUDGET_S = 2.5
ILLTR_E2E_BUDGET_S = 3.5

# Frozen snapshot filled after first successful measure on this host.
# Re-measure: scanbooks-latency-bench --onnx-dir /tmp/doctr-onnx --quant-dir …
FROZEN_ROWS: dict[str, Any] = {}
FROZEN_META: dict[str, Any] = {}


def estimate_ill_patch_count(height: int, width: int, patch: int = ILL_PATCH_SIZE, overlap: int = ILL_OVERLAP_PX) -> int:
    """Official-style grid: stride = patch - overlap."""
    stride = patch - overlap
    if height < patch or width < patch:
        return 1
    ny = 1 + (height - patch + stride - 1) // stride
    nx = 1 + (width - patch + stride - 1) // stride
    return int(ny * nx)


def pick_geotr_quant(
    *,
    dyn_ms: float,
    wo_ms: float,
    dyn_max_abs: float,
    wo_max_abs: float,
    dyn_mb: float,
    wo_mb: float,
    max_abs_cap: float = 0.01,
) -> str:
    """Prefer faster candidate when both stay under max_abs_cap; else tighter error."""
    dyn_ok = dyn_max_abs <= max_abs_cap
    wo_ok = wo_max_abs <= max_abs_cap
    if dyn_ok and wo_ok:
        if dyn_ms < wo_ms * 0.95:
            return "dynamic_int8"
        if wo_ms < dyn_ms * 0.95:
            return "weight_only_int8"
        # latency tie → smaller footprint
        return "dynamic_int8" if dyn_mb <= wo_mb else "weight_only_int8"
    if dyn_ok:
        return "dynamic_int8"
    if wo_ok:
        return "weight_only_int8"
    return "fp32"


def to_fp16_model(src: str | Path, dst: str | Path) -> Path:
    """Cast floating initializers to FLOAT16 (smoke helper for tiny graphs).

    DocTr GeoTr/IllTr graphs fail naive weight-cast (Conv float/float16 mismatch) and
    ``onnxconverter_common.float16`` hits Cast type errors. Ship path for CoreML×FP16
    is MLProgram + Neural Engine compute on FP32 ONNX (see ``coreml_fp16_providers``).
    """
    import onnx
    from onnx import numpy_helper

    model = onnx.load(str(src))
    new_inits = []
    for init in model.graph.initializer:
        arr = numpy_helper.to_array(init)
        if arr.dtype == np.float32:
            new_inits.append(numpy_helper.from_array(arr.astype(np.float16), name=init.name))
        else:
            new_inits.append(init)
    del model.graph.initializer[:]
    model.graph.initializer.extend(new_inits)
    out = Path(dst)
    out.parent.mkdir(parents=True, exist_ok=True)
    onnx.save(model, str(out))
    return out


def coreml_fp16_providers() -> list[str | tuple[str, dict[str, str]]]:
    """CoreML EP with MLProgram — Neural Engine runs FP16 compute; CPU fallback allowed."""
    return [
        (
            "CoreMLExecutionProvider",
            {
                "ModelFormat": "MLProgram",
                "MLComputeUnits": "CPUAndNeuralEngine",
                "SpecializationStrategy": "FastPrediction",
            },
        ),
        "CPUExecutionProvider",
    ]


def _bench_json_path() -> Path:
    return Path(__file__).resolve().parents[2] / "benches" / "latency.json"


def measure_inference_ms(
    model_path: str | Path,
    feeds: dict[str, np.ndarray],
    *,
    providers: list[Any],
    warmup: int = 1,
    repeats: int = 3,
) -> dict[str, Any]:
    import onnxruntime as ort

    path = Path(model_path)
    req = [p[0] if isinstance(p, tuple) else p for p in providers]
    if not path.exists():
        return {"status": "skipped_missing", "ms": None, "providers_requested": req}
    try:
        sess = ort.InferenceSession(str(path), providers=providers)
    except Exception as exc:  # noqa: BLE001 — bench must record EP absence
        return {
            "status": "error",
            "ms": None,
            "error": f"{type(exc).__name__}: {exc}",
            "providers_requested": req,
        }
    used = list(sess.get_providers())
    # Align feed key to first input name if needed
    in_name = sess.get_inputs()[0].name
    if in_name not in feeds and len(feeds) == 1:
        feeds = {in_name: next(iter(feeds.values()))}
    for _ in range(warmup):
        sess.run(None, feeds)
    t0 = time.perf_counter()
    for _ in range(repeats):
        sess.run(None, feeds)
    ms = (time.perf_counter() - t0) / repeats * 1000.0
    return {
        "status": "ok",
        "ms": round(ms, 2),
        "providers_requested": req,
        "providers_used": used,
        "fallback_to_cpu": "CPUExecutionProvider" in used and used[0] != "CPUExecutionProvider" and len(used) > 1,
    }


def _feeds_for(model: str, seed: int = 0) -> dict[str, np.ndarray]:
    rng = np.random.RandomState(seed)
    shape = MEASURED[model]["in_nchw"]
    return {"input": rng.randn(*shape).astype(np.float32)}


def _ensure_weight_int8(fp32: Path, quant_dir: Path) -> Path:
    from scanbooks_engine.quantize import quantize_weight_only_int8

    out = quant_dir / f"{fp32.stem}_weight_only_int8.onnx"
    if out.exists() and out.stat().st_mtime >= fp32.stat().st_mtime:
        return out
    return quantize_weight_only_int8(fp32, out)


def _ensure_dynamic_int8(fp32: Path, quant_dir: Path) -> Path:
    from scanbooks_engine.quantize import quantize_dynamic_int8

    out = quant_dir / f"{fp32.stem}_dynamic_int8.onnx"
    if out.exists() and out.stat().st_mtime >= fp32.stat().st_mtime:
        return out
    return quantize_dynamic_int8(fp32, out)


def measure_postprocess_ms(*, work: int = WORK_LONG_EDGE, seed: int = 0, repeats: int = 3) -> dict[str, float]:
    """Cheap OpenCV stand-in for flow upsample + remap @ workLongEdge (not in ONNX)."""
    import cv2

    rng = np.random.RandomState(seed)
    img = (rng.rand(work, work, 3) * 255).astype(np.uint8)
    flow = rng.randn(2, 288, 288).astype(np.float32)
    # upsample flow
    t0 = time.perf_counter()
    for _ in range(repeats):
        fx = cv2.resize(flow[0], (work, work), interpolation=cv2.INTER_LINEAR)
        fy = cv2.resize(flow[1], (work, work), interpolation=cv2.INTER_LINEAR)
        fx = cv2.blur(fx, (3, 3))
        fy = cv2.blur(fy, (3, 3))
        map_x = ((fx + 1) * 0.5 * (work - 1)).astype(np.float32)
        map_y = ((fy + 1) * 0.5 * (work - 1)).astype(np.float32)
        cv2.remap(img, map_x, map_y, interpolation=cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE)
    ms = (time.perf_counter() - t0) / repeats * 1000.0
    return {"upsample_remap_ms": round(ms, 2)}


def run_latency_matrix(
    onnx_dir: str | Path,
    *,
    quant_dir: str | Path | None = None,
    models: tuple[str, ...] = ("geotr", "illtr", "seg"),
    warmup: int = 1,
    repeats: int = 3,
    seed: int = 0,
    coreml_models: tuple[str, ...] = ("geotr", "seg"),
) -> dict[str, Any]:
    """Measure EP rows. IllTr CoreML compile of ~123k nodes is skipped by default (timeout risk)."""
    onnx_dir = Path(onnx_dir)
    quant_dir = Path(quant_dir or onnx_dir / "quant")
    quant_dir.mkdir(parents=True, exist_ok=True)

    import onnxruntime as ort

    available = list(ort.get_available_providers())
    rows: dict[str, Any] = {
        "cpu_fp32": {},
        "cpu_weight_int8": {},
        "cpu_dynamic_int8": {},
        "coreml_fp16": {},
        "nnapi_fp16": {},
    }
    op_notes: dict[str, Any] = {}

    for name in models:
        fp32 = onnx_dir / f"{name}.onnx"
        feeds = _feeds_for(name, seed=seed)
        if fp32.exists():
            op_notes[name] = summarize_compatibility(inspect_onnx_ops(fp32))

        rows["cpu_fp32"][name] = measure_inference_ms(
            fp32, feeds, providers=["CPUExecutionProvider"], warmup=warmup, repeats=repeats
        )

        if fp32.exists():
            wo = _ensure_weight_int8(fp32, quant_dir)
            rows["cpu_weight_int8"][name] = measure_inference_ms(
                wo, feeds, providers=["CPUExecutionProvider"], warmup=warmup, repeats=repeats
            )
            dyn = _ensure_dynamic_int8(fp32, quant_dir)
            rows["cpu_dynamic_int8"][name] = measure_inference_ms(
                dyn, feeds, providers=["CPUExecutionProvider"], warmup=warmup, repeats=repeats
            )
            if "CoreMLExecutionProvider" in available and name in coreml_models:
                row = measure_inference_ms(
                    fp32,
                    feeds,
                    providers=list(coreml_fp16_providers()),
                    warmup=warmup,
                    repeats=repeats,
                )
                row["note"] = (
                    "MLProgram + CPUAndNeuralEngine on FP32 ONNX "
                    "(graph float16 convert fails Cast/Conv types; NE compute ≈ FP16)"
                )
                rows["coreml_fp16"][name] = row
            elif "CoreMLExecutionProvider" not in available:
                rows["coreml_fp16"][name] = {
                    "status": "skipped_ep_unavailable",
                    "ms": None,
                    "providers_requested": ["CoreMLExecutionProvider"],
                }
            else:
                rows["coreml_fp16"][name] = {
                    "status": "skipped_illtr_compile_risk",
                    "ms": None,
                    "note": "IllTr ~123k nodes — CoreML MLProgram compile deferred; simplify export first",
                }
        else:
            for key in ("cpu_weight_int8", "cpu_dynamic_int8", "coreml_fp16"):
                rows[key][name] = {"status": "skipped_missing", "ms": None}

        rows["nnapi_fp16"][name] = {
            "status": "deferred_android_only",
            "ms": None,
            "note": "NNAPI EP requires Android ORT Mobile — not available on this macOS host",
        }

    post = measure_postprocess_ms(work=WORK_LONG_EDGE, seed=seed, repeats=repeats)
    return {
        "rows": rows,
        "available_providers": available,
        "op_notes": op_notes,
        "postprocess": post,
        "ill_patch_count_1024": estimate_ill_patch_count(WORK_LONG_EDGE, WORK_LONG_EDGE),
        "host": os.uname().sysname + " " + os.uname().machine,
        "coreml_path": "MLProgram+NeuralEngine on FP32 weights (FP16 compute)",
    }


def _ms(row: dict[str, Any] | None) -> float | None:
    if not row or row.get("status") != "ok":
        return None
    return float(row["ms"])


def _e2e_estimates(rows: dict[str, Any], post_ms: float, ill_patches: int) -> dict[str, Any]:
    """Page e2e ≈ geo net + upsample/remap; Ill ≈ single-patch × patch count (batch TBD)."""
    out: dict[str, Any] = {}
    for ep in ("cpu_fp32", "cpu_weight_int8", "cpu_dynamic_int8", "coreml_fp16"):
        g = _ms(rows.get(ep, {}).get("geotr"))
        i = _ms(rows.get(ep, {}).get("illtr"))
        if g is None:
            continue
        geo_e2e_s = (g + post_ms) / 1000.0
        ill_e2e_s = (i * ill_patches / 1000.0) if i is not None else None
        out[ep] = {
            "geotr_e2e_s": round(geo_e2e_s, 3),
            "geotr_gate_pass": geo_e2e_s <= GEOTR_E2E_BUDGET_S,
            "illtr_e2e_s_est": round(ill_e2e_s, 3) if ill_e2e_s is not None else None,
            "illtr_gate_pass": (ill_e2e_s <= ILLTR_E2E_BUDGET_S) if ill_e2e_s is not None else None,
            "illtr_note": (
                f"sequential {ill_patches} patches × single-patch ms "
                "(batch>1 not measured — IllTr ~123k-node graph load risk)"
            ),
        }
    return out


def build_report(measured: dict[str, Any] | None = None) -> dict[str, Any]:
    if measured is None:
        if FROZEN_ROWS:
            rows = FROZEN_ROWS
            meta = FROZEN_META
        else:
            path = _bench_json_path()
            if not path.exists():
                raise RuntimeError(
                    "No FROZEN_ROWS / benches/latency.json — run "
                    "scanbooks-latency-bench --onnx-dir … once"
                )
            existing = json.loads(path.read_text())
            return existing
    else:
        rows = measured["rows"]
        meta = {
            "available_providers": measured.get("available_providers"),
            "op_notes": measured.get("op_notes"),
            "postprocess": measured.get("postprocess"),
            "ill_patch_count_1024": measured.get("ill_patch_count_1024"),
            "host": measured.get("host"),
            "coreml_path": measured.get("coreml_path"),
        }

    post_ms = float(meta.get("postprocess", {}).get("upsample_remap_ms") or 0.0)
    ill_patches = int(meta.get("ill_patch_count_1024") or estimate_ill_patch_count(WORK_LONG_EDGE, WORK_LONG_EDGE))
    e2e = _e2e_estimates(rows, post_ms, ill_patches)

    # Quant pick from CPU dyn vs WO (error from prior quantize ticket)
    dyn_ms = _ms(rows.get("cpu_dynamic_int8", {}).get("geotr")) or 1e9
    wo_ms = _ms(rows.get("cpu_weight_int8", {}).get("geotr")) or 1e9
    geotr_quant = pick_geotr_quant(
        dyn_ms=dyn_ms,
        wo_ms=wo_ms,
        dyn_max_abs=0.00374,
        wo_max_abs=0.000494,
        dyn_mb=23.49,
        wo_mb=37.45,
    )

    cpu_fp16_note = (
        "CPU×FP16 not a required ship path; ARCHITECTURE: if CPU FP16 slower than CPU FP32, "
        "do not adopt that combo as default."
    )
    coreml = rows.get("coreml_fp16", {}).get("geotr", {})
    fallback_notes = {
        "LayerNormalization": meta.get("op_notes", {}).get("geotr", {}).get(
            "LayerNormalization", "present — CoreML may Cast/decompose"
        ),
        "Reshape": meta.get("op_notes", {}).get("geotr", {}).get("Reshape", "present"),
        "GridSample": "absent from network ONNX — warp is OpenCV remap post-process (measured separately)",
        "coreml_partition": (
            f"providers_used={coreml.get('providers_used')}; "
            f"fallback_to_cpu={coreml.get('fallback_to_cpu')}"
            if coreml
            else "n/a"
        ),
        "nnapi": "deferred_android_only — measure on device with ORT Mobile NNAPI EP",
        "cpu_fp16_policy": cpu_fp16_note,
    }

    # Default EP recommendation
    cpu_g = _ms(rows.get("cpu_fp32", {}).get("geotr"))
    core_g = _ms(rows.get("coreml_fp16", {}).get("geotr"))
    if core_g is not None and cpu_g is not None and core_g < cpu_g * 0.95:
        default_ep = "CoreML_MLProgram_NeuralEngine+CPU_fallback"
        fallback_notes["coreml_vs_cpu"] = (
            f"CoreML NE geotr {core_g} ms < CPU FP32 {cpu_g} ms — prefer CoreML MLProgram on Apple hosts"
        )
    else:
        default_ep = "CPUExecutionProvider"
        if core_g is not None and cpu_g is not None:
            fallback_notes["coreml_vs_cpu"] = (
                f"CoreML geotr {core_g} ms ≥ CPU FP32 {cpu_g} ms — do not adopt CoreML as default on this host"
            )
        elif core_g is None:
            fallback_notes["coreml_vs_cpu"] = "CoreML row missing/error — CPU default"

    return {
        "ticket": TICKET_ID,
        "gates": {
            "geotr_e2e_s_budget": GEOTR_E2E_BUDGET_S,
            "illtr_e2e_s_budget": ILLTR_E2E_BUDGET_S,
            "work_long_edge": WORK_LONG_EDGE,
            "geo_net_size": 288,
            "ill_patch_size": ILL_PATCH_SIZE,
        },
        "rows": rows,
        "e2e": e2e,
        "meta": meta,
        "fallback_notes": fallback_notes,
        "recommendation": {
            "default_ep": default_ep,
            "geotr_quant": geotr_quant,
            "geotr_quant_note": (
                f"CPU dyn {dyn_ms} ms / WO {wo_ms} ms; errors from quantize_candidates "
                "(dyn max_abs≈0.0037, WO≈5e-4)"
            ),
            "illtr": "blocked_on_export_simplify — latency dominated by ~123k-node graph; WO still preferred size",
            "seg": "keep_fp16_or_fp32 — not on critical path for EP pick",
        },
    }


def render_md(data: dict[str, Any]) -> str:
    rows = data["rows"]
    lines = [
        "# EP latency bench (M1)",
        "",
        f"Ticket: `{data['ticket']}`  ",
        "Machine JSON: [latency.json](latency.json)",
        "",
        f"Host: `{data.get('meta', {}).get('host', '?')}`  ",
        f"ORT providers: `{data.get('meta', {}).get('available_providers', [])}`",
        "",
        "## Gates (ARCHITECTURE §5.4)",
        "",
        f"- GeoTr e2e ≤ **{data['gates']['geotr_e2e_s_budget']} s** / page @ net 288 + workLongEdge {data['gates']['work_long_edge']}",
        f"- IllTr e2e ≤ **{data['gates']['illtr_e2e_s_budget']} s** / page @ 128 patches + stitch",
        "",
        "## Required EP rows (net inference ms, median of timed repeats)",
        "",
        "| Model | CPU FP32 ms | CPU weight-INT8 ms | CPU dynamic-INT8 ms | CoreML FP16 ms | NNAPI FP16 |",
        "|---|---:|---:|---:|---:|---|",
    ]

    def cell(ep: str, model: str) -> str:
        r = rows.get(ep, {}).get(model) or {}
        if r.get("status") == "ok":
            return str(r["ms"])
        return r.get("status") or "—"

    for name in ("geotr", "illtr", "seg"):
        lines.append(
            f"| {name} | {cell('cpu_fp32', name)} | {cell('cpu_weight_int8', name)} | "
            f"{cell('cpu_dynamic_int8', name)} | {cell('coreml_fp16', name)} | {cell('nnapi_fp16', name)} |"
        )

    post = data.get("meta", {}).get("postprocess", {})
    lines += [
        "",
        "## Post-process (not in ONNX)",
        "",
        f"- flow upsample + blur + `cv2.remap` @ {data['gates']['work_long_edge']}: "
        f"**{post.get('upsample_remap_ms', '?')} ms**",
        f"- IllTr patch grid @ 1024×1024: **{data.get('meta', {}).get('ill_patch_count_1024', 81)}** patches "
        f"(stride {ILL_PATCH_SIZE - ILL_OVERLAP_PX})",
        "",
        "## End-to-end estimates",
        "",
        "| EP | GeoTr e2e s (net+remap) | gate ≤2.5 | IllTr e2e s (seq patches) | gate ≤3.5 |",
        "|---|---:|:---:|---:|:---:|",
    ]
    for ep, est in data.get("e2e", {}).items():
        ill = est.get("illtr_e2e_s_est")
        ill_s = "—" if ill is None else str(ill)
        gpass = "pass" if est.get("geotr_gate_pass") else "fail"
        ip = est.get("illtr_gate_pass")
        ipass = "pass" if ip is True else ("fail" if ip is False else "n/a")
        lines.append(f"| {ep} | {est.get('geotr_e2e_s')} | {gpass} | {ill_s} | {ipass} |")

    rec = data["recommendation"]
    fb = data["fallback_notes"]
    lines += [
        "",
        "## Recommendation",
        "",
        f"- **Default EP (this host):** `{rec['default_ep']}`",
        f"- **GeoTr quant:** `{rec['geotr_quant']}` — {rec['geotr_quant_note']}",
        f"- **IllTr:** {rec['illtr']}",
        f"- **Seg:** {rec['seg']}",
        "",
        "## Fallback / Cast notes",
        "",
        f"- LayerNormalization: {fb.get('LayerNormalization')}",
        f"- Reshape: {fb.get('Reshape')}",
        f"- GridSample: {fb.get('GridSample')}",
        f"- CoreML partition: {fb.get('coreml_partition')}",
        f"- NNAPI: {fb.get('nnapi')}",
        f"- CPU FP16 policy: {fb.get('cpu_fp16_policy')}",
    ]
    if fb.get("coreml_vs_cpu"):
        lines.append(f"- CoreML vs CPU: {fb['coreml_vs_cpu']}")
    lines += [
        "",
        "## Re-run",
        "",
        "```bash",
        "cd engine && source .venv/bin/activate",
        "pip install -e '.[doctr,dev]'",
        "scanbooks-latency-bench --onnx-dir /tmp/doctr-onnx --quant-dir /tmp/doctr-onnx/quant",
        "pytest tests/test_latency.py -q",
        "```",
        "",
    ]
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--onnx-dir", default=os.environ.get("SCANBOOKS_ONNX_DIR", ""))
    p.add_argument("--quant-dir", default="")
    p.add_argument("--json-out", default="benches/latency.json")
    p.add_argument("--md-out", default="benches/latency.md")
    p.add_argument("--warmup", type=int, default=1)
    p.add_argument("--repeats", type=int, default=3)
    p.add_argument(
        "--coreml-illtr",
        action="store_true",
        help="Also compile IllTr on CoreML (slow / may hang on ~123k-node graph)",
    )
    p.add_argument(
        "--frozen",
        action="store_true",
        help="Rewrite md/json from existing latency.json (no ORT measure)",
    )
    args = p.parse_args(argv)

    json_path = Path(args.json_out)
    md_path = Path(args.md_out)

    if args.frozen:
        if not json_path.exists():
            raise SystemExit(f"--frozen requires existing {json_path}")
        data = json.loads(json_path.read_text())
        # allow regenerating md only
        md_path.write_text(render_md(data))
        print(json.dumps({"frozen": True, "json": str(json_path)}, indent=2))
        return 0

    if not args.onnx_dir:
        raise SystemExit("Provide --onnx-dir (geotr.onnx / illtr.onnx / seg.onnx) or use --frozen")

    coreml_models: tuple[str, ...] = ("geotr", "illtr", "seg") if args.coreml_illtr else ("geotr", "seg")
    measured = run_latency_matrix(
        args.onnx_dir,
        quant_dir=args.quant_dir or None,
        warmup=args.warmup,
        repeats=args.repeats,
        coreml_models=coreml_models,
    )
    data = build_report(measured)
    json_path.parent.mkdir(parents=True, exist_ok=True)
    json_path.write_text(json.dumps(data, indent=2) + "\n")
    md_path.write_text(render_md(data))
    print(
        json.dumps(
            {
                "recommendation": data["recommendation"],
                "e2e": data["e2e"],
                "json": str(json_path),
            },
            indent=2,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
