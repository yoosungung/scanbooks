# android/DESIGN.md

Android 네이티브 앱 내부 설계. 계약·스키마는 [ARCHITECTURE.md](../ARCHITECTURE.md). 화면·플로우는 [docs/UIUX.md](../docs/UIUX.md).

## 책임

- Jetpack Compose UI, CameraX 프리뷰
- 실시간 문서 테두리(ML Kit 또는 OpenCV)
- 로컬 `ScanSession` / `Page` 저장
- ONNX Runtime Mobile로 네트워크 추론
- OpenCV로 **flow remap**·**Telea/NS inpaint** (engine 파리티 픽스처 준수)
- `PdfDocument` export

모델 학습·ONNX 변환은 `engine/` 몫이다.

## 내부 구조

```
android/
  DESIGN.md
  settings.gradle.kts
  app/
    src/main/java/.../scanbooks/
      model/           # AppRoute, PipelineStage
      process/         # StubProcessRunner
      ui/camera|process|session/
      MainActivity.kt
    src/test/          # stage/route/stub unit tests
```

M2 first slice: UI shell only. CameraX / 실테두리 / filesDir 세션 / PdfDocument bytes = 후속 슬라이스.

## 파이프라인 러너

- 순서: `crop → geo → finger → ill → done`
- M2: `StubProcessRunner` 코루틴 단계 UI
- M3+: ORT 세션 재사용 + engine 픽스처 파리티

## 저장

- `context.filesDir/sessions/{sessionId}/` (후속)
- 스키마 필드명·단계 enum은 iOS·ARCHITECTURE와 동일

## 파리티

- M2/M3에서 `engine/tests/fixtures/`를 자산으로 넣어 remap/inpaint 단위 테스트

## UX

공통 스펙은 [docs/UIUX.md](../docs/UIUX.md). CAMERA 권한은 Manifest에 선언; 셸은 권한 거부 루프 없이 placeholder 프리뷰.

## Commands

```bash
cd android
./gradlew test
./gradlew :app:assembleDebug
```
