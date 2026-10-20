plugins {
    id("com.android.application")
}

android {
    namespace = "dev.tvlauncher.helper"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.tvlauncher.helper"
        minSdk = 24
        targetSdk = 30
        versionCode = providers.gradleProperty("mintaka.versionCode").get().toInt()
        versionName = providers.gradleProperty("mintaka.versionName").get()
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
