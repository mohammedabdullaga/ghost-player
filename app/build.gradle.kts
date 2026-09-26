plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android) // ADDED: Core Kotlin plugin
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.objectbox)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.firebase.perf)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.0"
}

android {
    namespace = "com.eliteprime.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.eliteprime.app"
        minSdk = 25
        targetSdk = 37
        versionCode = 11
        versionName = "1.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
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

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.perf)
    implementation(libs.firebase.config)

    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    // Compose
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // Compose TV
    implementation(libs.compose.tv.foundation)
    implementation(libs.compose.tv.material)
    implementation(libs.engage.tv)
    implementation(libs.androidx.tvprovider)
    implementation(libs.androidx.documentfile)

    // REMOVED: ObjectBox manual dependencies. The plugin handles this for you!

    // Media3
    implementation(files("libs/media3-decoder-ffmpeg-1.11.0.aar"))
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.rtsp)
    implementation(libs.media3.exoplayer.smoothstreaming)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Paging
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    // Image Loading
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Lifecycle & Navigation
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)

    // libVLC
    implementation("org.videolan.android:libvlc-all:3.5.1")

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // QR Code
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}