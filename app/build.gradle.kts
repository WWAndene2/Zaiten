plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.zaiten.mixer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zaiten.mixer"
        // AudioPlaybackCapture n'existe qu'à partir d'Android 10 (API 29).
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }

    ndkVersion = "27.2.12479018"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        prefab = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("com.google.oboe:oboe:1.9.0")
    // Lecteur YouTube officiel (IFrame) dans une vue Android, pour la synchro média.
    implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:12.1.0")
    testImplementation("junit:junit:4.13.2")
    // org.json d'Android n'est qu'un bouchon dans les tests JVM : implémentation réelle pour SettingsJsonTest.
    testImplementation("org.json:json:20240303")
}
