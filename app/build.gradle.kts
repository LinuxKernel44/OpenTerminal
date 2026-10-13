import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.openterminal"
    compileSdk = 37
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.openterminal"
        minSdk = 26
        targetSdk = 36
        versionCode = 10000
        versionName = "1.0.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    // Release signing: keystore.properties (not committed) or OPENTERMINAL_* environment variables.
    val keystoreProps = Properties().apply {
        listOf(rootProject.file("keystore.properties"), file(System.getProperty("user.home") + "/.openterminal-signing/keystore.properties"))
            .firstOrNull { it.exists() }?.inputStream()?.use { load(it) }
    }
    fun signingValue(prop: String, env: String): String? = keystoreProps.getProperty(prop) ?: System.getenv(env)
    val releaseStore = signingValue("storeFile", "OPENTERMINAL_KEYSTORE")

    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = signingValue("storePassword", "OPENTERMINAL_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "OPENTERMINAL_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "OPENTERMINAL_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt") }
    }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }

    packaging {
        jniLibs { useLegacyPackaging = true }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":terminal-emulator"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.backdrop)
    implementation(libs.kyant.shapes)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
}
