plugins {
    id("com.android.application")
}

android {
    namespace = "dev.tvlauncher.browser"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.tvlauncher.browser"
        minSdk = 24
        targetSdk = 30
        versionCode = 1
        versionName = "0.1.0"
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
