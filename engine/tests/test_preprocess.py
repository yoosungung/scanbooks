"""Preprocess: RGB HxWx3 → NCHW float /255 @ geoNetSize (DocTr inference)."""

from __future__ import annotations

import numpy as np

from scanbooks_engine.contracts import GEO_NET_SIZE
from scanbooks_engine.preprocess import preprocess_geo


def test_preprocess_geo_shape_and_scale():
    rgb = np.zeros((64, 80, 3), dtype=np.uint8)
    rgb[10, 20] = [255, 128, 0]
    out = preprocess_geo(rgb)
    assert out.shape == (1, 3, GEO_NET_SIZE, GEO_NET_SIZE)
    assert out.dtype == np.float32
    assert 0.0 <= float(out.min()) <= float(out.max()) <= 1.0


def test_preprocess_matches_doctr_resize_hwc_to_nchw():
    """Official: cv2.resize(im_ori, (288,288)).transpose(2,0,1) /255."""
    import cv2

    rng = np.random.default_rng(0)
    rgb = rng.integers(0, 256, size=(120, 200, 3), dtype=np.uint8)
    expected = cv2.resize(rgb, (GEO_NET_SIZE, GEO_NET_SIZE)).astype(np.float32) / 255.0
    expected = expected.transpose(2, 0, 1)[None, ...]
    got = preprocess_geo(rgb)
    np.testing.assert_allclose(got, expected, rtol=0, atol=1e-6)
