"""Finger mask → Telea inpaint reference (ARCHITECTURE §5.2 post-process core).

Outer-CC filter / dilation live in a later FingerSeg ticket; this module locks
``raw_mask → inpainted`` sampling for ios/android parity fixtures.
"""

from __future__ import annotations

import numpy as np


def inpaint_telea(image_u8_hwc: np.ndarray, mask_u8: np.ndarray, radius: int = 3) -> np.ndarray:
    """Inpaint where ``mask_u8 > 0`` using OpenCV Telea. Returns uint8 HWC RGB."""
    import cv2

    if image_u8_hwc.ndim != 3 or image_u8_hwc.shape[2] != 3:
        raise ValueError(f"expected HxWx3 uint8, got {image_u8_hwc.shape}")
    if mask_u8.shape[:2] != image_u8_hwc.shape[:2]:
        raise ValueError("mask spatial size must match image")
    m = (mask_u8 > 0).astype(np.uint8)
    # OpenCV inpaint works on BGR; convert for fidelity then back
    bgr = cv2.cvtColor(image_u8_hwc, cv2.COLOR_RGB2BGR)
    out_bgr = cv2.inpaint(bgr, m, float(radius), cv2.INPAINT_TELEA)
    return cv2.cvtColor(out_bgr, cv2.COLOR_BGR2RGB)
