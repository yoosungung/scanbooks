"""Parameter / FP16 / ONNX operator measurement helpers."""

from __future__ import annotations

from pathlib import Path
from typing import Any, Iterable

WATCH_OPS = (
    "GridSample",
    "LayerNormalization",
    "LayerNorm",
    "Reshape",
    "Resize",
    "InstanceNormalization",
    "ScatterND",
    "Cast",
    "Where",
)


def count_params(parameters: Iterable[Any]) -> int:
    return int(sum(int(p.numel()) for p in parameters))


def fp16_mb(param_count: int) -> float:
    """Weight-only FP16 footprint (bytes = params * 2)."""
    return round(param_count * 2 / (1024 * 1024), 2)


def inspect_onnx_ops(path: str | Path) -> dict[str, Any]:
    import onnx

    model = onnx.load(str(path))
    counts: dict[str, int] = {}
    for node in model.graph.node:
        counts[node.op_type] = counts.get(node.op_type, 0) + 1
    names = sorted(counts)
    return {
        "path": str(path),
        "size_mb": round(Path(path).stat().st_size / (1024 * 1024), 2),
        "nodes": len(model.graph.node),
        "all_ops": names,
        "watch": {op: counts[op] for op in WATCH_OPS if op in counts},
        "has_GridSample": "GridSample" in counts,
        "has_LayerNormalization": "LayerNormalization" in counts or "LayerNorm" in counts,
        "has_Reshape": "Reshape" in counts,
    }


def summarize_compatibility(op_report: dict[str, Any]) -> dict[str, str]:
    """Human notes for ORT Mobile planning (not a runtime probe)."""
    notes: dict[str, str] = {}
    if op_report.get("has_GridSample"):
        notes["GridSample"] = "in-graph — verify EP; may force CPU fallback"
    else:
        notes["GridSample"] = "absent from network ONNX — keep warp as OpenCV remap post-process"
    if op_report.get("has_LayerNormalization"):
        notes["LayerNormalization"] = "present — CoreML/NNAPI may Cast/decompose; profile fallback"
    else:
        notes["LayerNormalization"] = "absent"
    if op_report.get("has_Reshape"):
        notes["Reshape"] = (
            f"present (n={op_report.get('watch', {}).get('Reshape', '?')}) — "
            "prefer static shapes; watch dynamic reshape on mobile EP"
        )
    else:
        notes["Reshape"] = "absent"
    return notes
