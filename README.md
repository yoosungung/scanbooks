# Scanbooks

모바일에서 책을 촬영해 휘어진 페이지를 펴고(DocTr 기반 dewarp), 손가락을 지운 뒤 PDF로 모으는 **온디바이스** 스캔 앱입니다. iOS(Swift)와 Android(Kotlin) 네이티브로 만듭니다.

## 문서

| 문서 | 용도 |
|------|------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | 계약·파이프라인·스키마·모델 I/O |
| [ROADMAP.md](ROADMAP.md) | 마일스톤·성능 게이트·라이선스 |
| [AGENTS.md](AGENTS.md) | 에이전트/기여자 워크플로 |
| [engine/DESIGN.md](engine/DESIGN.md) | 변환·벤치·골든 테스트 |
| [ios/DESIGN.md](ios/DESIGN.md) | iOS 앱 내부 설계 |
| [android/DESIGN.md](android/DESIGN.md) | Android 앱 내부 설계 |
| [docs/UIUX.md](docs/UIUX.md) | 공통 UI/UX (vFlat류 패턴 반영) |

## 현황

기획 문서(M0) 확정. M1 엔진 스파이크(export·매트릭스·파리티·양자화·FingerSeg·EP latency) 완료 — [engine/benches/latency.md](engine/benches/latency.md). 앱 코드는 아직 없습니다.

## 로컬 quickstart

코드가 들어오면 각 컴포넌트 `DESIGN.md`의 `## Commands`를 따릅니다.

- 엔진 변환·벤치: [engine/DESIGN.md](engine/DESIGN.md)
- iOS: [ios/DESIGN.md](ios/DESIGN.md)
- Android: [android/DESIGN.md](android/DESIGN.md)

## 라이선스 주의

DocTr 가중치·코드는 비상업(연구·개인·비영리) 전제가 강합니다. 상업 스토어 배포 전 저자 허가가 필요합니다. 자세한 상태는 [ROADMAP.md](ROADMAP.md) §라이선스 검토를 보세요.
