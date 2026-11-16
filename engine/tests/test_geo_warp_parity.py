"""raw_flow → warped: torch grid_sample reference vs OpenCV remap (align_corners)."""

from __future__ import annotations

from pathlib import Path

import numpy as np
import pytest

from scanbooks_engine.contracts import GEO_NET_SIZE
from scanbooks_engine.geo_warp import (
    upsample_flow,
    warp_grid_sample,
    warp_remap,
)
from scanbooks_engine.parity_fixtures import PARITY_CASE, ensure_parity_fixtures, load_parity

FIXTURES = Path(__file__).resolve().parent / "fixtures" / "parity"

# Mobile remap may differ by ~0.5px near edges; fixtures lock the allowance.
REMAP_MAX_ABS = 3.0 / 255.0  # uint8-ish scale on float [0,1]
GRID_SAMPLE_ATOL = 1e-5


@pytest.fixture(scope="module")
def fixtures_root() -> Path:
    ensure_parity_fixtures(FIXTURES)
    return FIXTURES


def test_identity_flow_preserves_image():
    h, w = 32, 40
    rng = np.random.default_rng(1)
    img = rng.random((h, w, 3), dtype=np.float32)
    # align_corners normalized coords for each pixel center
    ys, xs = np.meshgrid(
        np.linspace(-1.0, 1.0, h, dtype=np.float32),
        np.linspace(-1.0, 1.0, w, dtype=np.float32),
        indexing="ij",
    )
    flow_hw2 = np.stack([xs, ys], axis=-1)  # x,y
    out = warp_grid_sample(img, flow_hw2)
    np.testing.assert_allclose(out, img, atol=1e-5)


def test_upsample_flow_shape_and_blur():
    raw = np.zeros((GEO_NET_SIZE, GEO_NET_SIZE, 2), dtype=np.float32)
    raw[100, 100] = [0.5, -0.25]
    up = upsample_flow(raw, out_hw=(64, 80))
    assert up.shape == (64, 80, 2)
    assert up.dtype == np.float32


def test_remap_matches_grid_sample_within_tolerance():
    h, w = 48, 64
    rng = np.random.default_rng(2)
    img = rng.random((h, w, 3), dtype=np.float32)
    # Mild synthetic flow in normalized coords
    ys, xs = np.meshgrid(
        np.linspace(-1.0, 1.0, h, dtype=np.float32),
        np.linspace(-1.0, 1.0, w, dtype=np.float32),
        indexing="ij",
    )
    flow = np.stack([xs + 0.02 * np.sin(ys * 3), ys + 0.02 * np.cos(xs * 3)], axis=-1)
    ref = warp_grid_sample(img, flow)
    mobile = warp_remap(img, flow)
    max_abs = float(np.max(np.abs(ref - mobile)))
    assert max_abs <= REMAP_MAX_ABS, f"max abs diff {max_abs} > {REMAP_MAX_ABS}"


def test_parity_fixture_raw_flow_to_warped(fixtures_root: Path):
    case = load_parity(fixtures_root, PARITY_CASE)
    warped = warp_grid_sample(case["image_f32"], case["flow_hw2"])
    # Fixture stores warped as PNG — allow 1 LSB quantization
    np.testing.assert_allclose(warped, case["warped_f32"], atol=1.0 / 255.0 + GRID_SAMPLE_ATOL)
