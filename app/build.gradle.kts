import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ktorfit)
    alias(libs.plugins.ghost)
}

ghost {
    version.set(libs.versions.ghost.get())
    autoInjectKtor.set(false)
}

android {
    namespace = "com.ghost.android.test"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ghost.android.test"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    buildFeatures {
        compose = true
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("src/main/AndroidManifest.xml")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xskip-prerelease-check")
    }
}

ksp {
    arg("ghost.moduleName", "app")
}

dependencies {
    // AndroidX & Lifecycle
    implementation(libs.bundles.androidx.essential)
    implementation(libs.androidx.metrics)
    
    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose.ui)

    // Benchmark Contenders & Networking
    implementation(libs.bundles.retrofit.moshi)
    implementation(libs.retrofit.gson)
    implementation(libs.bundles.ktor.moshi)
    implementation(libs.ktorfit.lib)
    ksp(libs.ktorfit.ksp)
    implementation(libs.ktor.serialization.kotlinx)
    implementation(libs.gson)
    implementation(libs.kotlinx.serialization.json)

    // Networking & Image Loading
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.mock)
}
