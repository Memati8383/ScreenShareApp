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
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.google.webrtc)
    implementation(libs.okhttp)
    implementation(libs.material)
    implementation(libs.gson)
}
