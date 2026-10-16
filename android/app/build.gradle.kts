import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localSettings = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val amapLocationKey = providers.environmentVariable("AMAP_LOCATION_KEY")
    .orElse(providers.gradleProperty("amapLocationKey"))
    .orElse(localSettings.getProperty("amapLocationKey", "")).get().trim()
require(amapLocationKey.isEmpty() || amapLocationKey.matches(Regex("[a-fA-F0-9]{32}"))) {
    "amapLocationKey must be a 32-character Android SDK key"
}

android {
    namespace = "cn.qztc.timetable"
    compileSdk = 34

    defaultConfig {
        applicationId = "cn.qztc.timetable"
        minSdk = 26
        targetSdk = 34
        versionCode = 14
        versionName = "1.5.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["amapLocationKey"] = amapLocationKey
    }

    buildTypes {
        release {
            // Local QA release must be installable; production signing stays outside the repository.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/academicAssets"))

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    androidResources {
        // Applied during asset merging, including assets from library modules.
        // Keep source files for desktop authoring; the app loads mobile.html.
        ignoreAssetsPatterns += listOf("map-runtime.js", "map-editor.js", "index.html", "app.html", "app.js", "app.css", "map.css", "*.md")
    }

}

val prepareAcademicAssets by tasks.registering(Sync::class) {
    from(rootProject.file("../web/academic-preview")) {
        include("*.js", "*.css", "community-feed.json", "index.html")
        exclude("audited-record-data.js")
        rename("index.html", "mobile.html")
        rename("app.js", "academic-app.js")
        filter { line: String -> line.replace("src=\"app.js\"", "src=\"academic-app.js\"") }
    }
    into(layout.buildDirectory.dir("generated/academicAssets/academic-hub"))
}
tasks.named("preBuild").configure { dependsOn(prepareAcademicAssets) }

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":feature:timetable"))
    implementation(project(":feature:settings"))
    implementation(project(":integration:qnu-android"))

    implementation(libs.androidx.core.ktx)
    implementation("androidx.webkit:webkit:1.12.1")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation("de.marhali:json5-java:3.0.0")
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui.tooling.preview)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation("com.amap.api:location:11.2.000")
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
