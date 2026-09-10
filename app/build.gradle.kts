import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun supabaseBuildProperty(name: String): String =
    localProperties.getProperty(name)
        ?: providers.environmentVariable(name).orNull
        ?: ""

fun asBuildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.gms.google-services")
}
android {
    namespace = "com.example.maps123"
    compileSdk = 36 // Changed to 35 (Standard for current tools)

    defaultConfig {
        applicationId = "com.example.maps123"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Public client values only.  Keep SUPABASE_SERVICE_ROLE_KEY on Render.
        buildConfigField(
            "String",
            "SUPABASE_URL",
            asBuildConfigString(supabaseBuildProperty("SUPABASE_URL"))
        )
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            asBuildConfigString(supabaseBuildProperty("SUPABASE_PUBLISHABLE_KEY"))
        )
    }

    packaging {
        jniLibs {
            // Use modern native library packaging for newer Android/Play requirements,
            // including 16 KB page-size compatibility checks.
            useLegacyPackaging = false
        }
    }

    buildTypes {
        debug {
            // Keep emulator-friendly defaults for local development.
        }
        release {
            isMinifyEnabled = false
            ndk {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17 // Updated to 17 for better Compose support
        targetCompatibility = JavaVersion.VERSION_17
        // supabase-kt supports minSdk 24 when Java APIs are desugared.
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // Core & UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended:1.7.6")
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    // Keep runtime and instrumentation-test classpaths on the same AndroidX
    // concurrent version (Espresso 3.7.0 requires 1.2.0).
    implementation("androidx.concurrent:concurrent-futures:1.2.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation(project(":shared"))
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Firebase Messaging is retained only to receive push notifications.
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation(libs.ui)

    // Room
    val roomVersion = "2.8.4"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Maps
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("com.google.maps.android:maps-compose:4.4.1")

    // Retrofit & OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation(platform("io.github.jan-tennert.supabase:bom:3.5.0"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")

    // Ktor client for Android
    implementation("io.ktor:ktor-client-android:3.0.3")
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
