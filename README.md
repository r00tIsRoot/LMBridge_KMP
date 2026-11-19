# LMBridge KMP

Google LiteRT-LM 기반 온디바이스 LLM SDK의 **Kotlin Multiplatform** 판입니다.
Android 전용 [LMBridge](https://github.com/r00tIsRoot/LMBridge) 0.3.2의 공개 API를 공통 코드로 옮겨,
같은 코드로 Android와 데스크톱(JVM, Compose Multiplatform Desktop 등)에서 모델을 돌립니다.

## 지원 플랫폼

| 타깃 | 런타임 | 상태 |
|---|---|---|
| Android (minSdk 26) | `litertlm-android` | ✅ 기기 검증(SM-A426N: SmolLM2-135M GPU/CPU, Gemma-4-E2B GPU) |
| Desktop JVM — macOS Apple Silicon | `litertlm-jvm` | ✅ 검증(SmolLM2-135M CPU/GPU) |
| Desktop JVM — Windows x64, Linux x64/arm64 | `litertlm-jvm` | 🔶 네이티브 포함, 미검증 |
| Desktop JVM — macOS Intel | — | ❌ litertlm-jvm에 네이티브 없음 |
| iOS | `CLiteRTLM.xcframework`(C API) | ⏳ 예정 — Kotlin/Native cinterop |
| Web | `@litert-lm/core`(npm) | ⏳ 예정 — Kotlin/JS 바인딩 |

`litertlm-android`와 `litertlm-jvm`은 같은 버전으로 함께 배포되고 공개 클래스가 같아(0.18.0에서 96개 클래스 일치 확인),
어댑터 코드 한 벌을 Android와 데스크톱이 공유합니다.

## 설치

```kotlin
dependencies {
    implementation("com.isroot.lmbridge-kmp:kmp:0.1.1")
}
```

KMP 프로젝트라면 `commonMain`에 선언하면 됩니다. Gradle 메타데이터가 타깃에 맞는
`kmp-android` / `kmp-desktop`을 고릅니다. Kotlin 2.4 이상이 필요합니다(litertlm 요구 사항).

## 사용법

공통 코드는 `PlatformContext`만 받으면 플랫폼과 무관하게 같습니다.

```kotlin
suspend fun ask(context: PlatformContext, modelPath: String): String {
    val client = LMBridgeClient.Builder(context)
        .setModelPath(modelPath)
        .setBackend(LMBridge.Backend.GPU)
        .build()
    client.initialize()
    return try {
        client.generateText("Name three colors.")
    } finally {
        client.release()
    }
}
```

- **Android**: `PlatformContext`는 `android.content.Context`의 별칭입니다. `LMBridgeClient.Builder(context)` 그대로 씁니다.
- **데스크톱**: `DesktopContext.forApp("MyApp")`이 OS 관례 경로(macOS `~/Library/Application Support/MyApp`,
  Windows `%LOCALAPPDATA%\MyApp`, Linux `~/.local/share/MyApp`)에 모델(`models/`)과 엔진 캐시(`cache/`)를 둡니다.
  경로를 직접 정하려면 `DesktopContext(File(...))`를 씁니다.

대화(`newChat`), 단발 생성(`generate`/`generateText`), 도구 호출, 추론(thinking), 멀티모달 입력,
모델 카탈로그·다운로드(`client.models`), 초기화 실패 분류(`InvalidModel` / `BackendUnavailable`)는 LMBridge와 같습니다.

### 이미지 입력

플랫폼 이미지 타입은 확장 함수로 넣습니다. 인코딩 정책(`ImageEncoding`)은 공통입니다.

```kotlin
import com.isroot.lmbridge.models.image

MultimodalInput.Builder()
    .image(bitmap, ImageEncoding.COMPACT_JPEG)      // Android: Bitmap
    .image(bufferedImage, ImageEncoding.COMPACT_JPEG) // 데스크톱: BufferedImage
    .text("이 사진을 설명해줘")
    .build()
```

이미 인코딩된 바이트(`imageBytes`)와 파일 경로(`imageFile`)는 공통 코드에서 바로 쓸 수 있습니다.

## LMBridge(Android) 0.3.2와 다른 점

| 항목 | LMBridge 0.3.2 | LMBridge KMP |
|---|---|---|
| 좌표 | `com.isroot:lmbridge` | `com.isroot.lmbridge-kmp:kmp` |
| `Builder(context)` | `Context` | `PlatformContext`(Android에서는 `Context`와 같음) |
| 비트맵 입력 | `Builder.image(bitmap)` 멤버 | 확장 함수 — `import com.isroot.lmbridge.models.image` 필요. 추가 시점에 인코딩된다 |
| `MultimodalInput.textAndImages` | 멤버 | 확장 함수(Android) |
| `MultimodalContent.Image` | 있음 | 제거(인코딩된 `ImageBytes`로 들어감) |
| 문서 문자셋 | `Charset` | 문자셋 이름 `String`(JVM은 `Charset` 오버로드 확장 제공) |
| `ModelStore` 경로 | `File` 반환 | `String` 반환 |
| 도구 인자(`Tool.executor`) | 배열·객체가 `org.json` 타입 | `List`·`Map` |
| 하위호환 shim | `GenerationResult`, `generateWith*`, `stopGeneration`, `close`, `getDownloadManager`, `Builder.audio` | 제거 |

## 구조

```
lmbridge/src
├─ commonMain     공개 API·도메인 타입·카탈로그·다운로드/검증 흐름·초기화 실패 분류 (kotlinx-io로 파일 접근)
├─ jvmSharedMain  Android+데스크톱 공유: LiteRtEngineAdapter(litertlm), HTTP 이어받기, SHA-256
├─ androidMain    PlatformContext = Context, Bitmap 인코딩, Logcat, assets 번들 모델
└─ desktopMain    DesktopContext, BufferedImage 인코딩, 표준 오류 로그, 물리 메모리 조회
```

- litertlm 타입은 `jvmSharedMain/.../inference/LiteRtEngineAdapter.kt`에서만 import합니다(어댑터 경계).
- `jvmSharedMain`은 `litertlm-jvm`에 컴파일 시점에만 의존하고, 런타임은 각 플랫폼이 제공합니다
  (Android `litertlm-android`, 데스크톱 `litertlm-jvm`).

## 개발

```bash
./gradlew :lmbridge-kmp:desktopTest           # 공통 + 데스크톱 단위 테스트
./gradlew :lmbridge-kmp:testDebugUnitTest     # 공통 테스트를 Android JVM에서
LMBRIDGE_TEST_MODEL=/path/SmolLM2_135M_Instruct.litertlm ./gradlew :lmbridge-kmp:desktopTest  # 실제 모델 통합 테스트
./gradlew :lmbridge-kmp:publishToMavenLocal
```

Android 계측 테스트는 기기의 모델 파일을 지우지 않도록 `connectedAndroidTest` 대신 직접 설치해 실행합니다.

```bash
./gradlew :lmbridge-kmp:assembleDebugAndroidTest
adb install -r -t lmbridge/build/outputs/apk/androidTest/debug/lmbridge-kmp-debug-androidTest.apk
adb shell am instrument -w -e backend GPU -e modelPath /data/local/tmp/lmbridge/smol135.litertlm \
  com.isroot.lmbridge.test/androidx.test.runner.AndroidJUnitRunner
```

## 배포

기존 LMBridge와 같은 방식입니다. `vX.Y.Z` 태그를 push하면 로컬 pre-push hook이 `scripts/publish-local.sh`를 실행해
`r00tIsRoot/packages`(gh-pages, https://r00tisroot.github.io/packages/)의 `com/isroot/lmbridge-kmp/` 한 폴더 아래로
**`kmp`(공통), `kmp-android`, `kmp-desktop` 세 아티팩트를 같은 버전으로 함께** 올립니다.

> 0.1.0·0.1.1은 처음에 이전 좌표(`com.isroot:lmbridge-kmp`)로 배포되었으나, 같은 소스를 새 좌표로 다시 빌드해 이 경로로 옮겼습니다.
> 0.1.0은 Android GPU 버그(OpenCL 선언 누락)가 있으니 0.1.1 이상을 쓰세요.
배포가 실패하면 태그 push도 중단됩니다.

```bash
git config core.hooksPath .githooks          # 저장소마다 한 번
# lmbridge/build.gradle.kts 의 version 을 올리고 커밋한 뒤
git tag v0.1.0 && git push origin v0.1.0     # 빌드 → 배포 → 태그 push
DRY_RUN=1 ./scripts/publish-local.sh         # push 직전까지만 검증
```

- 태그 버전과 `version`이 다르면 배포를 중단합니다.
- packages 클론은 `~/.cache/lmbridge-packages`를 LMBridge와 함께 씁니다(`PACKAGES_DIR`로 변경 가능).
- `.github/workflows/publish.yml`은 같은 일을 하는 수동 실행(workflow_dispatch) 전용입니다.

소비 측 저장소 설정:

```kotlin
repositories { maven { url = uri("https://r00tisroot.github.io/packages/") } }
```

## 다음 단계

1. **iOS**: LiteRT-LM 릴리스의 `CLiteRTLM.xcframework`(C API)를 cinterop으로 묶고 `iosMain` 어댑터 작성.
   HTTP 다운로드는 `NSURLSession` 또는 Ktor로 `downloadToPart`를 구현.
2. **Web**: npm `@litert-lm/core`를 Kotlin/JS 외부 선언으로 감싸 `jsMain` 어댑터 작성(파일 경로 대신 URL·바이트 입력).
3. 임베딩(`EmbeddingEngine`) 래퍼 — 두 litertlm 모두 같은 API로 제공.
