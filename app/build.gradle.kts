import java.util.Properties         // <--- FIX 1: EXPLICIT IMPORT TO RESOLVE 'util'
import java.io.FileInputStream      // <--- FIX 2: EXPLICIT IMPORT TO RESOLVE 'io'

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.tech4compassion.zoralens"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.tech4compassion.zoralens"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Cleaned up properties loader utilizing the explicit script imports above
        val props = Properties()
        val localPropertiesFile = project.rootProject.file("local.properties")

        if (localPropertiesFile.exists()) {
            val inputStream = FileInputStream(localPropertiesFile)
            props.load(inputStream)
            inputStream.close()
        }

        val apiKeyStr = props.getProperty("GEMINI_API_KEY") ?: ""
        buildConfigField("String", "GEMINI_API_KEY", "\"$apiKeyStr\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
        buildConfig = true
    }
}

dependencies {
    // UI Engine
    implementation("androidx.compose.ui:ui:1.7.0")

    // Low-latency Processing Network Engine
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Core Generative AI Models
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    // Peripheral Transmit Pipeline (Xiao Hardware Comm Link)
    implementation("com.github.mik3y:usb-serial-for-android:3.7.0")

    implementation("com.github.bumptech.glide:glide:4.15.1")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.mlkit:language-id:17.0.6")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}