import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    `maven-publish`
}

group = "com.isroot"
version = "0.1.0"

kotlin {
    compilerOptions {
        // PlatformContext(expect class ↔ Android Context typealias)에 쓴다.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        publishLibraryVariants("release")
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("jvmShared") {
                withAndroidTarget()
                withJvm()
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.io.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        val jvmSharedMain by getting {
            dependencies {
                // Android/데스크톱 litertlm은 같은 API다. 공유 코드는 JVM판에 맞춰 컴파일하고,
                // 실제 런타임은 각 플랫폼 소스 세트가 제공한다.
                compileOnly(libs.litertlm.jvm)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        androidMain.dependencies {
            implementation(libs.litertlm.android)
        }
        val desktopMain by getting {
            dependencies {
                implementation(libs.litertlm.jvm)
            }
        }
        val androidInstrumentedTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.ext.junit)
            }
        }
    }
}

// 데스크톱 통합 테스트용 모델 경로를 그대로 넘긴다(없으면 해당 테스트는 건너뛴다).
tasks.named<Test>("desktopTest") {
    System.getenv("LMBRIDGE_TEST_MODEL")?.let { environment("LMBRIDGE_TEST_MODEL", it) }
    testLogging { showStandardStreams = true }
}

android {
    namespace = "com.isroot.lmbridge"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// 좌표: com.isroot:lmbridge-kmp(공통 메타데이터) · lmbridge-kmp-android · lmbridge-kmp-desktop(프로젝트 이름에서 정해진다).
// 소비자는 lmbridge-kmp 하나만 선언하면 Gradle 메타데이터가 플랫폼별 아티팩트를 고른다.
publishing {
    // 배포 스크립트(scripts/publish-local.sh)가 이 디렉터리를 packages(gh-pages)로 복사한다.
    repositories {
        maven {
            name = "local"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("LMBridge KMP")
            description.set("Kotlin Multiplatform on-device LLM SDK on Google LiteRT-LM (Android, Desktop JVM)")
            url.set("https://github.com/r00tIsRoot/LMBridge_KMP")
            licenses {
                license {
                    name.set("Apache-2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0")
                }
            }
        }
    }
}
