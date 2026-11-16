"""ROI crop helpers — BBox+margin (default) vs Quad homography (C1 compare)."""

from __future__ import annotations

from typing import Any

import numpy as np

try:
    import cv2
except ImportError:  # pragma: no cover
    cv2 = None  # type: ignore


def _cv() -> Any:
    if cv2 is None:
        raise ImportError("opencv-python-headless required")
    return cv2


def quad_to_pixel(quad: dict, width: int, height: int) -> np.ndarray:
    """Normalized §4.3 quad → float32 pixel corners tl,tr,br,bl."""
    order = ("tl", "tr", "br", "bl")
    return np.array(
        [[quad[k]["x"] * (width - 1), quad[k]["y"] * (height - 1)] for k in order],
        dtype=np.float32,
    )


def quad_polygon_mask(h: int, w: int, quad_px: np.ndarray) -> np.ndarray:
    cv = _cv()
    mask = np.zeros((h, w), dtype=np.uint8)
    cv.fillPoly(mask, [quad_px.astype(np.int32)], 255)
    return mask


def bbox_margin_region_mask(
    h: int, w: int, quad_px: np.ndarray, margin_ratio: float
) -> np.ndarray:
    x0, y0, x1, y1 = _bbox_xyxy(quad_px, h, w, margin_ratio)
    mask = np.zeros((h, w), dtype=np.uint8)
    mask[y0:y1, x0:x1] = 255
    return mask


def _bbox_xyxy(
    quad_px: np.ndarray, h: int, w: int, margin_ratio: float
) -> tuple[int, int, int, int]:
    xs = quad_px[:, 0]
    ys = quad_px[:, 1]
    pad = margin_ratio * float(max(h, w))
    x0 = int(np.floor(xs.min() - pad))
    y0 = int(np.floor(ys.min() - pad))
    x1 = int(np.ceil(xs.max() + pad))
    y1 = int(np.ceil(ys.max() + pad))
    x0 = max(0, x0)
    y0 = max(0, y0)
    x1 = min(w, x1)
    y1 = min(h, y1)
    return x0, y0, x1, y1


def bbox_margin_crop(image: np.ndarray, quad_px: np.ndarray, margin_ratio: float) -> np.ndarray:
    h, w = image.shape[:2]
    x0, y0, x1, y1 = _bbox_xyxy(quad_px, h, w, margin_ratio)
    return image[y0:y1, x0:x1].copy()


def homography_crop(image: np.ndarray, quad_px: np.ndarray) -> np.ndarray:
    """Perspective-warp the quad interior to an axis-aligned rectangle."""
    cv = _cv()
    width = int(max(np.linalg.norm(quad_px[1] - quad_px[0]), np.linalg.norm(quad_px[2] - quad_px[3])))
    height = int(max(np.linalg.norm(quad_px[3] - quad_px[0]), np.linalg.norm(quad_px[2] - quad_px[1])))
    width = max(width, 1)
    height = max(height, 1)
    dst = np.array(
        [[0, 0], [width - 1, 0], [width - 1, height - 1], [0, height - 1]],
        dtype=np.float32,
    )
    m = cv.getPerspectiveTransform(quad_px.astype(np.float32), dst)
    return cv.warpPerspective(image, m, (width, height))


def content_retention(content_mask: np.ndarray, region_mask: np.ndarray) -> float:
    content = content_mask > 0
    total = int(content.sum())
    if total == 0:
        return 1.0
    kept = int(np.logical_and(content, region_mask > 0).sum())
    return kept / total
