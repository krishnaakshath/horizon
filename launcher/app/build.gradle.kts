plugins {
    id("com.android.application")
}

android {
    namespace = "dev.tvlauncher"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.tvlauncher"
        minSdk = 24
        // 28 on purpose: keeps the legacy WifiManager calls (setWifiEnabled, addNetwork,
        // enableNetwork, removeNetwork, reconnect) working on Android 10/11, so Wi-Fi is run from
        // the launcher instead of the TV's settings. See lint.xml and docs/SYSTEM_SETTINGS.md.
        targetSdk = 28
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
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
