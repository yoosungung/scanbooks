# engine/DESIGN.md

DocTr 변환·양자화·벤치·골든/파리티 테스트와 FingerSeg export를 담당한다. 컴포넌트 *간* 계약은 [ARCHITECTURE.md](../ARCHITECTURE.md).

## 책임

- `GeoTr`(+공식 seg)·`IllTr` PyTorch → ONNX → 허용된 양자화만
- FingerSeg ONNX + inpaint 레퍼런스(외곽 CC 필터 포함)
- CLI 추론, EP별 latency/용량 벤치, 골든·파리티 픽스처 발행
- `models/` 버전 메타·체크섬

앱 UI·카메라·PDF는 다루지 않는다.

## 내부 구조 (예정)

```
engine/
  DESIGN.md
  pyproject.toml
  src/scanbooks_engine/
    export_doctr.py
    preprocess.py         # RGB→NCHW /255 @ 288
    geo_warp.py           # upsample flow + grid_sample / remap
    finger_inpaint.py     # Telea/NS 레퍼런스 (raw_mask→inpainted; 파리티=Telea)
    finger_pipeline.py    # 임계값 → 외곽 CC → dilate(k·workLongEdge) → inpaint
    parity_fixtures.py    # fixtures/parity 발행·로드
    crop.py / golden.py / stage_order.py / experiment_matrix.py
    quantize.py           # weight-only INT8 / dynamic; static 기본 금지
    ill_patches.py        # (예정) 128 패치·overlap·batch ORT·stitch
  tests/
    test_preprocess.py
    test_geo_warp_parity.py
    test_finger_inpaint_parity.py
    test_finger_pipeline.py
    test_parity_fixtures.py
    test_crop_c1.py / test_stage_order_s1.py / test_export_contracts.py
    fixtures/
      golden/             # 실험 매트릭스 골든
      parity/             # 전처리 텐서 + raw_flow/warped + raw_mask/inpainted
  benches/
    latency.md            # EP×정밀도 벤치 + e2e 게이트
    fingerseg_candidates.md
    quantize_candidates.md
    experiment_matrix.md
    export_prototype.md
```

TDD: 전처리 shape → warp/inpaint 파리티 → 골든 품질 → export 붙이기.

## 파리티 픽스처 (M1)

경로: `tests/fixtures/parity/` (`manifest.json`).

| 산출물 | 파일 | 용도 |
|---|---|---|
| 전처리 텐서 | `synth_page_preprocess.npy` | `1×3×288×288` float32 |
| raw_flow | `synth_page_raw_flow.npy` | `2×288×288`, 이미 `(2*(bm/286.8)-1)*0.99` 스케일 |
| warped | `synth_page_warped.png` | `grid_sample(align_corners=True)` 기준 |
| raw_mask / inpainted | `*_raw_mask.png` / `*_inpainted.png` | Telea 기준 |

허용오차: remap vs grid_sample `max abs ≤ 3/255`; PNG 라운드트립·inpaint `≤ 1/255`. 해시 비교 금지. ios/android는 동일 픽스처로 CvBridge 단위 테스트(M2 훅: `ios/ScanbooksTests/Fixtures/parity/` · `android/app/src/test/resources/parity/`에 `.rgb`/`.gray`/`.f32` 서브셋).

## 해상도 (혼용 금지)

| 이름 | 값 | 용도 |
|---|---|---|
| `geoNetSize` | 288 | GeoTr/seg 네트워크 입력·flow 출력 |
| `workLongEdge` | 1024 (완화 768) | crop 후 작업 이미지 긴 변, flow upsample 목표 |
| `illPatchSize` | 128 | IllTr 패치 |
| `illOverlap` | 12.5% | 공식 `inference_ill.py`와 동일 |

GeoTr에 work 해상도 텐서를 넣지 않는다.

## Geo 워핑 레퍼런스

공식 DocTr과 동일:

1. crop 이미지를 288×288로 resize → (seg) → GeoTr
2. flow 스케일 `(2 * (bm / 286.8) - 1) * 0.99` (export 그래프에 넣을지 후처리로 둘지 한쪽으로 고정)
3. flow를 crop(또는 work) HxW로 upsample + 3×3 blur
4. `grid_sample(..., align_corners=True)` 동등 샘플링

모바일 `cv::remap`과의 반픽셀·border 차이는 fixtures로 허용오차를 정한다.

