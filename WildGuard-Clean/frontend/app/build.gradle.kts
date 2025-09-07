plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.wildguard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.wildguard"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Core AndroidX + Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.runtime.saved.instance.state)
    implementation(libs.androidx.navigation.compose.android)

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.13.0"))
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.auth.ktx)
    implementation("com.google.firebase:firebase-messaging:23.4.1")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")

    // Maps & Location
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    implementation("org.osmdroid:osmdroid-android:6.1.16")
    implementation("org.osmdroid:osmdroid-wms:6.1.16")

    // Images
    implementation("com.squareup.picasso:picasso:2.8")
    implementation("com.github.bumptech.glide:glide:4.15.1")
    implementation("io.coil-kt:coil-compose:2.4.0")

    // Material
    implementation("com.google.android.material:material:1.10.0")

    // Notifications & vibration
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")

    // Testing
    testImplementation("junit:junit:4.13.2") // Updated JUnit
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // ❌ Removed libs.testng to avoid duplicate Hamcrest classes
    // If you REALLY need TestNG, use:
    // testImplementation("org.testng:testng:7.9.0") {
    //     exclude(group = "org.hamcrest", module = "hamcrest-core")
    // }
}
