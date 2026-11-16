# ios/DESIGN.md

iOS 네이티브 앱 내부 설계. 계약·스키마는 [ARCHITECTURE.md](../ARCHITECTURE.md). 화면·플로우는 [docs/UIUX.md](../docs/UIUX.md).

## 책임

- SwiftUI 셸, AVFoundation 카메라 프리뷰
- 실시간 문서 테두리(Vision 우선, 부족 시 OpenCV)
- 로컬 `ScanSession` / `Page` 저장
- ONNX Runtime Mobile로 네트워크 추론
- OpenCV(또는 동등)로 **flow remap**·**Telea/NS inpaint** (engine 파리티 픽스처 준수)
- PDFKit export

모델 학습·ONNX 변환은 `engine/` 몫이다.

## 내부 구조 (예정)

```
ios/
  DESIGN.md
  Scanbooks/
    App/
    Camera/           # preview, capture, quad overlay → BBox+margin
    Pipeline/         # crop → geo → finger → ill
    Ort/              # EP 선택, 세션 재사용
    CvBridge/         # remap, inpaint, morph, CC filter
    Models/
    Storage/
    Export/
    UI/
  ScanbooksTests/     # 파리티 픽스처 소비
```

## 파이프라인 러너

- 순서: `crop → geo → finger → ill → done` ([ARCHITECTURE §1.3](../ARCHITECTURE.md))
- `geo`: ORT(288 입력) → flow upsample → `align_corners=True` 동등 remap → `dewarped.png`
- `finger`: ORT 마스크 → 외곽 CC 필터 → inpaint
- `ill`: 128 패치 배치 ORT → stitch → `cleaned.png`
- 실패 시 `failedStage`부터 재시도

## 저장

- `sessions/{sessionId}/`
- `original.jpg` (원본만 JPEG 허용)
- `cropped.png`, `dewarped.png`, `cleaned.png` (중간·최종 무손실)
- 단계 간 JPEG 재인코딩 금지

## 파리티

- `engine/tests/fixtures/`의 flow/mask 픽스처로 `CvBridge` 단위 테스트
- 전처리 상수·좌표 규약은 engine 스펙만 사용

## UX

공통 스펙은 [docs/UIUX.md](../docs/UIUX.md). 이 문서는 네이티브 모듈·저장·CV 링크만 다룬다.

## Commands

Xcode 프로젝트 생성 전이라 명령을 적지 않는다. M2에서 빌드·테스트 커맨드를 추가한다.
