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

## 내부 구조

```
ios/
  DESIGN.md
  project.yml              # XcodeGen
  Scanbooks.xcodeproj/     # generated
  Scanbooks/
    App/                   # ScanbooksApp, RootView (route shell)
    Camera/                # M2: 1페이지 chrome + placeholder quad
    Process/               # M2: StubProcessRunner + stage list UI
    Session/               # M2: grid + PDF CTA stub alert
    Models/                # AppRoute, PipelineStage
  ScanbooksTests/
```

M2 first slice: UI shell only. Live camera / Vision quad / disk `ScanSession` / PDFKit bytes = 후속 슬라이스.

## 파이프라인 러너

- 순서: `crop → geo → finger → ill → done` ([ARCHITECTURE §1.3](../ARCHITECTURE.md))
- M2: `StubProcessRunner`가 단계 UI만 순회 (ORT 없음)
- M3+: `geo` ORT → remap → finger → ill → `cleaned.png`

## 저장

- `sessions/{sessionId}/` (후속)
- `original.jpg`; 중간·최종 PNG; 단계 간 JPEG 재인코딩 금지

## 파리티

- `engine/tests/fixtures/` → `CvBridge` 단위 테스트 (후속)

## UX

공통 스펙은 [docs/UIUX.md](../docs/UIUX.md).

## Commands

```bash
cd ios
xcodegen generate
xcodebuild test -scheme Scanbooks -destination 'platform=iOS Simulator,name=iPhone 17,OS=27.0'
```

시뮬레이터 이름이 환경마다 다르면 `xcrun simctl list devices available`으로 destination을 맞춘다.
