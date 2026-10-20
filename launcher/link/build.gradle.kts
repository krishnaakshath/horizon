plugins {
    id("com.android.application")
}

android {
    namespace = "dev.tvlauncher.link"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.tvlauncher.link"
        minSdk = 24
        // Same target as the launcher (:app): the phone page asks the launcher to start apps and
        // the H02 Browser; at 28 every package is visible and the background rules match :app.
        targetSdk = 28
        versionCode = 1
        versionName = "0.1.0"
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
