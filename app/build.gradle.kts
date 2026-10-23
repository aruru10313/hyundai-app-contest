plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.aruru10313.arrivaltale"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.aruru10313.arrivaltale"
        // Pleos 공식 요구사항: Android API 28 이상 (PROGRESS.md 참조)
        minSdk = 28
        // Pleos Connect Emulator 는 API 34 이미지를 사용한다.
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Pleos Connect SDK (공식 Maven 저장소)
    // Vehicle State / NaviHelper 는 신청 없이 사용 가능한 모듈.
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    // TextToSpeech 는 현재 Playground 에서 "준비 중" 상태.
    // 코드 경로만 준비하고, 데모 기본값은 폴백 TTS 로 둔다 (PROGRESS.md 참조).
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")

    // Kotlin reflect: Vehicle SDK 가 runtime 으로 1.9.25 를 요구하므로 정렬
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.0.0")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
