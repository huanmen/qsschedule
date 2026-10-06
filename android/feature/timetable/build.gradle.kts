plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val campusMapApiBaseUrl = providers.gradleProperty("campusMapApiBaseUrl")
val productionCampusMapApi = "https://web.huanmen.com.cn/qztc/ditu/api"
val emulatorCampusMapApi = "http://10.0.2.2:19120/api"

android {
    namespace = "cn.qztc.timetable.feature.timetable"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        getByName("debug") {
            buildConfigField(
                "String",
                "CAMPUS_MAP_API_BASE_URL",
                "\"${campusMapApiBaseUrl.orElse(emulatorCampusMapApi).get()}\"",
            )
        }
        getByName("release") {
            buildConfigField(
                "String",
                "CAMPUS_MAP_API_BASE_URL",
                "\"${campusMapApiBaseUrl.orElse(productionCampusMapApi).get()}\"",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("com.amap.api:location:11.2.000")
    implementation("net.jafama:jafama:2.3.2")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation(libs.androidx.core.ktx)
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation("androidx.webkit:webkit:1.9.0")
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