## Ill 패치

- 공식 pad/crop/compose를 레퍼런스로 구현
- M1 최우선: **패치 배치로 ORT 1회(또는 소수 회) 호출** 가능 여부
- overlap/stride 변경 시 경계 아티팩트를 `experiment_matrix.md`에 기록
- 3.5s 미달 시 IllTr 옵션화·경량 full-image 후보(GeoTr 유지)

## FingerSeg

- 입력: **geo 출력**(ill 전)
- 후처리: 임계값(`0.5`) → **이미지 외곽과 맞닿은 CC만 채택** → dilation 반경 = `FINGER_DILATION_RATIO * workLongEdge`(`0.004` → ~4px@1024) → Telea(기본·파리티) / NS(대안)
- 구현: `finger_pipeline.remove_fingers` · 후보·거절 사유: [benches/fingerseg_candidates.md](benches/fingerseg_candidates.md)
- 본문 인쇄 손/얼굴 오탐을 골든 세트에 넣어 회귀 (`printed_hand`)
- 최종 `fingerseg.onnx` 가중치는 미선정(후보 A 커스텀 MobileNet/U-Net 우선); stub은 벤치 전용

## 양자화·EP

- 금지(기본): static INT8(활성화 양자화)로 GeoTr flow 배포
- 후보: weight-only INT8 (`MatMulNBits` bits=8), dynamic INT8, attention/flow-head 제외 mixed(미측정)
- 측정·권고: [benches/quantize_candidates.md](benches/quantize_candidates.md) — GeoTr **`dynamic_int8`**(latency 확정), IllTr WO 우선(단 export 그래프 단순화 선행), Seg는 FP16/FP32 유지
- `latency.md` 필수 행: CPU FP32, CPU weight-INT8, CoreML MLProgram·NE(FP16 compute)/NNAPI deferred + fallback 노트 — [benches/latency.md](benches/latency.md)
- 기본 EP(Apple host): CoreML MLProgram NeuralEngine + CPU fallback; GeoTr e2e ≈0.13 s(pass). IllTr 순차 81패치 ≈11 s(fail) → export 단순화·배치 선행

## 품질 기준

- 해시 비교 금지
- `max abs diff` / SSIM, 소규모 OCR CER
- ARCHITECTURE §5.4 게이트 통과분만 app-ready

## 실험 매트릭스 (M1)

| ID | 변수 | 기록 | 결과 |
|---|---|---|---|
| C1 | BBox+margin vs Quad homography → geo | content retention | **BBox 유지** |
| S1 | `geo→finger→ill` vs `geo→ill→finger` | chroma·recall·composite | **finger→ill 유지** |
| I1 | IllTr batch vs 순차 패치 | latency·스티칭 품질 | (후속 티켓) |

상세: [benches/experiment_matrix.md](benches/experiment_matrix.md). 골든: `tests/fixtures/golden/`. 계약 기본 유지(ARCHITECTURE 미개정).

## Commands

```bash
cd engine
python3 -m venv .venv && source .venv/bin/activate
pip install -e '.[doctr,dev]'
pytest
scanbooks-experiment-matrix
scanbooks-quantize-candidates --frozen   # writes benches/quantize_candidates.{json,md}
# optional re-measure (needs DocTr checkout + weights + CPU device patches):
# DOCTR_ROOT=/path/to/DocTr scanbooks-export-doctr
# scanbooks-quantize-candidates --onnx-dir /tmp/doctr-onnx --out-dir benches/onnx_quant
# scanbooks-latency-bench --onnx-dir /tmp/doctr-onnx --quant-dir /tmp/doctr-onnx/quant
# optional regenerate parity (no-op if files already present):
# python -c "from pathlib import Path; from scanbooks_engine.parity_fixtures import ensure_parity_fixtures; ensure_parity_fixtures(Path('tests/fixtures/parity'))"
pytest tests/test_latency.py -q
```

Export: [benches/export_prototype.md](benches/export_prototype.md). 매트릭스: [benches/experiment_matrix.md](benches/experiment_matrix.md). 양자화: [benches/quantize_candidates.md](benches/quantize_candidates.md). FingerSeg: [benches/fingerseg_candidates.md](benches/fingerseg_candidates.md). Latency: [benches/latency.md](benches/latency.md). 파리티: `tests/fixtures/parity/manifest.json`.
