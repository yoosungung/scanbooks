"""Finger mask → Telea/NS inpaint reference (ARCHITECTURE §5.2).

Parity fixtures lock ``raw_mask → inpainted`` via Telea. NS is the alternate
algorithm for mobile A/B; outer-CC / dilation live in ``finger_pipeline``.
"""

from __future__ import annotations

from typing import Literal

import numpy as np

InpaintMethod = Literal["telea", "ns"]


def _inpaint(
    image_u8_hwc: np.ndarray,
    mask_u8: np.ndarray,
    radius: int,
    flags: int,
) -> np.ndarray:
    import cv2

    if image_u8_hwc.ndim != 3 or image_u8_hwc.shape[2] != 3:
        raise ValueError(f"expected HxWx3 uint8, got {image_u8_hwc.shape}")
    if mask_u8.shape[:2] != image_u8_hwc.shape[:2]:
        raise ValueError("mask spatial size must match image")
    m = (mask_u8 > 0).astype(np.uint8)
    bgr = cv2.cvtColor(image_u8_hwc, cv2.COLOR_RGB2BGR)
    out_bgr = cv2.inpaint(bgr, m, float(radius), flags)
    return cv2.cvtColor(out_bgr, cv2.COLOR_BGR2RGB)


def inpaint_telea(image_u8_hwc: np.ndarray, mask_u8: np.ndarray, radius: int = 3) -> np.ndarray:
    """Inpaint where ``mask_u8 > 0`` using OpenCV Telea. Returns uint8 HWC RGB."""
    import cv2

    return _inpaint(image_u8_hwc, mask_u8, radius, cv2.INPAINT_TELEA)


def inpaint_ns(image_u8_hwc: np.ndarray, mask_u8: np.ndarray, radius: int = 3) -> np.ndarray:
    """Inpaint where ``mask_u8 > 0`` using OpenCV Navier-Stokes. Returns uint8 HWC RGB."""
    import cv2

    return _inpaint(image_u8_hwc, mask_u8, radius, cv2.INPAINT_NS)


def inpaint(
    image_u8_hwc: np.ndarray,
    mask_u8: np.ndarray,
    radius: int = 3,
    method: InpaintMethod = "telea",
) -> np.ndarray:
    if method == "telea":
        return inpaint_telea(image_u8_hwc, mask_u8, radius=radius)
    if method == "ns":
        return inpaint_ns(image_u8_hwc, mask_u8, radius=radius)
    raise ValueError(f"unknown inpaint method: {method!r} (expected 'telea' or 'ns')")
