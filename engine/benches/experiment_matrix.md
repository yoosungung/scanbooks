# Experiment matrix — crop & stage order (M1)

Ticket: `3817f270-425f-4ed1-927b-54f07a009cae`  
Machine JSON: [experiment_matrix.json](experiment_matrix.json)

## Golden set

Synthetic fixtures under `engine/tests/fixtures/golden/` (device captures can replace later):

| ID | Scenario |
|---|---|
| `curved_book` | Content bulges outside straight-edge quad (page curl) |
| `thumb_occlusion` | Border-touching thumb |
| `printed_hand` | Thumb + interior printed hand/face-like blob |

Manifest: `tests/fixtures/golden/manifest.json`. `CROP_MARGIN_RATIO` = **0.05**.

## C1 — BBox+margin vs Quad homography → GeoTr input ROI

| Case | BBox+margin retention | Homography (quad poly) retention | Winner |
|---|---:|---:|---|
| curved_book | 1.0000 | 0.9621 | **bbox_margin** |

Homography keeps only the straight-edge quad interior, so curl that sits outside the polygon is clipped before GeoTr. BBox+margin retains the bulge. **Default remains BBox+margin.**

## S1 — `geo→finger→ill` vs `geo→ill→finger`

Stub finger (skin + outer-CC) and IllTr-like chroma wash; proxies only (no DocTr weights).

| Case | Order | border_finger_recall | printed_hand_kept | skin_chroma_at_detect | composite |
|---|---|---:|---:|---:|---:|
| thumb_occlusion | geo→finger→ill | 1.0000 | 1.0000 | 120.0000 | **1.0000** |
| thumb_occlusion | geo→ill→finger | 0.9643 | 1.0000 | 46.0793 | 0.8007 |
| printed_hand | geo→finger→ill | 1.0000 | 1.0000 | 120.0000 | **1.0000** |
| printed_hand | geo→ill→finger | 0.9643 | 1.0000 | 46.0793 | 0.8007 |

Ill-before-finger washes skin chroma at detect time → lower composite. Outer-CC filter keeps interior printed hands in both orders. **Default remains `geo→finger→ill`.**

## Contract reconfirmation

- Pipeline default: `crop → geo → finger → ill → done`
- Recommendation: crop=`bbox_margin`, stages=`geo→finger→ill`
- ARCHITECTURE change required: **False**

Non-goals held: no M2+ app work; GeoTr net still 288; static INT8 not adopted.
