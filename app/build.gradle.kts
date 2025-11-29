plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Add the Google services Gradle plugin
    id("com.google.gms.google-services")
    // Add the Crashlytics Gradle plugin
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.speedlimit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.speedlimit"
        minSdk = 26
        targetSdk = 35
        versionCode = 24
        versionName = "3.3"
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
        viewBinding = true
    }

    applicationVariants.all {
        outputs.all {
            val outputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            outputImpl.outputFileName = "SpeedLimit-${versionName}.apk"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // FlexboxLayout for dynamic speed limit grid
    implementation("com.google.android.flexbox:flexbox:3.0.0")
    
    // Splash screen
    implementation("androidx.core:core-splashscreen:1.0.1")
    
    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // OkHttp for API calls
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // JSON parsing
    implementation("org.json:json:20231013")
    
    // Location services
    implementation("com.google.android.gms:play-services-location:21.0.1")
    
    // Firebase - Import the BoM for version management
    implementation(platform("com.google.firebase:firebase-bom:34.6.0"))
    
    // Firebase Analytics - for event tracking and monitoring
    implementation("com.google.firebase:firebase-analytics")
    
    // Firebase Crashlytics - for crash and error reporting
    implementation("com.google.firebase:firebase-crashlytics")
}

