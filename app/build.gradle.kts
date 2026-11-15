plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// ───────────────────────────────────────────────────────────────
// 이름이 확정되면 아래 두 변수와 res/values/strings.xml 의 app_name 만 바꾸면 된다.
// (소스 패키지명 app.carwell.drive 는 namespace 로 고정, 바꿀 필요 없음)
val appId = "app.everyway.drive"    // 임시 applicationId
val apkBaseName = "김성준_에브리웨이"       // 임시 산출물 파일명: <apkBaseName>_release.apk
// ───────────────────────────────────────────────────────────────

android {
    namespace = "app.carwell.drive"
    compileSdk = 35

    defaultConfig {
        applicationId = appId
        minSdk = 28            // Pleos 요구사항
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            // 공모전 임시 산출물: debug 키로 서명 (정식 제출 전 교체)
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    // Pleos Connect SDK (문서 기준 좌표)
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:vehicle-data:1.1.11")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

// assembleRelease 후 app/apk/ 로 복사 (프로젝트 루트 기준 apk/)
val copyReleaseApk by tasks.registering(Copy::class) {
    from(layout.buildDirectory.dir("outputs/apk/release")) { include("*.apk") }
    into(rootProject.layout.projectDirectory.dir("apk"))
    rename { "${apkBaseName}_release.apk" }
}
afterEvaluate {
    tasks.named("assembleRelease") { finalizedBy(copyReleaseApk) }
}
