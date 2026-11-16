# ios/DESIGN.md

iOS 네이티브 앱 내부 설계. 계약·스키마는 [ARCHITECTURE.md](../ARCHITECTURE.md). 화면·플로우는 [docs/UIUX.md](../docs/UIUX.md).

## 책임

- SwiftUI 셸, AVFoundation 카메라 프리뷰
- 실시간 문서 테두리(Vision 우선, 부족 시 Rough luminance blob)
- 로컬 `ScanSession` / `Page` 저장 (`Storage/SessionStore`)
- ONNX Runtime Mobile로 네트워크 추론 (후속)
- **flow remap** (`CvBridge.remap`, align_corners) · Telea/NS inpaint는 OpenCV 연동 시 동일 훅으로 잠금 (M3)
- PDFKit export (후속; Session에 CTA stub)

모델 학습·ONNX 변환은 `engine/` 몫이다.

## 내부 구조

```
ios/
  DESIGN.md
  project.yml              # XcodeGen
  Scanbooks/
    App/                   # ScanbooksApp, RootView
    Camera/                # preview, Vision+rough quad, photo shutter
    Storage/               # SessionStore — sessions/{id}/pages/{pageId}/
    Process/               # PipelineStub + StubProcessRunner + stage list UI
    Session/               # grid + PDF CTA stub
    CvBridge/              # remap (pure Swift) + inpaintTelea 훅(OpenCV pending)
    Models/                # AppRoute, PipelineStage, ScanSession, Page, Quad, …
  ScanbooksTests/          # + engine parity fixtures 리소스
```

## 파이프라인 러너

- 순서: `crop → geo → finger → ill → done` ([ARCHITECTURE §1.3](../ARCHITECTURE.md))
- M2: `PipelineStub`가 단계 UI + `Page.status` 디스크 반영 (ORT/OpenCV 없음)
- `StubProcessRunner`는 순수 UI/단위 테스트용 잔존
- 프리뷰: Dewarp 없음 ([ARCHITECTURE §1.4](../ARCHITECTURE.md))

## 저장

- Application Support `scanbooks/sessions/{sessionId}/session.json`
- `pages/{pageId}/page.json` + `original.jpg` + mid `working.png` (capture-time lossless)
- 후속 단계: `cropped.png` / `dewarped.png` / `cleaned.png`
- 단계 간 JPEG 재인코딩 금지 ([ARCHITECTURE §1.11](../ARCHITECTURE.md))
- 셔터 → `AVCapturePhotoOutput` JPEG → `appendPage` → stub Process 화면

## 파리티

- `CvBridge.remap` vs warped fixture (`remap_max_abs ≤ 3/255`, align_corners, INTER_LINEAR + BORDER_CONSTANT(0))
- `CvBridge.inpaintTelea` 시그니처·픽스처 훅; Telea 본체는 OpenCV(M3), 동일 테스트가 `inpaint_atol ≤ 1/255`로 잠금
- 픽스처: `ScanbooksTests/Fixtures/parity/` (`.rgb`/`.gray`/`.f32` + manifest; engine parity에서 발행)
- 단위 테스트: `ParityRemapInpaintHookTests`

## UX

공통 스펙은 [docs/UIUX.md](../docs/UIUX.md).

## Commands

```bash
cd ios
xcodegen generate
xcodebuild test -scheme Scanbooks -destination 'platform=iOS Simulator,name=iPhone 17' -only-testing:ScanbooksTests
```

시뮬레이터 이름이 환경마다 다르면 `xcrun simctl list devices available`으로 destination을 맞춘다.
