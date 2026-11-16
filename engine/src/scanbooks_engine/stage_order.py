"""S1 stage-order stubs — finger/ill quality proxies without heavy model weights."""

from __future__ import annotations

from typing import Any, Sequence

import numpy as np

try:
    import cv2
except ImportError:  # pragma: no cover
    cv2 = None  # type: ignore

DEFAULT_STAGE_ORDER = ("finger", "ill")
ALT_STAGE_ORDER = ("ill", "finger")


def _cv() -> Any:
    if cv2 is None:
        raise ImportError("opencv-python-headless required")
    return cv2


def mean_chroma(image: np.ndarray, mask: np.ndarray) -> float:
    """Rough skin-chroma proxy: mean |R-G|+|G-B| on masked pixels (RGB/BGR agnostic)."""
    sel = mask > 0
    if not np.any(sel):
        return 0.0
    pix = image[sel].astype(np.float32)
    return float(np.mean(np.abs(pix[:, 0] - pix[:, 1]) + np.abs(pix[:, 1] - pix[:, 2])))


def outer_cc_finger_mask(prob_or_binary: np.ndarray) -> np.ndarray:
    """Keep connected components that touch the image border (ARCHITECTURE §1.7)."""
    from scanbooks_engine.finger_pipeline import outer_cc_filter

    # Stub detector emits 0/255; threshold 0 keeps historical >0 semantics.
    return outer_cc_filter(prob_or_binary, threshold=0.0)


def detect_finger_stub(image: np.ndarray) -> np.ndarray:
    """
    Skin-ish detector for synthetics.
    Illumination wash (low chroma) reduces recall — models the ill→finger failure mode.
    """
    cv = _cv()
    img = image.astype(np.float32)
    # Warm-ish: channel2 high relative to others (matches golden skin paint).
    c0, c1, c2 = img[:, :, 0], img[:, :, 1], img[:, :, 2]
    chroma = np.abs(c0 - c1) + np.abs(c1 - c2)
    warm = (c2 > c1) & (c2 > c0) & (c2 > 120) & (chroma > 40)
    raw = (warm.astype(np.uint8) * 255)
    # Mild close to merge thumb ellipse.
    raw = cv.morphologyEx(raw, cv.MORPH_CLOSE, np.ones((3, 3), np.uint8))
    return outer_cc_finger_mask(raw)


def inpaint_finger(image: np.ndarray, finger_mask: np.ndarray) -> np.ndarray:
    cv = _cv()
    if not np.any(finger_mask):
        return image.copy()
    dil = cv.dilate(finger_mask, np.ones((5, 5), np.uint8), iterations=1)
    return cv.inpaint(image, dil, 3, cv.INPAINT_TELEA)


def ill_stub(image: np.ndarray) -> np.ndarray:
    """
    Local brightness flatten that also washes chroma (stand-in for IllTr side-effect).
    """
    cv = _cv()
    img = image.astype(np.float32)
    gray = cv.cvtColor(image, cv.COLOR_BGR2GRAY).astype(np.float32)
    blur = cv.GaussianBlur(gray, (0, 0), sigmaX=12)
    gain = (180.0 / np.maximum(blur, 1.0))[..., None]
    out = img * gain
    # Chroma compression toward gray — models ill-before-finger skin wash.
    mean = out.mean(axis=2, keepdims=True)
    out = mean + 0.35 * (out - mean)
    return np.clip(out, 0, 255).astype(np.uint8)


def run_stage_pipeline(case: dict[str, Any], order: Sequence[str]) -> dict[str, Any]:
    img = case["image"].copy()
    chroma_at_detect = 0.0
    finger_mask_used = np.zeros(img.shape[:2], dtype=np.uint8)
    for stage in order:
        if stage == "finger":
            chroma_at_detect = mean_chroma(img, case["border_finger_mask"])
            finger_mask_used = detect_finger_stub(img)
            img = inpaint_finger(img, finger_mask_used)
        elif stage == "ill":
            img = ill_stub(img)
        else:  # pragma: no cover
            raise ValueError(stage)
    return {
        "image": img,
        "finger_mask": finger_mask_used,
        "skin_chroma_at_detect": chroma_at_detect,
        "order": tuple(order),
    }


def score_stage_result(result: dict[str, Any], case: dict[str, Any]) -> dict[str, float]:
    border = case["border_finger_mask"] > 0
    printed = case["printed_hand_mask"] > 0
    removed = result["finger_mask"] > 0

    border_total = int(border.sum()) or 1
    border_recall = float(np.logical_and(border, removed).sum() / border_total)

    if np.any(printed):
        printed_total = int(printed.sum())
        printed_kept = 1.0 - float(np.logical_and(printed, removed).sum() / printed_total)
    else:
        printed_kept = 1.0

    # Halo: residual high-frequency edge along former finger after pipeline.
    cv = _cv()
    edge = cv.Canny(result["image"], 80, 160)
    dil_border = cv.dilate(case["border_finger_mask"], np.ones((7, 7), np.uint8))
    ring = (dil_border > 0) & ~border
    halo = float(edge[ring].mean() / 255.0) if np.any(ring) else 0.0

    chroma = float(result["skin_chroma_at_detect"])
    # Higher chroma + recall + printed kept, lower halo → better.
    composite = (
        0.35 * border_recall
        + 0.25 * printed_kept
        + 0.30 * min(chroma / 120.0, 1.0)
        + 0.10 * (1.0 - min(halo, 1.0))
    )
    return {
        "border_finger_recall": border_recall,
        "printed_hand_kept": printed_kept,
        "skin_chroma_at_detect": chroma,
        "halo": halo,
        "composite": composite,
    }
