plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.code"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.code"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // Debug builds use the Firebase Local Emulator Suite on this computer
        // instead of the team's Firebase project when useFirebaseEmulator=true
        // in gradle.properties, so local testing never changes shared data.
        debug {
            val useEmulator = (project.findProperty("useFirebaseEmulator") as String?) == "true"
            val host = (project.findProperty("firebaseEmulatorHost") as String?) ?: "10.0.2.2"
            buildConfigField("boolean", "USE_FIREBASE_EMULATOR", useEmulator.toString())
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"$host\"")
        }
        release {
            buildConfigField("boolean", "USE_FIREBASE_EMULATOR", "false")
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"\"")
            optimization {
                enable = false
            }
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
}

dependencies {
    implementation(libs.zxing.core)
    implementation(libs.zxing.embedded)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.core)
    // Added for Part 4: more icons (map, tent, star, chat) and the OpenStreetMap
    // view used for campsite locations and the map WOW features (no API key needed).
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)

    // Added: supports the native date/time pickers in the lottery UI tests.
    androidTestImplementation(
        "androidx.test.espresso:espresso-contrib:" +
                libs.androidx.espresso.core.get().versionConstraint.requiredVersion
    )

    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(platform("com.google.firebase:firebase-bom:34.4.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")
}
