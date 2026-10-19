plugins {
    id("com.android.application")
}

android {
    namespace = "dev.tvprobe"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.tvprobe"
        minSdk = 24
        targetSdk = 30
        versionCode = 1
        versionName = "0.0.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
