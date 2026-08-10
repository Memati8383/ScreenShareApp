plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.screenmirror"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.screenmirror"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "3.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.webrtc)
    implementation(libs.okhttp)
    implementation(libs.material)
    implementation(libs.gson)

    // Lottie
    implementation(libs.lottie)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.ktx)
}
