"""Synthetic golden-set fixtures for M1 experiment matrix (C1 / S1)."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import numpy as np

try:
    import cv2
except ImportError:  # pragma: no cover
    cv2 = None  # type: ignore

GOLDEN_IDS = ("curved_book", "thumb_occlusion", "printed_hand")

# Normalized quads (ARCHITECTURE §4.3). Slightly inset so margins/bulges exist.
_DEFAULT_QUAD = {
    "tl": {"x": 0.18, "y": 0.12},
    "tr": {"x": 0.82, "y": 0.14},
    "br": {"x": 0.84, "y": 0.88},
    "bl": {"x": 0.16, "y": 0.86},
}


def _require_cv2() -> Any:
    if cv2 is None:
        raise ImportError("opencv-python-headless required for golden fixtures")
    return cv2


def _blank(h: int = 256, w: int = 256) -> np.ndarray:
    img = np.full((h, w, 3), 245, dtype=np.uint8)
    # Page-like paper with mild vignette for ill stubs.
    yy, xx = np.mgrid[0:h, 0:w]
    vignette = (1.0 - 0.35 * ((xx / w - 0.5) ** 2 + (yy / h - 0.5) ** 2) * 4).clip(0.55, 1.0)
    img = (img.astype(np.float32) * vignette[..., None]).astype(np.uint8)
    return img


def _quad_pts(quad: dict, w: int, h: int) -> np.ndarray:
    order = ("tl", "tr", "br", "bl")
    return np.array(
        [[quad[k]["x"] * (w - 1), quad[k]["y"] * (h - 1)] for k in order],
        dtype=np.float32,
    )


def _draw_text_lines(img: np.ndarray, quad: dict) -> np.ndarray:
    cv = _require_cv2()
    out = img.copy()
    h, w = out.shape[:2]
    pts = _quad_pts(quad, w, h).astype(np.int32)
    x0, y0 = int(pts[:, 0].min()) + 8, int(pts[:, 1].min()) + 12
    x1, y1 = int(pts[:, 0].max()) - 8, int(pts[:, 1].max()) - 12
    for i, y in enumerate(range(y0, y1, 10)):
        x_end = x0 + 40 + (i % 5) * 12
        cv.line(out, (x0, y), (min(x_end, x1), y), (40, 40, 50), 1, cv.LINE_AA)
    return out


def _curved_content_mask(h: int, w: int, quad: dict) -> np.ndarray:
    """Page content that bulges outside the straight-edge quad (curvature)."""
    cv = _require_cv2()
    mask = np.zeros((h, w), dtype=np.uint8)
    pts = _quad_pts(quad, w, h)
    # Mid-edge outward bulge on the right side (simulates page curl).
    tl, tr, br, bl = pts
    mid_r = (tr + br) / 2
    # Outside straight-edge quad but inside BBox+5% margin (ARCHITECTURE §3.1).
    bulge = mid_r + np.array([0.055 * w, 0.0], dtype=np.float32)
    poly = np.vstack([tl, tr, bulge, br, bl]).astype(np.int32)
    cv.fillPoly(mask, [poly], 255)
    return mask


def _paint_finger_border(img: np.ndarray, mask: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    cv = _require_cv2()
    out = img.copy()
    h, w = out.shape[:2]
    finger = np.zeros((h, w), dtype=np.uint8)
    # Thumb from bottom edge into page (touches border).
    cv.ellipse(finger, (w // 2, h - 4), (28, 55), 0, 0, 360, 255, -1)
    skin = np.array([90, 140, 210], dtype=np.uint8)  # BGR-ish warm via RGB slot
    out[finger > 0] = skin
    mask[finger > 0] = 255
    return out, mask


def _paint_printed_hand(img: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    cv = _require_cv2()
    out = img.copy()
    h, w = out.shape[:2]
    printed = np.zeros((h, w), dtype=np.uint8)
    # Interior only — does not touch image border.
    cv.ellipse(printed, (w // 2, h // 2), (22, 30), 15, 0, 360, 255, -1)
    out[printed > 0] = np.array([70, 100, 160], dtype=np.uint8)
    return out, printed


def build_case(case_id: str, size: int = 256) -> dict[str, Any]:
    cv = _require_cv2()
    h = w = size
    quad = dict(_DEFAULT_QUAD)
    img = _draw_text_lines(_blank(h, w), quad)
    content = np.zeros((h, w), dtype=np.uint8)
    border_finger = np.zeros((h, w), dtype=np.uint8)
    printed_hand = np.zeros((h, w), dtype=np.uint8)

    if case_id == "curved_book":
        content = _curved_content_mask(h, w, quad)
        # Tint content region slightly so image reflects bulge.
        tint = img.copy()
        tint[content > 0] = (tint[content > 0].astype(np.int16) - 8).clip(0, 255).astype(np.uint8)
        img = tint
    elif case_id == "thumb_occlusion":
        content = np.zeros((h, w), dtype=np.uint8)
        cv.fillPoly(content, [_quad_pts(quad, w, h).astype(np.int32)], 255)
        img, border_finger = _paint_finger_border(img, border_finger)
    elif case_id == "printed_hand":
        content = np.zeros((h, w), dtype=np.uint8)
        cv.fillPoly(content, [_quad_pts(quad, w, h).astype(np.int32)], 255)
        img, border_finger = _paint_finger_border(img, border_finger)
        img, printed_hand = _paint_printed_hand(img)
    else:  # pragma: no cover
        raise ValueError(case_id)

    return {
        "id": case_id,
        "quad": quad,
        "image": img,
        "content_mask": content,
        "border_finger_mask": border_finger,
        "printed_hand_mask": printed_hand,
    }


def ensure_golden_fixtures(root: Path, size: int = 256) -> None:
    cv = _require_cv2()
    root.mkdir(parents=True, exist_ok=True)
    cases_meta: dict[str, Any] = {}
    for case_id in GOLDEN_IDS:
        case = build_case(case_id, size=size)
        cv.imwrite(str(root / f"{case_id}.png"), case["image"])
        cv.imwrite(str(root / f"{case_id}_content.png"), case["content_mask"])
        cv.imwrite(str(root / f"{case_id}_border_finger.png"), case["border_finger_mask"])
        cv.imwrite(str(root / f"{case_id}_printed_hand.png"), case["printed_hand_mask"])
        cases_meta[case_id] = {"quad": case["quad"], "size": [size, size]}
    manifest = {
        "version": 1,
        "description": "Synthetic M1 golden set: curved_book / thumb_occlusion / printed_hand",
        "cases": cases_meta,
        "note": "Deterministic synthetics for crop/order matrix; replace with device captures later.",
    }
    (root / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")


def load_golden(root: Path, case_id: str) -> dict[str, Any]:
    cv = _require_cv2()
    if case_id not in GOLDEN_IDS:
        raise ValueError(case_id)
    manifest = json.loads((root / "manifest.json").read_text())
    meta = manifest["cases"][case_id]
    return {
        "id": case_id,
        "quad": meta["quad"],
        "image": cv.imread(str(root / f"{case_id}.png"), cv.IMREAD_COLOR),
        "content_mask": cv.imread(str(root / f"{case_id}_content.png"), cv.IMREAD_GRAYSCALE),
        "border_finger_mask": cv.imread(
            str(root / f"{case_id}_border_finger.png"), cv.IMREAD_GRAYSCALE
        ),
        "printed_hand_mask": cv.imread(
            str(root / f"{case_id}_printed_hand.png"), cv.IMREAD_GRAYSCALE
        ),
    }
