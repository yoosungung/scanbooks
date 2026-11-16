"""Parity fixture layout: preprocess tensor + raw_flow/warped + raw_mask/inpainted."""

from __future__ import annotations

from pathlib import Path

import numpy as np
import pytest

from scanbooks_engine.contracts import GEO_NET_SIZE
from scanbooks_engine.parity_fixtures import (
    PARITY_CASE,
    ensure_parity_fixtures,
    load_parity,
)
from scanbooks_engine.preprocess import preprocess_geo

FIXTURES = Path(__file__).resolve().parent / "fixtures" / "parity"


@pytest.fixture(scope="module")
def fixtures_root() -> Path:
    ensure_parity_fixtures(FIXTURES)
    return FIXTURES


def test_parity_manifest_and_files(fixtures_root: Path):
    case = load_parity(fixtures_root, PARITY_CASE)
    for key in (
        "image_u8",
        "image_f32",
        "preprocess_nchw",
        "raw_flow_288",
        "flow_hw2",
        "warped_f32",
        "mask_u8",
        "inpainted_f32",
    ):
        assert key in case
    assert case["preprocess_nchw"].shape == (1, 3, GEO_NET_SIZE, GEO_NET_SIZE)
    assert case["raw_flow_288"].shape == (2, GEO_NET_SIZE, GEO_NET_SIZE)
    assert case["mask_u8"].ndim == 2


def test_preprocess_tensor_matches_image(fixtures_root: Path):
    case = load_parity(fixtures_root, PARITY_CASE)
    got = preprocess_geo(case["image_u8"])
    np.testing.assert_allclose(got, case["preprocess_nchw"], atol=1e-6)
