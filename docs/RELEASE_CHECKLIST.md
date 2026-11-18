# Release checklist — 스토어 전 라이선스 게이트

계약: [ARCHITECTURE.md](../ARCHITECTURE.md) §7 · 계획 표: [ROADMAP.md](../ROADMAP.md) §라이선스 검토.  
앱 게이트 상수: `ReleaseChecklistGate` (iOS/Android).

## 배포 경로

| 경로 | 게이트 | 상태 | 조건 |
|---|---|---|---|
| 내부/비상업 배포 (debug·TestFlight 비상업·사이드로드) | `internalDistribution` | **pass** | attribution·개인정보·DocTr 비상업 전제·ORT/OpenCV 고지 closed |
| 상업 스토어 (App Store / Play 유료·광고·상업 배포) | `commercialStore` | **blocked** | DocTr 저자 서면 허가 전 금지 |

서면 허가 연락: `haof@mail.ustc.edu.cn` / `zhwg@ustc.edu.cn` ([DocTr-Plus LICENSE](https://github.com/fh2019ustc/DocTr-Plus/blob/master/LICENSE.md)).

## 라이선스 항목

| id | 상태 | 요약 | 증거/조치 |
|---|---|---|---|
| `doctr_noncommercial` | **closed** | DocTr 코드·가중치 비상업 사용 전제 | ROADMAP 표 · Settings non-commercial notice |
| `doctr_commercial` | **open** | 상업 배포 | 서면 허가 전까지 스토어 제출 금지 |
| `ort_opencv_notice` | **closed** | ONNX Runtime / OpenCV 표준 OSS | `AttributionPresentation.creditLines` |
| `fingerseg_final_weights` | **open** | FingerSeg 최종 가중치 | `models/manifest.json` `candidate_a_pending_weights` — 선정 시 라이선스 재검토 |
| `attribution_ui` | **closed** | Settings attribution UI | PR #22 |
| `privacy_notice` | **closed** | 온디바이스 개인정보 고지 | PR #22 · 첫 스캔 툴팁 |
| `ux_oss_reference_only` | **closed** | Document Scanner UX 참고만 | 코드 복사·포크 없음 (ARCHITECTURE §7) |

## 내부 배포 통과 기준

1. 위 closed 항목이 유지되고 `ReleaseChecklistGate.internalDistributionAllowed == true`
2. `commercialStoreAllowed == false` 이며 open 목록에 `doctr_commercial`·`fingerseg_final_weights` 포함
3. 단위 테스트: `ReleaseChecklistGateTest(s)` green · Android `:app:assembleDebug` 성공

상업 스토어 체크리스트는 DocTr 허가 + FingerSeg 가중치·라이선스 행 closed 후에만 pass로 올린다.
