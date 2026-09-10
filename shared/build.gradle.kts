import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.android.lint)
    // Ensure the Compose Compiler plugin is applied
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21"
    id("org.jetbrains.kotlin.native.cocoapods")
}

kotlin {
    androidLibrary {
        namespace = "com.example.shared"
        compileSdk = 36
        minSdk = 24

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    val xcfName = "shared"
    val xcframework = XCFramework(xcfName)

    cocoapods {
        version = "1.0"
        summary = "Shared KMP module for maps123"
        homepage = "https://example.com/maps123"
        // Keep this in sync with iosApp/Podfile.
        ios.deploymentTarget = "15.0"
        podfile = project.file("../iosApp/Podfile")
        framework {
            baseName = xcfName
            isStatic = true
        }
        pod("GoogleMaps")
        pod("FirebaseCore")
        pod("FirebaseFirestore")
    }

    iosX64 {
        binaries.framework {
            baseName = xcfName
            xcframework.add(this)
        }
    }
    iosArm64 {
        binaries.framework {
            baseName = xcfName
            xcframework.add(this)
        }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = xcfName
            xcframework.add(this)
        }
        // Fix for C-interop module map errors with Xcode 16.4+
        binaries.all {
            linkerOpts += listOf(
                "-fmodules",
                "-fcxx-modules",
                "-suppress-warnings"
            )
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)

                // Explicit JetBrains Compose dependencies
                implementation("org.jetbrains.compose.runtime:runtime:1.7.1")
                implementation("org.jetbrains.compose.foundation:foundation:1.7.1")
                implementation("org.jetbrains.compose.material3:material3:1.7.1")
                implementation("org.jetbrains.compose.ui:ui:1.7.1")
                implementation("org.jetbrains.compose.material:material-icons-extended:1.7.1")

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        androidMain {
            dependencies {
                // Necessary for Composable functions to be recognized on Android
                implementation("androidx.compose.runtime:runtime:1.7.1")
                implementation("androidx.compose.ui:ui-tooling-preview:1.7.1")

                // Firebase for Android
                implementation(project.dependencies.platform("com.google.firebase:firebase-bom:33.7.0"))
                implementation("com.google.firebase:firebase-firestore")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
            }
        }
    }

    targets.configureEach {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
            }
        }
    }
}
