# android/DESIGN.md

Android 네이티브 앱 내부 설계. 계약·스키마는 [ARCHITECTURE.md](../ARCHITECTURE.md). 화면·플로우는 [docs/UIUX.md](../docs/UIUX.md).

## 책임

- Jetpack Compose(또는 XML) UI, CameraX 프리뷰
- 실시간 문서 테두리(ML Kit 또는 OpenCV)
- 로컬 `ScanSession` / `Page` 저장
- ONNX Runtime Mobile로 네트워크 추론
- OpenCV로 **flow remap**·**Telea/NS inpaint** (engine 파리티 픽스처 준수)
- `PdfDocument` export

모델 학습·ONNX 변환은 `engine/` 몫이다.

## 내부 구조 (예정)

```
android/
  DESIGN.md
  app/
    src/main/java/.../scanbooks/
      camera/          # quad → BBox+margin crop
      pipeline/        # crop → geo → finger → ill
      ort/
      cv/              # remap, inpaint, morph, CC filter
      data/
      export/
      ui/
    src/test/          # 파리티 픽스처
    src/androidTest/
```

## 파이프라인 러너

- 순서: `crop → geo → finger → ill → done`
- ORT 세션 프로세스 내 재사용; EP는 M1 벤치 결과로 고정
- IllTr 패치는 가능하면 배치 입력으로 한 세션 호출에 모은다
- 전·후처리는 engine 픽스처로 비트/허용오차 검증

## 저장

- `context.filesDir/sessions/{sessionId}/`
- `original.jpg`; 중간·최종 `cropped.png` / `dewarped.png` / `cleaned.png`
- 스키마 필드명·단계 enum은 iOS·ARCHITECTURE와 동일

## 파리티

- M2/M3에서 `engine/tests/fixtures/`를 자산으로 넣어 remap/inpaint 단위 테스트

## UX

공통 스펙은 [docs/UIUX.md](../docs/UIUX.md). CAMERA 권한·네이티브 모듈은 이 문서·구현에서 다룬다.

## Commands

Gradle 프로젝트 생성 전이라 명령을 적지 않는다. M2에서 `./gradlew` 테스트·빌드 커맨드를 추가한다.
