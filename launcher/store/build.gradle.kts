plugins {
    id("com.android.application")
}

android {
    namespace = "dev.tvlauncher.store"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.tvlauncher.store"
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
    // Game controller support shared with the launcher (dev.tvlauncher.input: pure rules + one
    // Activity helper; no resources), compiled into this app as well.
    sourceSets["main"].kotlin.directories.add("../app/src/main/java/dev/tvlauncher/input")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
