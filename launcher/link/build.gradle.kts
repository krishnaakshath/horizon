plugins {
    id("com.android.application")
}

android {
    namespace = "dev.tvlauncher.link"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.tvlauncher.link"
        minSdk = 24
        // 30, not 28: Google Play Protect blocks a fresh install of an app that targets 28
        // ("built for an older version of Android"). Package visibility is declared in <queries>.
        targetSdk = 30
        versionCode = providers.gradleProperty("mintaka.versionCode").get().toInt()
        versionName = providers.gradleProperty("mintaka.versionName").get()
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    // The launcher's contract with this app (dev.tvlauncher.link.core: pure constants, no
    // resources) and game controller support (dev.tvlauncher.input), compiled in here as well.
    sourceSets["main"].kotlin.directories.add("../app/src/main/java/dev/tvlauncher/link/core")
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
