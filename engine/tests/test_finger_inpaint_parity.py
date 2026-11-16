"""raw_mask → inpainted: Telea reference for mobile parity."""

from __future__ import annotations

from pathlib import Path

import numpy as np
import pytest

from scanbooks_engine.finger_inpaint import inpaint_telea
from scanbooks_engine.parity_fixtures import PARITY_CASE, ensure_parity_fixtures, load_parity

FIXTURES = Path(__file__).resolve().parent / "fixtures" / "parity"
INPAINT_ATOL = 1.0 / 255.0


@pytest.fixture(scope="module")
def fixtures_root() -> Path:
    ensure_parity_fixtures(FIXTURES)
    return FIXTURES


def test_inpaint_clears_masked_region():
    img = np.full((40, 50, 3), 200, dtype=np.uint8)
    img[10:20, 15:25] = 0  # hole content (will be overwritten by inpaint)
    mask = np.zeros((40, 50), dtype=np.uint8)
    mask[10:20, 15:25] = 255
    out = inpaint_telea(img, mask, radius=3)
    assert out.shape == img.shape
    assert out.dtype == np.uint8
    # Masked pixels should no longer be pure black (filled from neighbors)
    assert int(out[15, 20].mean()) > 50


def test_parity_fixture_raw_mask_to_inpainted(fixtures_root: Path):
    case = load_parity(fixtures_root, PARITY_CASE)
    got = inpaint_telea(case["image_u8"], case["mask_u8"], radius=case["inpaint_radius"])
    np.testing.assert_allclose(
        got.astype(np.float32) / 255.0,
        case["inpainted_f32"],
        atol=INPAINT_ATOL,
    )
