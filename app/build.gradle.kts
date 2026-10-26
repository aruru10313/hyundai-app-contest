import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing comes from keystore.properties (gitignored) or CI environment variables.
// Without either, the release build is left unsigned and only debug builds are installable.
val signingProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}
fun signingValue(key: String, env: String): String? =
    signingProps.getProperty(key) ?: System.getenv(env)

android {
    namespace = "io.github.aruru10313.drivecare"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.aruru10313.drivecare"
        // Pleos 공식 요구사항: Android API 28 이상 (PROGRESS.md 참조)
        minSdk = 28
        // Pleos Connect Emulator 는 API 34 이미지를 사용한다.
        targetSdk = 34
        versionCode = 3
        versionName = "1.1.0"
        // Head units are arm64; the Pleos Connect emulator is x86_64. Dropping 32-bit ABIs keeps
        // Filament's native libraries from doubling the APK.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    signingConfigs {
        val storeFilePath = signingValue("storeFile", "RELEASE_STORE_FILE")
        if (storeFilePath != null) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = signingValue("storePassword", "RELEASE_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "RELEASE_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // App Market: release signing, debugging off (default for release), shrunk with R8.
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
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
    // AAOS optional platform API; present on automotive builds, absent on phones.
    compileOnly(files("${android.sdkDirectory}/platforms/android-35/optional/android.car.jar"))

    // Pleos Connect SDK (공식 Maven 저장소)
    // Vehicle State / NaviHelper 는 신청 없이 사용 가능한 모듈.
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    // TextToSpeech / SpeechToText / FusedLocation 은 Playground 에서 "준비 중" 상태.
    // Pleos 구현을 먼저 시도하고, 실패하면 Android 기본 TTS·음성 인식·데모 위치로 폴백한다.
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")

    // Kotlin reflect: Vehicle SDK 가 runtime 으로 1.9.25 를 요구하므로 정렬
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.0.0")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // glTF car model rendering (Filament). 2.3.0 is the last line built with Kotlin 2.0.
    implementation("io.github.sceneview:sceneview:2.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}

// Contest submission: copies the signed release APK to apk/<팀명>_DriveCare_release.apk.
// Usage: ./gradlew exportContestApk -PteamName=팀명
tasks.register<Copy>("exportContestApk") {
    val team = providers.gradleProperty("teamName").orNull
        ?: throw GradleException("팀명을 지정하세요: ./gradlew exportContestApk -PteamName=팀명")
    dependsOn("assembleRelease")
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    into(rootProject.layout.projectDirectory.dir("apk"))
    rename { "${team}_DriveCare_release.apk" }
}
