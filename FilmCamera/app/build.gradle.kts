plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.filmcamera"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.filmcamera"
        minSdk = 29
        targetSdk = 36
        versionCode = 6
        versionName = "0.1.5"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            // 개인 설치(사이드로드)용: 로컬 디버그 키로 서명한다. 스토어 배포 전에는 반드시 배포용 키로 바꿀 것.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    val camerax = "1.6.1"
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    implementation(platform("androidx.compose:compose-bom:2025.09.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.10.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
