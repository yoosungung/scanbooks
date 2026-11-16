"""C1 — BBox+margin vs Quad homography content retention on curved golden."""

from __future__ import annotations

import json
from pathlib import Path

import pytest

from scanbooks_engine.contracts import CROP_MARGIN_RATIO, PIPELINE_DEFAULT_ORDER
from scanbooks_engine.crop import (
    bbox_margin_crop,
    bbox_margin_region_mask,
    content_retention,
    homography_crop,
    quad_polygon_mask,
    quad_to_pixel,
)
from scanbooks_engine.golden import GOLDEN_IDS, ensure_golden_fixtures, load_golden

FIXTURES = Path(__file__).resolve().parent / "fixtures" / "golden"


@pytest.fixture(scope="module", autouse=True)
def _fixtures():
    ensure_golden_fixtures(FIXTURES)


def test_crop_margin_constant_documented():
    assert 0.0 < CROP_MARGIN_RATIO <= 0.15


def test_pipeline_default_order_contract():
    assert PIPELINE_DEFAULT_ORDER == ("crop", "geo", "finger", "ill", "done")


def test_golden_manifest_lists_three_cases():
    manifest = json.loads((FIXTURES / "manifest.json").read_text())
    assert set(manifest["cases"]) == set(GOLDEN_IDS)
    for case_id in GOLDEN_IDS:
        assert (FIXTURES / f"{case_id}.png").is_file()


def test_c1_bbox_retains_more_curvature_bulge_than_homography():
    case = load_golden(FIXTURES, "curved_book")
    img = case["image"]
    content = case["content_mask"]
    h, w = img.shape[:2]
    quad_px = quad_to_pixel(case["quad"], w, h)

    bbox_region = bbox_margin_region_mask(h, w, quad_px, CROP_MARGIN_RATIO)
    homo_region = quad_polygon_mask(h, w, quad_px)

    bbox_ret = content_retention(content, bbox_region)
    homo_ret = content_retention(content, homo_region)

    bbox_img = bbox_margin_crop(img, quad_px, CROP_MARGIN_RATIO)
    homo_img = homography_crop(img, quad_px)

    assert bbox_img.size > 0 and homo_img.size > 0
    assert bbox_ret > homo_ret
    assert bbox_ret >= 0.98
    assert (bbox_ret - homo_ret) >= 0.02  # straight-edge polygon clips the bulge
