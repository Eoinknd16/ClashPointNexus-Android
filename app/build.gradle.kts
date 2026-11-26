plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.eoinknd16.nexus"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.eoinknd16.nexus"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("NEXUS_VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("NEXUS_VERSION_NAME") ?: "0.1.0"
    }

    signingConfigs {
        // CI signs release builds with a keystore passed in through repo
        // secrets (see .github/workflows/android.yml). Without one, release
        // falls back to the debug key so the build still produces an
        // installable APK -- fine for sideloading test builds.
        create("release") {
            val path = System.getenv("NEXUS_KEYSTORE_PATH")
            if (path != null && file(path).exists()) {
                storeFile = file(path)
                storePassword = System.getenv("NEXUS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("NEXUS_KEY_ALIAS")
                keyPassword = System.getenv("NEXUS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val release = signingConfigs.getByName("release")
            signingConfig = if (release.storeFile != null) release else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.ui)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
