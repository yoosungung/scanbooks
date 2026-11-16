"""Geo warp reference: upsample raw_flow + blur + grid_sample / remap.

``raw_flow`` is already scaled to grid_sample coords:
``(2 * (bm / 286.8) - 1) * 0.99`` (ARCHITECTURE §5.1; export graph includes scale).
Channels are (x, y) in NCHW or HxWx2 layout.
"""

from __future__ import annotations

import numpy as np


def upsample_flow(raw_hw2: np.ndarray, out_hw: tuple[int, int]) -> np.ndarray:
    """Bilinear resize each flow channel to ``out_hw`` then 3×3 blur (DocTr)."""
    import cv2

    h, w = out_hw
    if raw_hw2.ndim != 3 or raw_hw2.shape[2] != 2:
        raise ValueError(f"expected HxWx2 flow, got {raw_hw2.shape}")
    bm0 = cv2.resize(raw_hw2[..., 0], (w, h), interpolation=cv2.INTER_LINEAR)
    bm1 = cv2.resize(raw_hw2[..., 1], (w, h), interpolation=cv2.INTER_LINEAR)
    bm0 = cv2.blur(bm0, (3, 3))
    bm1 = cv2.blur(bm1, (3, 3))
    return np.stack([bm0, bm1], axis=-1).astype(np.float32)


def warp_grid_sample(image_f32_hwc: np.ndarray, flow_hw2: np.ndarray) -> np.ndarray:
    """PyTorch ``F.grid_sample(..., align_corners=True)`` reference (float HWC [0,1])."""
    import torch
    import torch.nn.functional as F

    if image_f32_hwc.ndim != 3 or image_f32_hwc.shape[2] != 3:
        raise ValueError(f"expected HxWx3, got {image_f32_hwc.shape}")
    if flow_hw2.shape[:2] != image_f32_hwc.shape[:2]:
        raise ValueError("flow spatial size must match image")
    img = torch.from_numpy(image_f32_hwc.astype(np.float32)).permute(2, 0, 1).unsqueeze(0)
    grid = torch.from_numpy(flow_hw2.astype(np.float32)).unsqueeze(0)  # 1×H×W×2
    with torch.no_grad():
        out = F.grid_sample(img, grid, mode="bilinear", padding_mode="zeros", align_corners=True)
    return out[0].permute(1, 2, 0).contiguous().numpy()


def flow_to_remap_maps(flow_hw2: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """Convert align_corners normalized flow to OpenCV remap absolute maps."""
    h, w = flow_hw2.shape[:2]
    map_x = ((flow_hw2[..., 0] + 1.0) * (w - 1) / 2.0).astype(np.float32)
    map_y = ((flow_hw2[..., 1] + 1.0) * (h - 1) / 2.0).astype(np.float32)
    return map_x, map_y


def warp_remap(image_f32_hwc: np.ndarray, flow_hw2: np.ndarray) -> np.ndarray:
    """OpenCV ``remap`` equivalent for mobile CvBridge parity checks."""
    import cv2

    map_x, map_y = flow_to_remap_maps(flow_hw2)
    # cv2 expects BGR or multi-channel; float32 HWC RGB is fine
    out = cv2.remap(
        image_f32_hwc.astype(np.float32),
        map_x,
        map_y,
        interpolation=cv2.INTER_LINEAR,
        borderMode=cv2.BORDER_CONSTANT,
        borderValue=(0, 0, 0),
    )
    return out.astype(np.float32)
