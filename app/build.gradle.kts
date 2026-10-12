plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// NO subir a 1.30 o más sin revisar: desde esa versión onnxruntime-android trae un módulo de telemetría
// que se inicia solo y pide permiso de INTERNET. La 1.23.2 no tiene código de red.
val versionOrt = "1.23.2"

android {
    namespace = "investigacion.cafehojas"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "investigacion.cafehojas"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // Teléfonos reales solamente (32 y 64 bits); sin x86 para no engordar el APK
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.all { it.maxHeapSize = "2g"; it.testLogging.showStandardStreams = true } }
}

dependencies {
    implementation("com.microsoft.onnxruntime:onnxruntime-android:$versionOrt")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    // La prueba de paridad corre el mismo Detector en el computador con el onnxruntime de escritorio
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.microsoft.onnxruntime:onnxruntime:$versionOrt")
    testImplementation("org.json:json:20240303")
}
