# AGENTS.md - LMBridge KMP

Kotlin Multiplatform on-device LLM SDK on Google LiteRT-LM. 상세는 `README.md`.

## Structure
- 모듈 하나: Gradle 경로 `:lmbridge-kmp`, 디렉터리 `lmbridge/` (프로젝트 이름 = Maven 아티팩트 이름).
- 소스 세트: `commonMain` → `jvmSharedMain`(Android+Desktop 공유) → `androidMain` / `desktopMain`.
- **litertlm 타입은 `jvmSharedMain/.../inference/LiteRtEngineAdapter.kt` 한 파일에만** import한다.
- 플랫폼 차이는 `Platform.kt`의 `expect`(PlatformContext, environment, 로그, IO 디스패처, 엔진 생성)로 모은다.
- 파일 접근은 공통 코드에서 kotlinx-io를 쓴다(향후 iOS 공유). `java.io`는 jvmShared/플랫폼 소스에서만.

## Tech
- Kotlin 2.4.21 (litertlm 0.18 → kotlin-stdlib 2.4), AGP 8.8.2, Gradle 8.14.4, JVM 17.
- litertlm 0.18.0: Android `litertlm-android`, Desktop `litertlm-jvm`(같은 버전·같은 API). 버전은 `gradle/libs.versions.toml`에서 함께 올린다.

## Verify
- `./gradlew :lmbridge-kmp:desktopTest :lmbridge-kmp:testDebugUnitTest`
- 실제 모델 데스크톱 테스트: `LMBRIDGE_TEST_MODEL=<.litertlm> ./gradlew :lmbridge-kmp:desktopTest`
- Android 계측 테스트: `connectedAndroidTest` 금지(기기 모델 삭제 위험). `assembleDebugAndroidTest` → `adb install -r -t` → `am instrument`.
- 어댑터 경계: `grep -rl com.google.ai.edge.litertlm lmbridge/src/*Main` → `LiteRtEngineAdapter.kt`만.

## Release
- 배포는 사용자가 한다. `vX.Y.Z` 태그 push → `.githooks/pre-push` → `scripts/publish-local.sh`가
  packages(gh-pages)의 `com/isroot/lmbridge-kmp{,-android,-desktop}/<버전>`을 함께 올린다. `com/isroot/lmbridge`(Android판)는 건드리지 않는다.
- 버전은 `lmbridge/build.gradle.kts`의 `version`. 버전 변경은 별도 커밋(`[chore] 버전명 변경 vX.Y.Z`).
