# ROADMAP.md

Scanbooks 수행 계획(마일스톤·순서·미결정·게이트). 계약은 [ARCHITECTURE.md](ARCHITECTURE.md).

factory `pm-roadmap-sync`는 `##` 아래 **`- [ ]` / `- [x]`** 만 enqueue한다 (`###`·plain bullet → no-op).

## 목표

휘어진 책 페이지를 모바일에서 촬영해 DocTr 기반 곡면·조명 보정과 손가락 제거 후 PDF로 만드는 네이티브 앱(iOS Swift / Android Kotlin).

## M0 — 문서 고정 (완료)

- [x] 기술 조사 반영(DocTr 경량화 우선, 네이티브 듀얼, MVP 전체 파이프라인)
- [x] [ARCHITECTURE.md](ARCHITECTURE.md) / 본 문서 / [README.md](README.md) / `engine|ios|android/DESIGN.md` 초안
- [x] DocTr 라이선스 검토 결과 기록(아래 §라이선스) — 비상업 전제 확정, 상업 경로 open
- [x] M1 착수 전 기술 쟁점 반영: GeoTr 288 vs work 1024 분리, IllTr 패치, `geo→finger→ill`, BBox crop, ORT/양자화/파리티

**완료 기준:** 루트 문서와 컴포넌트 DESIGN이 위 쟁점을 반영하고, AGENTS §3가 M1 대기. — 충족(문서 정정 포함).

## M1 — 엔진 스파이크 (`engine/`)

착수 순서(이 순서를 지킨다):

- [x] **Export 프로토타입 먼저:** DocTr 체크포인트에서 실제 I/O 텐서(`288×288` GeoTr, `128×128` IllTr 패치), 파라미터 수·FP16 용량, ORT 연산자(GridSample/LayerNorm/Reshape) 호환성을 측정하고 ARCHITECTURE §5 숫자를 확정·필요 시 미세 수정. — 증거: `engine/benches/export_prototype.md` (합 FP16 75.89 MB; GridSample은 후처리)
- [x] **파이프라인 실험 매트릭스:** 골든 세트(곡률 큰 책 / 엄지 가림 / 본문 인물·손 사진)에 대해 ① BBox+margin crop vs Quad homography → GeoTr, ② `geo→finger→ill`(계약 기본) vs `geo→ill→finger` 품질 비교 후 기본 경로를 문서에 재확인. — 증거: `engine/benches/experiment_matrix.md` (BBox·`geo→finger→ill` 유지)
- [x] **파리티 픽스처:** `engine/tests/fixtures/`에 전처리 텐서 + `raw_flow→warped` + `raw_mask→inpainted` 기준 출력 포함. — 증거: `engine/tests/fixtures/parity/` + `test_geo_warp_parity` / `test_finger_inpaint_parity`
- [x] ONNX 최적화·양자화 후보(weight-only INT8 / dynamic; **static INT8 기본 금지**). — 증거: `engine/benches/quantize_candidates.md`
- [x] FingerSeg 후보 + 외곽 CC 필터 + Telea/NS. — 증거: `engine/benches/fingerseg_candidates.md` + `finger_pipeline` / `test_finger_pipeline`
- [x] EP별 latency 벤치 → `benches/latency.md`. — 증거: `engine/benches/latency.md` (GeoTr e2e pass; IllTr seq fail→export 단순화; GeoTr quant=`dynamic_int8`; default EP=CoreML MLProgram NE)

**완료 기준(성공 게이트):**

| 항목 | 기준 |
|---|---|
| GeoTr e2e | ≤ 2.5 s / page @ net 288 + workLongEdge 1024 |
| IllTr e2e | ≤ 3.5 s / page @ 128 패치(+배치 우선) + stitch |
| 온디바이스 상주 합 | ≤ ~80 MB 권고; **초과 시 온디맨드 다운로드/캐시 설계를 M1 산출에 포함**(M4로 미루지 않음) |
| 양자화 | static INT8 미채택(또는 flow head 붕괴 시 폐기). weight-only/dynamic/mixed만 |
| EP | CPU FP32 vs weight-INT8, CoreML/NNAPI FP16 분리 측정 + fallback 기록 |
| 품질 | max abs / SSIM(+ OCR CER 스모크). 해시 금지 |
| 실험 매트릭스 | crop·단계 순서 비교 결과 기록, 계약 기본(`BBox`, `geo→finger→ill`) 유지 또는 문서 개정 |
| 아티팩트 | 버전 태그·체크섬·`models/` 메타 |

**미달 완화(DocTr GeoTr 유지):** `workLongEdge` 768 → IllTr 옵션화 → IllTr 경량 full-image 대체. GeoTr 입력을 1024로 키우지 않는다. DocScanner 교체는 M4 이후.

