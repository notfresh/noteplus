plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "person.notfresh.noteplus"
    compileSdk = 34

    defaultConfig {
        applicationId = "person.notfresh.noteplusv2"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.1"

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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.viewpager:viewpager:1.0.0")
    implementation("com.github.chrisbanes:PhotoView:2.3.0")
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.work:work-runtime:2.8.1")

    // Lucene 核心
    implementation("org.apache.lucene:lucene-core:9.10.0")
    implementation("org.apache.lucene:lucene-analysis-common:9.10.0")

    // IK Analyzer Android 版 (来自 jitpack)
    implementation("com.github.magese:ik-analyzer-android:1.0.5")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}