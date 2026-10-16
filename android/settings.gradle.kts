pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.google.devtools.ksp") {
                useModule("com.google.devtools.ksp:symbol-processing-gradle-plugin:${requested.version}")
            }
        }
    }
    repositories {
        if (System.getenv("GITHUB_ACTIONS") == "true") {
            gradlePluginPortal()
            google()
            mavenCentral()
        } else {
            // 国内镜像优先，Central/Google 直连在部分网络下会握手失败。
            maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (System.getenv("GITHUB_ACTIONS") == "true") {
            google()
            mavenCentral()
        } else {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
            google()
            mavenCentral()
        }
    }
}

rootProject.name = "QztcTimetable"

include(
    ":app",
    ":core:domain",
    ":core:data",
    ":core:ui",
    ":integration:qnu",
    ":integration:qnu-android",
    ":feature:timetable",
    ":feature:settings",
    ":tools:adskip-fixture",
)