## M2 — 앱 스켈레톤 (`ios/`, `android/`)

- [x] UI는 [docs/UIUX.md](docs/UIUX.md)의 `Camera`(1페이지)·stub `Process`·간단 `Session`·PDF를 따른다 — 증거: PR #8 (`4679c600`)
- [x] 카메라 프리뷰 + 실시간 테두리 — 증거: PR #9 (`1113c912`)
- [x] 단장 촬영, 로컬 `ScanSession`/`Page` 저장(중간 PNG) — 증거: PR #10 (`56345717`)
- [x] 파이프라인 stub(`crop→geo→finger→ill` 상태 전이) — 증거: PR #11 (`c878b056`)
- [x] 파리티 픽스처로 remap/inpaint 단위 테스트 훅 준비 — 증거: `CvBridge` + `ParityRemapInpaintHookTests`/`ParityRemapInpaintHookTest` (remap 파리티 live; inpaint OpenCV 훅); PR #12 (`4563165b`)
- [x] `cleanedUri`(또는 stub) → PDF export — 증거: `PdfExporter`/`PdfExporterTests`·`PdfExporterTest` + PipelineStub `cleaned.png`; PR #13 (`416db9f3`)
- [x] vFlat/OSS Document Scanner UX는 참고만(복제·포크 금지) — 증거: `615012b0` done (PR #8–#13 fork/복제 없음; `docs/UIUX.md` 참고-only)

**완료 기준:** 양 플랫폼에서 촬영→(stub)→PDF 수동 스모크 통과.

## M3 — 엔진 연동

- [x] ORT Mobile로 `geo → finger → ill` 연동(선택한 EP) — 증거: PR #15 (`caa47a9`)
- [x] 페이지별 진행 UI, `PageMetrics`(`workLongEdge`, `geoNetSize`, `ortEp` 포함) — 증거: `PageMetricsPresentation` + Process 단계 ms/계약 라인 (본 PR)
- [ ] `failed` + 실패 단계부터 재시도
- [ ] 실기기 스모크(중급기 1대 이상/플랫폼)

**완료 기준:** 책 페이지 실촬영 3장 이상에서 `done` 및 PDF 생성.

## M4 — 배치·폴리시

- [ ] 연속(배치) 스캔, 세션 내 페이지 재정렬·삭제
- [ ] 성능 튜닝, 온디맨드 다운로드 운영 다듬기(M1에서 이미 도입했을 수 있음)
- [ ] attribution UI, 개인정보(온디바이스) 고지
- [ ] 릴리스 체크리스트(스토어 전 라이선스 항목 닫기)

**완료 기준:** 내부 배포 빌드 + 체크리스트 통과. 상업 스토어는 라이선스 허가 후에만.

## 라이선스 검토

| 항목 | 상태 | 조치 |
|---|---|---|
| DocTr 코드/가중치 비상업 사용 | **확인됨(전제)** | 연구·개인·비영리 빌드는 attribution 후 진행 가능. [DocTr](https://github.com/fh2019ustc/DocTr), [DocTr-Plus LICENSE](https://github.com/fh2019ustc/DocTr-Plus/blob/master/LICENSE.md) |
| DocTr 상업 배포 | **미결** | `haof@mail.ustc.edu.cn` / `zhwg@ustc.edu.cn` 서면 허가 전 상업 릴리스 금지 |
| ONNX Runtime / OpenCV | 표준 OSS | About·NOTICE 고지 |
| FingerSeg 최종 가중치 | **미선정** | M1 확정 시 라이선스 재검토 |
| UX 참고 OSS Document Scanner | MIT, 참고만 | 코드 복사·포크 없음 |

## 명시적 비범위 (MVP)

- 서버 동기화·계정·클라우드 OCR
- 실시간(프리뷰 프레임) Dewarp
- GeoTr 네트워크 입력을 288 이외(예: 1024)로 키우기
- DocTr → DocScanner/DocTr++ 전면 교체(M4 이후 검토)
- Core ML 전용 경로를 MVP 필수로 강제
- 손글씨·도장 제거 등 Finger 이외 복원
- GeoTr/IllTr static INT8을 기본 배포 경로로 채택

## 다음 액션

1. IllTr export forward 단순화(노드 폭발 완화) — 양자화 후보(WO)는 문서화됨
2. FingerSeg 가중치 학습/export(후보 A) — 후처리·후보 표는 `fingerseg_candidates.md` 완료; 라이선스 행은 가중치 확정 시 갱신
3. ~~EP·latency 벤치(`benches/latency.md`) 후 게이트 판정 — GeoTr dyn vs WO 최종 선택~~ → **완료:** GeoTr=`dynamic_int8`; IllTr 게이트는 export 단순화·배치 선행(완화 경로 유지)
4. 상업화 시 라이선스 문의 병행
