import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import java.util.Properties

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun publicProperty(name: String): String =
    localProperties.getProperty(name)
        ?: providers.environmentVariable(name).orNull
        ?: ""

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.android.lint)
    alias(libs.plugins.buildkonfig)
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21"
    id("org.jetbrains.kotlin.native.cocoapods")
}

buildkonfig {
    packageName = "com.example.shared.data"

    defaultConfigs {
        buildConfigField(STRING, "SUPABASE_URL", publicProperty("SUPABASE_URL"))
        buildConfigField(STRING, "SUPABASE_PUBLISHABLE_KEY", publicProperty("SUPABASE_PUBLISHABLE_KEY"))
        buildConfigField(STRING, "BACKEND_URL", publicProperty("BACKEND_URL"))
    }
}

kotlin {
    androidLibrary {
        namespace = "com.example.shared"
        compileSdk = 36
        minSdk = 24

        // Run commonTest on the JVM as part of normal CI. Previously this
        // project only configured device tests, so shared business rules had
        // no executable host-side test target.
        withHostTestBuilder {}.configure {}

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
        binaries.all {
            linkerOpts += listOf("-fmodules", "-fcxx-modules", "-suppress-warnings")
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation("org.jetbrains.compose.runtime:runtime:1.7.1")
                implementation("org.jetbrains.compose.foundation:foundation:1.7.1")
                implementation("org.jetbrains.compose.material3:material3:1.7.1")
                implementation("org.jetbrains.compose.ui:ui:1.7.1")
                implementation("org.jetbrains.compose.material:material-icons-extended:1.7.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

                implementation("io.github.jan-tennert.supabase:auth-kt:3.5.0")
                implementation("io.github.jan-tennert.supabase:postgrest-kt:3.5.0")
                implementation("io.github.jan-tennert.supabase:realtime-kt:3.5.0")
                implementation("io.github.jan-tennert.supabase:storage-kt:3.5.0")
                implementation("io.ktor:ktor-client-core:3.0.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        androidMain {
            dependencies {
                implementation("androidx.compose.runtime:runtime:1.7.1")
                implementation("androidx.compose.ui:ui-tooling-preview:1.7.1")
                implementation("io.ktor:ktor-client-cio:3.0.3")
            }
        }

        iosMain {
            dependencies {
                implementation("io.ktor:ktor-client-darwin:3.0.3")
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

val verifyKmpArchitecture by tasks.registering {
    group = "verification"
    description = "Checks that commonMain contains portable code and repository interfaces, not platform implementations."
    doLast {
        val commonSource = project.file("src/commonMain/kotlin")
        val violations = mutableListOf<String>()
        commonSource.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { source ->
            source.readLines().forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (Regex("expect\\s+class\\s+\\w*Repository").containsMatchIn(trimmed)) {
                    violations += "${source.relativeTo(projectDir)}:${index + 1}: repositories must be common interfaces"
                }
                if (Regex("import\\s+(android\\.|java\\.|platform\\.|cocoapods\\.)").containsMatchIn(trimmed)) {
                    violations += "${source.relativeTo(projectDir)}:${index + 1}: platform import in commonMain"
                }
                if (trimmed.startsWith("import com.google.firebase")) {
                    violations += "${source.relativeTo(projectDir)}:${index + 1}: Firebase import in commonMain"
                }
            }
        }
        check(violations.isEmpty()) {
            "KMP architecture violations:\n${violations.joinToString("\n")}"
        }
    }
}

tasks.matching { it.name == "check" }.configureEach {
    dependsOn(verifyKmpArchitecture)
}
