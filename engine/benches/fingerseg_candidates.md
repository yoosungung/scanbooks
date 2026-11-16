# FingerSeg candidates + post-process (M1)

Ticket: `2b22debc-12fa-4af6-b459-4d4a8a6e94b8`  
Machine JSON: [fingerseg_candidates.json](fingerseg_candidates.json)

## Post-process contract (implemented)

Reference: `scanbooks_engine.finger_pipeline` + `finger_inpaint`.

| Step | Spec | Constant |
|---|---|---|
| Threshold | logits/prob → binary | `FINGER_MASK_THRESHOLD = 0.5` |
| Outer-CC | keep CCs that touch image border only | ARCHITECTURE §1.7 / §5.2 |
| Dilate | ellipse radius `round(k · workLongEdge)` | `FINGER_DILATION_RATIO = 0.004` (~4 px @ 1024) |
| Inpaint | Telea (default / parity) or NS | `FINGER_INPAINT_DEFAULT = telea` |

Parity fixtures (`tests/fixtures/parity/*_inpainted.png`) remain **Telea-only** on the raw mask (no CC/dilate in that lock). Pipeline CC+dilate is covered by unit tests on synthetics + golden `printed_hand` / `thumb_occlusion`.

## Network candidates (weights not shipped this ticket)

Final ONNX artifact name stays `fingerseg.onnx` (ARCHITECTURE §5.2). **Final weights still open** (ROADMAP 라이선스 표).

| ID | Candidate | Fit | License / risk | Verdict |
|---|---|---|---|---|
| A | Custom MobileNetV3-small / U-Net binary mask @ work res, trained on book-page + border-finger labels | Matches geo→finger input; outer-CC still required | Need own dataset + retrain; preferred long-term | **primary target** |
| B | DeepLabV3+ MobileNetV2 fine-tune (binary finger) | Mobile-proven seg backbone | Training cost; watch FP16 size vs 80 MB budget | **backup** |
| C | Academic FingerSeg (VISIGRAPP 2024, DDRNet-style finger-class HGR) | Wrong task (13 finger classes on hand crops, not page occlusion) | Paper code TBD; domain mismatch | **reject for MVP** |
| D | Generic saliency (U²-Net / rembg) | Easy ONNX | Salient “subject” ≠ border finger; high interior FP | **reject** |
| E | MediaPipe Hands landmarks alone | Fast | Forbidden as sole mask (ARCHITECTURE §1.7) | **reject as primary** |
| Stub | Skin-chroma heuristic + outer-CC (`stage_order.detect_finger_stub`) | Benches / S1 only | Not production quality | **interim until A/B weights** |

## Recommendation

1. Ship **post-process now** (this ticket): threshold → outer-CC → dilate → Telea (NS alternate).
2. Train/export candidate **A** next (or B if A underfits); gate on golden border recall + printed-hand keep + resident MB.
3. Do **not** adopt C/D/E as `fingerseg.onnx`.
4. Revisit ROADMAP “FingerSeg 최종 가중치” license row when A/B checkpoint exists.

## Reproduce

```bash
cd engine && source .venv/bin/activate
pytest tests/test_finger_pipeline.py tests/test_finger_inpaint_parity.py tests/test_stage_order_s1.py -q
```
