# android/DESIGN.md

Android 네이티브 앱 내부 설계. 계약·스키마는 [ARCHITECTURE.md](../ARCHITECTURE.md). 화면·플로우는 [docs/UIUX.md](../docs/UIUX.md).

## 책임

- Jetpack Compose UI, CameraX 프리뷰
- 실시간 문서 테두리(Rough luminance blob + 안정화; ML Kit 후속 가능)
- 로컬 `ScanSession` / `Page` 저장 (`storage/SessionStore`)
- ONNX Runtime Mobile 연동 골격 (`PipelineRunner` + `OrtBackendFactory`; `.onnx` 없으면 passthrough)
- **flow remap** (`CvBridge.remap`, align_corners) · Telea/NS inpaint는 OpenCV 연동 시 동일 훅으로 잠금 (후속)
- `PdfDocument` export (`export/PdfExporter` ← `cleanedUri`; Session CTA)

모델 학습·ONNX 변환은 `engine/` 몫이다.

## 내부 구조

```
android/
  DESIGN.md
  settings.gradle.kts / gradle wrapper
  app/
    src/main/java/.../scanbooks/
      camera/          # RoughQuadDetector, EdgeStabilityTracker, YuvToGray, CameraCaptureHolder
      storage/         # SessionStore — sessions/{id}/pages/{pageId}/
      model/           # AppRoute, PipelineStage, ScanSession, Page, Quad, …
      process/         # PipelineRunner + OrtBackendFactory + PipelineStub(legacy)
      export/          # PdfExporter (cleanedUri → PdfDocument)
      cvbridge/        # remap (pure Kotlin) + inpaintTelea 훅(OpenCV pending)
      ui/camera|process|session/
      MainActivity.kt
    src/test/          # + engine parity fixtures 리소스
```

## 파이프라인 러너

- 순서: `crop → geo → finger → ill → done`
- M3: `PipelineRunner`가 BBox crop → 단계 PNG + `PageMetrics`(`workLongEdge`/`geoNetSize`/`ortEp`) 기록
- `StageBackend` 주입: `.onnx` 있으면 ORT 백엔드, 없으면 passthrough. 실패 시 `failed`+`failedStage`+`errorCode`, `startFrom` 재시도
- 선택 EP: `CPUExecutionProvider` (NNAPI deferred — `engine/benches/latency.md`)
- `PipelineStub` / `StubProcessRunner`는 레거시·단위 테스트용 잔존
- 프리뷰: Dewarp 없음
- 모델 메타: [models/manifest.json](../models/manifest.json)

## 저장

- `context.filesDir/sessions/{sessionId}/session.json`
- `pages/{pageId}/page.json` + `original.jpg` + mid `working.png` (capture-time lossless)
- 후속 단계: `cropped.png` / `dewarped.png` / `cleaned.png` (M2 stub는 working→cleaned 복사)
- PDF: `cleanedUri` 있는 페이지만 `sessions/{id}/export.pdf` ([ARCHITECTURE §6](../ARCHITECTURE.md))
- 스키마 필드명·단계 enum은 iOS·ARCHITECTURE와 동일
- 셔터 → CameraX `ImageCapture` JPEG → `appendPage` → stub Process 화면

## 파리티

- `CvBridge.remap` vs warped fixture (`remap_max_abs ≤ 3/255`, align_corners, INTER_LINEAR + BORDER_CONSTANT(0))
- `CvBridge.inpaintTelea` 시그니처·픽스처 훅; Telea 본체는 OpenCV(M3), 동일 테스트가 `inpaint_atol ≤ 1/255`로 잠금
- 픽스처: `app/src/test/resources/parity/` (`.rgb`/`.gray`/`.f32` + manifest; engine parity에서 발행)
- 단위 테스트: `ParityRemapInpaintHookTest`

## UX

공통 스펙은 [docs/UIUX.md](../docs/UIUX.md). CAMERA 권한 거부 시 설정 CTA만(빈 프리뷰 루프 금지).

## Commands

```bash
cd android
./gradlew test
./gradlew :app:assembleDebug
```
