pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LMBridge-KMP"
// 프로젝트 이름이 Maven 아티팩트 이름이 된다(lmbridge-kmp, lmbridge-kmp-android, lmbridge-kmp-desktop).
include(":lmbridge-kmp")
project(":lmbridge-kmp").projectDir = file("lmbridge")
