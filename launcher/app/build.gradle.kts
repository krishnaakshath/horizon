plugins {
    id("com.android.application")
}

android {
    namespace = "dev.tvlauncher"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.tvlauncher"
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
    // Shared volume bar and volume control (also built into the other app).
    sourceSets.getByName("main").kotlin.srcDir("../volumecore/src/main/java")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
