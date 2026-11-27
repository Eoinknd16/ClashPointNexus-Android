import java.util.Properties

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

    // One fixed key for every build (signing/ at the repo root). Android only
    // installs an update over an app signed with the same key, so this is what
    // makes in-app updates work and keeps your settings across versions.
    // Debug builds use it too, so any build can update any other.
    val signingProps = Properties().apply {
        val f = rootProject.file("signing/signing.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    signingConfigs {
        create("nexus") {
            storeFile = rootProject.file("signing/" + (signingProps.getProperty("storeFile") ?: "nexus-release.keystore"))
            storePassword = signingProps.getProperty("storePassword")
            keyAlias = signingProps.getProperty("keyAlias") ?: "nexus"
            keyPassword = signingProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("nexus")
        }
        release {
            // R8 off again until it's verified on a device (it was a suspect in the v0.2.26 crashes).
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("nexus")
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
    // Installs the baseline profiles that ship inside Compose etc. on sideloaded installs,
    // so code is pre-compiled instead of running interpreted for the first sessions.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    // On-device Gemini Nano (the phone's built-in AI) for Ask Nexus.
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta4")
    debugImplementation(libs.androidx.compose.ui.tooling)
}
