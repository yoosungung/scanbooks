"""S1 — geo→finger→ill vs geo→ill→finger quality proxies on golden set."""

from __future__ import annotations

from pathlib import Path

import pytest

from scanbooks_engine.golden import ensure_golden_fixtures, load_golden
from scanbooks_engine.stage_order import (
    DEFAULT_STAGE_ORDER,
    ALT_STAGE_ORDER,
    run_stage_pipeline,
    score_stage_result,
)

FIXTURES = Path(__file__).resolve().parent / "fixtures" / "golden"


@pytest.fixture(scope="module", autouse=True)
def _fixtures():
    ensure_golden_fixtures(FIXTURES)


def test_default_stage_order_is_finger_before_ill():
    assert DEFAULT_STAGE_ORDER == ("finger", "ill")
    assert ALT_STAGE_ORDER == ("ill", "finger")


@pytest.mark.parametrize("case_id", ["thumb_occlusion", "printed_hand"])
def test_s1_default_order_beats_alt_on_composite(case_id: str):
    case = load_golden(FIXTURES, case_id)
    default = run_stage_pipeline(case, DEFAULT_STAGE_ORDER)
    alt = run_stage_pipeline(case, ALT_STAGE_ORDER)

    d = score_stage_result(default, case)
    a = score_stage_result(alt, case)

    # Border finger should be removed in both paths when CC filter works.
    assert d["border_finger_recall"] >= 0.85
    assert a["border_finger_recall"] >= 0.85

    # Printed hand inside page must not be wiped (outer-CC only).
    if case_id == "printed_hand":
        assert d["printed_hand_kept"] >= 0.90
        assert a["printed_hand_kept"] >= 0.90

    # Finger-before-ill preserves skin chroma at detect time and reduces halo.
    assert d["skin_chroma_at_detect"] > a["skin_chroma_at_detect"]
    assert d["composite"] > a["composite"]
