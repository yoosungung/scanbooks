# ios/DESIGN.md

iOS 네이티브 앱 내부 설계. 계약·스키마는 [ARCHITECTURE.md](../ARCHITECTURE.md). 화면·플로우는 [docs/UIUX.md](../docs/UIUX.md).

## 책임

- SwiftUI 셸, AVFoundation 카메라 프리뷰
- 실시간 문서 테두리(Vision 우선, 부족 시 Rough luminance blob)
- 로컬 `ScanSession` / `Page` 저장 (`Storage/SessionStore`)
- ONNX Runtime Mobile 연동 골격 (`PipelineRunner` + `OrtBackendFactory` + `ModelCache`; `.onnx` 없으면 passthrough)
- **flow remap** (`CvBridge.remap`, align_corners) · Telea/NS inpaint는 OpenCV 연동 시 동일 훅으로 잠금 (후속)
- PDFKit export (`Export/PdfExporter` → `export.pdf` / `exportPdfUri`)

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
    Process/               # PipelineRunner + OrtBackendFactory + PipelineStub(legacy) + stage UI
    Session/               # grid · reorder/delete · 추가 스캔 · PDF CTA · BatchScanPresentation
    Settings/              # AttributionPresentation · SettingsView (privacy + OSS + About)
    Export/                # PdfExporter (cleanedUri → PDFKit)
    CvBridge/              # remap (pure Swift) + inpaintTelea 훅(OpenCV pending)
    Models/                # AppRoute, PipelineStage, ScanSession, Page, Quad, …
  ScanbooksTests/          # + engine parity fixtures 리소스
```

## 파이프라인 러너

- 순서: `crop → geo → finger → ill → done` ([ARCHITECTURE §1.3](../ARCHITECTURE.md))
- M3: `PipelineRunner`가 BBox crop → 단계 PNG(`cropped`/`dewarped`/`finger`/`cleaned`) + `PageMetrics`(`workLongEdge`/`geoNetSize`/`ortEp`) 기록
- Process UI: 단계 리스트 + 단계 ms + `PageMetricsPresentation` 계약 라인(`workLongEdge`/`geoNet`/`EP`); store에서 stage마다 metrics 리로드
- `StageBackend` 주입: 실기기 `.onnx` 있으면 ORT 백엔드, 없으면 passthrough(선택 EP 메타 유지). 실패 시 `failed`+`failedStage`+`errorCode`; `status==failed`면 `failedStage`부터 자동 resume. Process UI: `FailedRetryPresentation` 에러 한 줄 + `이 단계부터 재시도`
- 선택 EP: `CoreML_MLProgram_NeuralEngine+CPU_fallback` (`engine/benches/latency.md`)
- `PipelineStub` / `StubProcessRunner`는 레거시·단위 테스트용 잔존
- 프리뷰: Dewarp 없음 ([ARCHITECTURE §1.4](../ARCHITECTURE.md))
- 모델 메타: [models/manifest.json](../models/manifest.json)
- M4: `ModelCache`/`ModelDeployPolicy` — IllTr=`onDemand`, geo/seg/finger=`residentPreferred`; `ensure`·checksum·`ensureResidentPreferred` → `OrtModelCatalog`. `workLongEdgeMitigated=768` 상수만(기본 1024 유지)
- M4: `AttributionPresentation` + `SettingsView` — 온디바이스 개인정보 고지·DocTr/ORT/OpenCV attribution·About 모델 버전; Camera 첫 스캔 툴팁 + ⋯ → Settings
- M4: `ReleaseChecklistGate` — 스토어 전 라이선스 게이트([docs/RELEASE_CHECKLIST.md](../docs/RELEASE_CHECKLIST.md)); 내부 pass · 상업 blocked · FingerSeg open


## 저장

- Application Support `scanbooks/sessions/{sessionId}/session.json`
- `pages/{pageId}/page.json` + `original.jpg` + mid `working.png` (capture-time lossless)
- 후속 단계: `cropped.png` / `dewarped.png` / `cleaned.png` (M2 stub는 working→cleaned 복사)
- PDF: `cleanedUri` 있는 페이지만 `sessions/{id}/export.pdf` ([ARCHITECTURE §6](../ARCHITECTURE.md))
- 단계 간 JPEG 재인코딩 금지 ([ARCHITECTURE §1.11](../ARCHITECTURE.md))
- 셔터 → `AVCapturePhotoOutput` JPEG → `appendPage` → 백그라운드 `PipelineRunner`(연속 배치, Camera 코너 `처리 중 N장`) · Session `reorderPages`/`deletePage`

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
