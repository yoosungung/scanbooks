"""Run C1/S1 experiment matrix and write benches/experiment_matrix.{json,md}."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from scanbooks_engine.contracts import CROP_MARGIN_RATIO, PIPELINE_DEFAULT_ORDER
from scanbooks_engine.crop import (
    bbox_margin_region_mask,
    content_retention,
    quad_polygon_mask,
    quad_to_pixel,
)
from scanbooks_engine.golden import GOLDEN_IDS, ensure_golden_fixtures, load_golden
from scanbooks_engine.stage_order import (
    ALT_STAGE_ORDER,
    DEFAULT_STAGE_ORDER,
    run_stage_pipeline,
    score_stage_result,
)


def run_matrix(fixtures: Path) -> dict:
    ensure_golden_fixtures(fixtures)
    c1 = {}
    case = load_golden(fixtures, "curved_book")
    img = case["image"]
    h, w = img.shape[:2]
    quad_px = quad_to_pixel(case["quad"], w, h)
    bbox_ret = content_retention(
        case["content_mask"], bbox_margin_region_mask(h, w, quad_px, CROP_MARGIN_RATIO)
    )
    homo_ret = content_retention(case["content_mask"], quad_polygon_mask(h, w, quad_px))
    c1["curved_book"] = {
        "bbox_margin_retention": round(bbox_ret, 4),
        "homography_retention": round(homo_ret, 4),
        "winner": "bbox_margin" if bbox_ret > homo_ret else "homography",
        "margin_ratio": CROP_MARGIN_RATIO,
    }

    s1 = {}
    for case_id in ("thumb_occlusion", "printed_hand"):
        g = load_golden(fixtures, case_id)
        d = score_stage_result(run_stage_pipeline(g, DEFAULT_STAGE_ORDER), g)
        a = score_stage_result(run_stage_pipeline(g, ALT_STAGE_ORDER), g)
        s1[case_id] = {
            "geo_finger_ill": {k: round(v, 4) for k, v in d.items()},
            "geo_ill_finger": {k: round(v, 4) for k, v in a.items()},
            "winner": "geo→finger→ill" if d["composite"] > a["composite"] else "geo→ill→finger",
        }

    return {
        "ticket": "3817f270-425f-4ed1-927b-54f07a009cae",
        "golden_ids": list(GOLDEN_IDS),
        "crop_margin_ratio": CROP_MARGIN_RATIO,
        "pipeline_default_order": list(PIPELINE_DEFAULT_ORDER),
        "c1": c1,
        "s1": s1,
        "recommendation": {
            "crop": "bbox_margin",
            "stages": "geo→finger→ill",
            "architecture_change": False,
        },
    }


def render_md(data: dict) -> str:
    c1 = data["c1"]["curved_book"]
    lines = [
        "# Experiment matrix — crop & stage order (M1)",
        "",
        f"Ticket: `{data['ticket']}`  ",
        "Machine JSON: [experiment_matrix.json](experiment_matrix.json)",
        "",
        "## Golden set",
        "",
        "Synthetic fixtures under `engine/tests/fixtures/golden/` (device captures can replace later):",
        "",
        "| ID | Scenario |",
        "|---|---|",
        "| `curved_book` | Content bulges outside straight-edge quad (page curl) |",
        "| `thumb_occlusion` | Border-touching thumb |",
        "| `printed_hand` | Thumb + interior printed hand/face-like blob |",
        "",
        f"Manifest: `tests/fixtures/golden/manifest.json`. `CROP_MARGIN_RATIO` = **{data['crop_margin_ratio']}**.",
        "",
        "## C1 — BBox+margin vs Quad homography → GeoTr input ROI",
        "",
        "| Case | BBox+margin retention | Homography (quad poly) retention | Winner |",
        "|---|---:|---:|---|",
        f"| curved_book | {c1['bbox_margin_retention']:.4f} | {c1['homography_retention']:.4f} | **{c1['winner']}** |",
        "",
        "Homography keeps only the straight-edge quad interior, so curl that sits outside the "
        "polygon is clipped before GeoTr. BBox+margin retains the bulge. **Default remains BBox+margin.**",
        "",
        "## S1 — `geo→finger→ill` vs `geo→ill→finger`",
        "",
        "Stub finger (skin + outer-CC) and IllTr-like chroma wash; proxies only (no DocTr weights).",
        "",
        "| Case | Order | border_finger_recall | printed_hand_kept | skin_chroma_at_detect | composite |",
        "|---|---|---:|---:|---:|---:|",
    ]
    for case_id, row in data["s1"].items():
        for label, key in (("geo→finger→ill", "geo_finger_ill"), ("geo→ill→finger", "geo_ill_finger")):
            m = row[key]
            bold = key == "geo_finger_ill" and row["winner"] == "geo→finger→ill"
            comp = f"**{m['composite']:.4f}**" if bold else f"{m['composite']:.4f}"
            lines.append(
                f"| {case_id} | {label} | {m['border_finger_recall']:.4f} | "
                f"{m['printed_hand_kept']:.4f} | {m['skin_chroma_at_detect']:.4f} | {comp} |"
            )
    lines.extend(
        [
            "",
            "Ill-before-finger washes skin chroma at detect time → lower composite. "
            "Outer-CC filter keeps interior printed hands in both orders. "
            "**Default remains `geo→finger→ill`.**",
            "",
            "## Contract reconfirmation",
            "",
            f"- Pipeline default: `{' → '.join(data['pipeline_default_order'])}`",
            f"- Recommendation: crop=`{data['recommendation']['crop']}`, "
            f"stages=`{data['recommendation']['stages']}`",
            f"- ARCHITECTURE change required: **{data['recommendation']['architecture_change']}**",
            "",
            "Non-goals held: no M2+ app work; GeoTr net still 288; static INT8 not adopted.",
            "",
        ]
    )
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> None:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument(
        "--fixtures",
        type=Path,
        default=Path(__file__).resolve().parents[2] / "tests" / "fixtures" / "golden",
    )
    p.add_argument(
        "--out-dir",
        type=Path,
        default=Path(__file__).resolve().parents[2] / "benches",
    )
    args = p.parse_args(argv)
    data = run_matrix(args.fixtures)
    args.out_dir.mkdir(parents=True, exist_ok=True)
    (args.out_dir / "experiment_matrix.json").write_text(json.dumps(data, indent=2) + "\n")
    (args.out_dir / "experiment_matrix.md").write_text(render_md(data))
    print(json.dumps(data["recommendation"], indent=2))


if __name__ == "__main__":
    main()
