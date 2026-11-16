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
    quantize.py
    infer_cli.py
    geo_warp.py           # upsample flow + grid_sample 레퍼런스
    ill_patches.py        # 128 패치·overlap·batch ORT·stitch
    finger_pipeline.py
    preprocess.py
  tests/
    test_preprocess.py
    test_geo_warp_parity.py
    test_ill_stitch.py
    test_finger_filter.py
    test_golden_geo.py
    test_golden_ill.py
    fixtures/             # 전처리 텐서 + flow/mask 후처리 기준 PNG/bin
  benches/
    latency.md
    experiment_matrix.md  # crop·단계 순서 비교
```

TDD: 전처리 shape → warp/inpaint 파리티 → 골든 품질 → export 붙이기.

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
- 후처리: 임계값 → **이미지 외곽과 맞닿은 CC만 채택** → dilation 반경 = `k * workLongEdge` → Telea/NS
- 본문 인쇄 손/얼굴 오탐을 골든 세트에 넣어 회귀

## 양자화·EP

- 금지(기본): static INT8(활성화 양자화)로 GeoTr flow 배포
- 후보: weight-only INT8, dynamic, attention/flow-head 제외 mixed
- `latency.md` 필수 행: CPU FP32, CPU weight-INT8, CoreML/NNAPI FP16 + fallback 노트

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
# optional re-measure (needs DocTr checkout + weights + CPU device patches):
# DOCTR_ROOT=/path/to/DocTr scanbooks-export-doctr
```

Export: [benches/export_prototype.md](benches/export_prototype.md). 매트릭스: [benches/experiment_matrix.md](benches/experiment_matrix.md).
