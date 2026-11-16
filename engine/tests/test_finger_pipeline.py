"""FingerSeg post-process: threshold → outer-CC → dilation → Telea/NS."""

from __future__ import annotations

from pathlib import Path

import numpy as np
import pytest

from scanbooks_engine.contracts import (
    FINGER_DILATION_RATIO,
    FINGER_MASK_THRESHOLD,
    WORK_LONG_EDGE,
)
from scanbooks_engine.finger_inpaint import inpaint_ns, inpaint_telea
from scanbooks_engine.finger_pipeline import (
    dilate_finger_mask,
    outer_cc_filter,
    postprocess_finger_mask,
    remove_fingers,
)
from scanbooks_engine.parity_fixtures import PARITY_CASE, ensure_parity_fixtures, load_parity

FIXTURES = Path(__file__).resolve().parent / "fixtures" / "parity"
INPAINT_ATOL = 1.0 / 255.0


@pytest.fixture(scope="module")
def fixtures_root() -> Path:
    ensure_parity_fixtures(FIXTURES)
    return FIXTURES


def _blob_touching_border(h: int, w: int) -> np.ndarray:
    m = np.zeros((h, w), dtype=np.float32)
    m[:8, :12] = 0.9  # touches top + left
    return m


def _interior_blob(h: int, w: int) -> np.ndarray:
    m = np.zeros((h, w), dtype=np.float32)
    m[h // 2 - 4 : h // 2 + 4, w // 2 - 4 : w // 2 + 4] = 0.95
    return m


def test_outer_cc_keeps_border_drops_interior():
    h, w = 48, 64
    raw = _blob_touching_border(h, w) + _interior_blob(h, w)
    out = outer_cc_filter(raw, threshold=FINGER_MASK_THRESHOLD)
    assert out.dtype == np.uint8
    assert out.max() == 255
    assert out[2, 2] == 255  # border blob kept
    assert out[h // 2, w // 2] == 0  # interior dropped


def test_outer_cc_empty_when_nothing_touches_border():
    h, w = 40, 40
    out = outer_cc_filter(_interior_blob(h, w), threshold=0.5)
    assert not np.any(out)


def test_dilate_radius_scales_with_work_long_edge():
    mask = np.zeros((32, 32), dtype=np.uint8)
    mask[10:14, 10:14] = 255
    small = dilate_finger_mask(mask, work_long_edge=100, ratio=0.02)  # r=2
    large = dilate_finger_mask(mask, work_long_edge=500, ratio=0.02)  # r=10
    assert int(large.sum()) > int(small.sum())


def test_dilate_default_ratio_at_work_long_edge():
    # Contract: radius = round(ratio * workLongEdge); at 1024 default ≥ 1
    r = max(1, int(round(FINGER_DILATION_RATIO * WORK_LONG_EDGE)))
    assert r >= 1
    mask = np.zeros((16, 16), dtype=np.uint8)
    mask[7, 7] = 255
    out = dilate_finger_mask(mask, work_long_edge=WORK_LONG_EDGE)
    assert np.any(out)
    assert int(out.sum()) > 255  # grew beyond single pixel


def test_postprocess_composes_threshold_outer_cc_dilate():
    h, w = 48, 64
    logits = _blob_touching_border(h, w) + _interior_blob(h, w)
    # Sub-threshold noise on border should vanish
    logits[0, w - 1] = FINGER_MASK_THRESHOLD - 0.1
    out = postprocess_finger_mask(logits, work_long_edge=256)
    assert out[2, 2] == 255
    assert out[h // 2, w // 2] == 0
    assert out[0, w - 1] == 0


def test_remove_fingers_telea_and_ns_fill_hole():
    img = np.full((40, 50, 3), 200, dtype=np.uint8)
    img[10:20, 0:8] = 0
    raw = np.zeros((40, 50), dtype=np.float32)
    raw[10:20, 0:8] = 1.0  # touches left border
    telea = remove_fingers(img, raw, work_long_edge=128, method="telea", radius=3)
    ns = remove_fingers(img, raw, work_long_edge=128, method="ns", radius=3)
    assert telea.shape == img.shape and ns.shape == img.shape
    assert int(telea[15, 3].mean()) > 50
    assert int(ns[15, 3].mean()) > 50


def test_remove_fingers_preserves_interior_printed_hand():
    img = np.full((48, 64, 3), 180, dtype=np.uint8)
    img[20:28, 28:36] = (40, 40, 200)  # printed hand color
    raw = _interior_blob(48, 64)
    out = remove_fingers(img, raw, work_long_edge=128, method="telea")
    np.testing.assert_array_equal(out[20:28, 28:36], img[20:28, 28:36])


def test_parity_telea_path_unchanged(fixtures_root: Path):
    case = load_parity(fixtures_root, PARITY_CASE)
    # Direct Telea reference (no CC/dilate) must still match fixture
    got = inpaint_telea(case["image_u8"], case["mask_u8"], radius=case["inpaint_radius"])
    np.testing.assert_allclose(
        got.astype(np.float32) / 255.0,
        case["inpainted_f32"],
        atol=INPAINT_ATOL,
    )
    # NS is available and produces same shape (not a parity lock)
    ns = inpaint_ns(case["image_u8"], case["mask_u8"], radius=case["inpaint_radius"])
    assert ns.shape == case["image_u8"].shape


def test_unknown_inpaint_method_raises():
    img = np.zeros((8, 8, 3), dtype=np.uint8)
    mask = np.zeros((8, 8), dtype=np.float32)
    mask[0:2, 0:2] = 1.0  # border-touching so inpaint path runs
    with pytest.raises(ValueError, match="method"):
        remove_fingers(img, mask, method="bogus")  # type: ignore[arg-type]
