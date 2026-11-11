plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "my.sado.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "my.sado.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 84
        versionName = "0.43.0"
        // ML Kit ships native libs per CPU; arm64 covers modern phones and keeps the APK sideload-friendly
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    buildTypes {
        release {
            // R8 drops the parts of the libraries (Claude SDK, ML Kit, Compose) the app never calls: a much smaller APK
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // the debug key, so it installs over the debug builds already on the phone and keeps their data
            signingConfig = signingConfigs.getByName("debug")
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
    packaging {
        // compress the ML Kit native libs inside the APK: a much smaller file to sideload, unpacked once at install
        jniLibs.useLegacyPackaging = true
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/*.kotlin_module", "META-INF/versions/**")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.biometric:biometric:1.1.0")
    val camerax = "1.4.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("com.google.mlkit:pose-detection:18.0.0-beta5")
    // the body outline for the shoulder:waist ratio; pose only finds which rows are shoulders and hips
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta6")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.anthropic:anthropic-java:2.34.0")
    implementation("androidx.health.connect:connect-client:1.1.0-alpha12") // last version that builds with compileSdk 35 / AGP 8.7
    // Health Connect brings Guava at runtime only; CameraX code needs its ListenableFuture at compile time
    implementation("com.google.guava:guava:31.1-android")
}

// screenshot tests: render real screens on the JVM (Robolectric) and save PNGs with Roborazzi
android.testOptions.unitTests.isIncludeAndroidResources = true
tasks.withType<Test>().configureEach { systemProperty("roborazzi.test.record", "true") }
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.32.2")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.32.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
