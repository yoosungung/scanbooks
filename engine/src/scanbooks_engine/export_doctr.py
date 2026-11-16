"""Export DocTr GeoTr / IllTr / U2NETP to ONNX and print measurement JSON.

Requires:
  DOCTR_ROOT  — checkout of fh2019ustc/DocTr with CPU-safe device patches
                (replace `.cuda()` in GeoTr/position_encoding with tensor.device)
  weights under DOCTR_ROOT/model_pretrained/{geotr,illtr,seg}.pth
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

from scanbooks_engine.contracts import FLOW_SCALE_DIV, FLOW_SCALE_MUL, MEASURED
from scanbooks_engine.measure import count_params, fp16_mb, inspect_onnx_ops, summarize_compatibility


def _patch_sys_path(doctr_root: Path) -> None:
    root = str(doctr_root.resolve())
    if root not in sys.path:
        sys.path.insert(0, root)


def _reload_model(model, path: Path, strip: int):
    import torch

    model_dict = model.state_dict()
    pretrained = torch.load(path, map_location="cpu", weights_only=False)
    pretrained = {k[strip:]: v for k, v in pretrained.items() if k[strip:] in model_dict}
    if len(pretrained) != len(model_dict):
        raise RuntimeError(f"{path}: matched {len(pretrained)}/{len(model_dict)} keys")
    model_dict.update(pretrained)
    model.load_state_dict(model_dict)
    return model


def export(doctr_root: Path, out_dir: Path, opset: int = 17) -> dict:
    import torch
    import torch.nn as nn

    _patch_sys_path(doctr_root)
    from GeoTr import GeoTr  # type: ignore
    from IllTr import IllTr  # type: ignore
    from seg import U2NETP  # type: ignore

    weights = doctr_root / "model_pretrained"
    geo = _reload_model(GeoTr(num_attn_layers=6).eval(), weights / "geotr.pth", 7)
    ill = _reload_model(IllTr().eval(), weights / "illtr.pth", 7)
    seg = _reload_model(U2NETP(3, 1).eval(), weights / "seg.pth", 6)

    class GeoWrap(nn.Module):
        def __init__(self, m):
            super().__init__()
            self.m = m

        def forward(self, x):
            bm = self.m(x)
            return (2 * (bm / FLOW_SCALE_DIV) - 1) * FLOW_SCALE_MUL

    class SegWrap(nn.Module):
        def __init__(self, m):
            super().__init__()
            self.m = m

        def forward(self, x):
            return self.m(x)[0]

    out_dir.mkdir(parents=True, exist_ok=True)
    geo_in = torch.randn(1, 3, 288, 288)
    ill_in = torch.randn(1, 3, 128, 128)
    seg_in = torch.randn(1, 3, 288, 288)

    with torch.no_grad():
        geo_out = geo(geo_in)
        ill_out = ill(ill_in)
        seg_out = seg(seg_in)[0]

    torch.onnx.export(
        GeoWrap(geo).eval(),
        geo_in,
        str(out_dir / "geotr.onnx"),
        input_names=["input"],
        output_names=["bm"],
        opset_version=opset,
        dynamo=False,
    )
    torch.onnx.export(
        ill.eval(),
        ill_in,
        str(out_dir / "illtr.onnx"),
        input_names=["input"],
        output_names=["out"],
        opset_version=opset,
        dynamo=False,
    )
    torch.onnx.export(
        SegWrap(seg).eval(),
        seg_in,
        str(out_dir / "seg.onnx"),
        input_names=["input"],
        output_names=["mask"],
        opset_version=opset,
        dynamo=False,
    )

    stats = {
        "geotr": {
            "params": count_params(geo.parameters()),
            "fp16_mb": fp16_mb(count_params(geo.parameters())),
            "in": list(geo_in.shape),
            "out": list(geo_out.shape),
            "dtype": str(geo_out.dtype),
        },
        "illtr": {
            "params": count_params(ill.parameters()),
            "fp16_mb": fp16_mb(count_params(ill.parameters())),
            "in": list(ill_in.shape),
            "out": list(ill_out.shape),
            "dtype": str(ill_out.dtype),
        },
        "seg": {
            "params": count_params(seg.parameters()),
            "fp16_mb": fp16_mb(count_params(seg.parameters())),
            "in": list(seg_in.shape),
            "out": list(seg_out.shape),
            "dtype": str(seg_out.dtype),
        },
    }
    stats["sum_fp16_mb_geo_ill_seg"] = round(
        stats["geotr"]["fp16_mb"] + stats["illtr"]["fp16_mb"] + stats["seg"]["fp16_mb"], 2
    )
    stats["sum_fp16_mb_geo_ill"] = round(stats["geotr"]["fp16_mb"] + stats["illtr"]["fp16_mb"], 2)

    onnx_report = {name: inspect_onnx_ops(out_dir / f"{name}.onnx") for name in ("geotr", "illtr", "seg")}
    for name, rep in onnx_report.items():
        rep["compat_notes"] = summarize_compatibility(rep)

    # Sanity vs frozen MEASURED (checkpoint drift)
    for name in ("geotr", "illtr", "seg"):
        if stats[name]["params"] != MEASURED[name]["params"]:
            raise RuntimeError(
                f"{name} params {stats[name]['params']} != measured {MEASURED[name]['params']}"
            )

    return {"stats": stats, "onnx": onnx_report}


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument(
        "--doctr-root",
        default=os.environ.get("DOCTR_ROOT", ""),
        help="DocTr checkout (or set DOCTR_ROOT)",
    )
    p.add_argument("--out-dir", default="benches/onnx")
    p.add_argument("--json-out", default="benches/export_prototype.json")
    args = p.parse_args(argv)
    if not args.doctr_root:
        print("DOCTR_ROOT / --doctr-root required", file=sys.stderr)
        return 2
    report = export(Path(args.doctr_root), Path(args.out_dir))
    Path(args.json_out).parent.mkdir(parents=True, exist_ok=True)
    Path(args.json_out).write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report["stats"], indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
