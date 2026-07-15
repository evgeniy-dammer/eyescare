plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.eyecare"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.eyecare"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // ВРЕМЕННО для проверки/внутренних сборок: подписываем release debug-ключом, чтобы APK
            // устанавливался. Для публикации в Google Play заменить на настоящий release-keystore.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        // .task-модель MediaPipe нельзя сжимать — читается mmap'ом из APK на лету.
        noCompress += "task"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Jetpack Compose (главный экран)
    val composeBom = "androidx.compose:compose-bom:2024.09.00"
    implementation(platform(composeBom))
    debugImplementation(platform(composeBom))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    // Backdrop-размытие «liquid glass» (реальный блюр на Android 12+, авто-откат ниже)
    implementation("dev.chrisbanes.haze:haze:1.2.2")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // CameraX
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ML Kit Face Detection
    implementation("com.google.mlkit:face-detection:16.1.6")

    // MediaPipe Face Landmarker — прототип измерения дистанции по диаметру радужки (478 landmarks
    // с зрачками/радужкой). On-device, оффлайн. Используется только в debug-сборке для сравнения
    // с текущим IPD-методом; в release путь отключён (BuildConfig.DEBUG).
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")

    // Encrypted SharedPreferences (Jetpack Security)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // WorkManager — периодический «сторож» для восстановления мониторинга (ResumeWatchWorker)
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    testImplementation("org.mockito:mockito-core:5.10.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("org.mockito:mockito-inline:5.2.0") // Для мокирования final классов

    // Инструментальные Compose UI-тесты (требуют устройство/эмулятор для запуска).
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}