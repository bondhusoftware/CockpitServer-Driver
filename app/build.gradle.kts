plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.bondhu.cockpitserver"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bondhu.cockpitserver"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("persistent") {
            storeFile = file("debug-persistent.keystore")
            storePassword = "cockpitserver"
            keyAlias = "cockpitserver"
            keyPassword = "cockpitserver"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("persistent")
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Retrofit + OkHttp (server API)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Socket.IO (job push — SimSupport protocol)
    implementation("io.socket:socket.io-client:2.1.0")
    // AndroidX (notifications)
    implementation("androidx.core:core:1.13.1")
}
