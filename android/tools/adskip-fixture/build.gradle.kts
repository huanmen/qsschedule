plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "org.example.adskipfixture"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.example.adskipfixture"
        minSdk = 26
        targetSdk = 28
    }
}
