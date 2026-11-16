"""DocTr-compatible preprocess: RGB uint8 → NCHW float32 /255 @ geoNetSize."""

from __future__ import annotations

import numpy as np

from scanbooks_engine.contracts import GEO_NET_SIZE


def preprocess_geo(rgb_u8: np.ndarray, size: int = GEO_NET_SIZE) -> np.ndarray:
    """Resize HxWx3 RGB uint8 to 1×3×size×size float32 in [0, 1].

    Matches official DocTr ``inference.py``::
        im = cv2.resize(im_ori, (288, 288)); im.transpose(2,0,1)  # already /255 on im_ori
    Here we accept uint8 and divide after resize.
    """
    import cv2

    if rgb_u8.ndim != 3 or rgb_u8.shape[2] != 3:
        raise ValueError(f"expected HxWx3, got {rgb_u8.shape}")
    resized = cv2.resize(rgb_u8, (size, size), interpolation=cv2.INTER_LINEAR)
    chw = resized.astype(np.float32).transpose(2, 0, 1) / 255.0
    return chw[None, ...]
