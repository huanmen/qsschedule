plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

// Gradle's Windows test worker cannot reliably decode UTF-8 classpath argfiles
// when compiled classes live below a non-ASCII project path.
if (rootDir.absolutePath.any { it.code > 127 }) {
    val workspaceId = rootDir.absolutePath.lowercase().hashCode().toUInt().toString(16)
    val externalBuildRoot = gradle.gradleUserHomeDir.resolve("qztc-timetable-build/$workspaceId")

    allprojects {
        val projectFolder = path.trimStart(':').replace(':', '/').ifEmpty { "root" }
        layout.buildDirectory.set(externalBuildRoot.resolve(projectFolder))
    }
}
