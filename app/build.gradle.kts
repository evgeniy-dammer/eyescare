plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.eyescare"
    // compileSdk 37 требуют свежие androidx (core-ktx 1.19, compose 1.12, lifecycle 2.11).
    // targetSdk намеренно оставлен на 36: его подъём меняет runtime-поведение и требует
    // отдельной проверки на устройстве.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.eyescare"
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
    implementation("androidx.core:core-ktx:1.19.0")
    // appcompat: AppCompatActivity + AppCompatDelegate (per-app locales на Android < 13)
    implementation("androidx.appcompat:appcompat:1.8.0")
    // material (View-компоненты): используется только в overlay_layout.xml (MaterialButton)
    implementation("com.google.android.material:material:1.14.0")

    // Jetpack Compose (главный экран)
    val composeBom = "androidx.compose:compose-bom:2026.08.00"
    implementation(platform(composeBom))
    debugImplementation(platform(composeBom))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.10.0")
    // Backdrop-размытие «liquid glass» (реальный блюр на Android 12+, авто-откат ниже)
    implementation("dev.chrisbanes.haze:haze:1.7.3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // CameraX
    val cameraxVersion = "1.6.2"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ML Kit Face Detection
    implementation("com.google.mlkit:face-detection:16.1.7")

    // MediaPipe Face Landmarker — прототип измерения дистанции по диаметру радужки (478 landmarks
    // с зрачками/радужкой). On-device, оффлайн. Используется только в debug-сборке для сравнения
    // с текущим IPD-методом; в release путь отключён (BuildConfig.DEBUG).
    implementation("com.google.mediapipe:tasks-vision:1.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Lifecycle
    val lifecycleVersion = "2.11.0"
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-service:$lifecycleVersion")

    // Encrypted SharedPreferences (Jetpack Security).
    // ВНИМАНИЕ: в 1.1.0 (первый стабильный релиз) EncryptedSharedPreferences помечен @Deprecated —
    // AndroidX закрыл библиотеку и рекомендует обычные SharedPreferences (данные и так защищены
    // file-based encryption устройства). Держим её осознанно: API рабочий и стабильный, а отказ от
    // шифрования — продуктовое решение (затрагивает README/privacy policy). См. TECHNICAL_TASK.md.
    implementation("androidx.security:security-crypto:1.1.0")

    // WorkManager — периодический «сторож» для восстановления мониторинга (ResumeWatchWorker)
    implementation("androidx.work:work-runtime-ktx:2.11.2")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    // mockito-core >= 5.3 использует inline mock maker по умолчанию — отдельный
    // артефакт mockito-inline больше не нужен.
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.3.0")

    // Инструментальные Compose UI-тесты (требуют устройство/эмулятор для запуска).
    androidTestImplementation(platform(composeBom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
