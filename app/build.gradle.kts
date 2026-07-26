plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "cc.maren.deskpet"
    compileSdk = 34

    defaultConfig {
        applicationId = "cc.maren.deskpet"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "SUPABASE_URL", "\"https://idzxkhwbpxwkcvmempuc.supabase.co\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImlkenhraHdicHh3a2N2bWVtcHVjIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODI1NDY1OTMsImV4cCI6MjA5ODEyMjU5M30.HGmvwev9ZSfmStmpd2ZzXJja6DDxmAk-gHM1ZUZ--8g\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
