"""FingerSeg post-process pipeline (ARCHITECTURE §5.2).

``threshold → outer-CC (border-touching only) → dilate(k·workLongEdge) → Telea/NS``.

Network weights / ONNX export are tracked in ``benches/fingerseg_candidates.md``;
this module is the shared reference for engine tests and mobile CvBridge parity.
"""

from __future__ import annotations

from typing import Any

import numpy as np

from scanbooks_engine.contracts import (
    FINGER_DILATION_RATIO,
    FINGER_INPAINT_DEFAULT,
    FINGER_MASK_THRESHOLD,
    WORK_LONG_EDGE,
)
from scanbooks_engine.finger_inpaint import InpaintMethod, inpaint


def _cv() -> Any:
    import cv2

    return cv2


def outer_cc_filter(
    prob_or_binary: np.ndarray,
    threshold: float = FINGER_MASK_THRESHOLD,
) -> np.ndarray:
    """Keep connected components that touch the image border (ARCHITECTURE §1.7)."""
    cv = _cv()
    if prob_or_binary.ndim != 2:
        raise ValueError(f"expected HxW mask, got {prob_or_binary.shape}")
    binary = (prob_or_binary.astype(np.float32) > float(threshold)).astype(np.uint8) * 255
    n, labels, _stats, _ = cv.connectedComponentsWithStats(binary, connectivity=8)
    h, w = binary.shape
    out = np.zeros_like(binary)
    for i in range(1, n):
        ys, xs = np.where(labels == i)
        if ys.size == 0:
            continue
        touches = (
            np.any(ys == 0)
            or np.any(ys == h - 1)
            or np.any(xs == 0)
            or np.any(xs == w - 1)
        )
        if touches:
            out[labels == i] = 255
    return out


def dilate_finger_mask(
    mask_u8: np.ndarray,
    work_long_edge: int = WORK_LONG_EDGE,
    ratio: float = FINGER_DILATION_RATIO,
) -> np.ndarray:
    """Morphological dilate with radius ``round(ratio * workLongEdge)`` (min 1)."""
    cv = _cv()
    if not np.any(mask_u8):
        return (mask_u8 > 0).astype(np.uint8) * 255
    radius = max(1, int(round(float(ratio) * int(work_long_edge))))
    kernel = cv.getStructuringElement(cv.MORPH_ELLIPSE, (2 * radius + 1, 2 * radius + 1))
    binary = (mask_u8 > 0).astype(np.uint8) * 255
    return cv.dilate(binary, kernel, iterations=1)


def postprocess_finger_mask(
    logits_or_prob: np.ndarray,
    *,
    threshold: float = FINGER_MASK_THRESHOLD,
    work_long_edge: int = WORK_LONG_EDGE,
    dilation_ratio: float = FINGER_DILATION_RATIO,
) -> np.ndarray:
    """Threshold → outer-CC → shadow dilate. Returns uint8 0/255 mask."""
    kept = outer_cc_filter(logits_or_prob, threshold=threshold)
    return dilate_finger_mask(kept, work_long_edge=work_long_edge, ratio=dilation_ratio)


def remove_fingers(
    image_u8_hwc: np.ndarray,
    logits_or_mask: np.ndarray,
    *,
    work_long_edge: int = WORK_LONG_EDGE,
    threshold: float = FINGER_MASK_THRESHOLD,
    dilation_ratio: float = FINGER_DILATION_RATIO,
    method: InpaintMethod | None = None,
    radius: int = 3,
) -> np.ndarray:
    """Full finger stage: post-process mask then Telea/NS inpaint (RGB uint8)."""
    chosen: InpaintMethod = method if method is not None else FINGER_INPAINT_DEFAULT  # type: ignore[assignment]
    mask = postprocess_finger_mask(
        logits_or_mask,
        threshold=threshold,
        work_long_edge=work_long_edge,
        dilation_ratio=dilation_ratio,
    )
    if not np.any(mask):
        return image_u8_hwc.copy()
    return inpaint(image_u8_hwc, mask, radius=radius, method=chosen)
