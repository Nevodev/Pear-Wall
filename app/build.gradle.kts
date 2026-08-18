import java.util.Properties
import org.gradle.api.tasks.Exec

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.aboutLibraries)
}

val classicNdkVersion = "28.2.13676358"

fun signingProperty(name: String) =
    providers.gradleProperty(name).orElse(providers.environmentVariable(name))

val signingStoreFile = signingProperty("MOMENTO_SIGNING_STORE_FILE")
val signingStorePassword = signingProperty("MOMENTO_SIGNING_STORE_PASSWORD")
val signingKeyAlias = signingProperty("MOMENTO_SIGNING_KEY_ALIAS")
val signingKeyPassword = signingProperty("MOMENTO_SIGNING_KEY_PASSWORD")
val signingProperties = listOf(
    signingStoreFile,
    signingStorePassword,
    signingKeyAlias,
    signingKeyPassword,
)
val hasSigningProperties = signingProperties.any { it.isPresent }
val isSigningConfigured = signingProperties.all { it.isPresent }

check(!hasSigningProperties || isSigningConfigured) {
    "Momento signing requires all MOMENTO_SIGNING_* properties to be configured"
}

android {
    namespace = "com.nevoit.pearwall"
    compileSdk {
        version = release(37)
    }
    ndkVersion = classicNdkVersion

    defaultConfig {
        applicationId = "com.nevoit.pearwall"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        ndk {
            abiFilters.add("arm64-v8a")
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (isSigningConfigured) {
            create("momento") {
                storeFile = file(signingStoreFile.get())
                storePassword = signingStorePassword.get()
                keyAlias = signingKeyAlias.get()
                keyPassword = signingKeyPassword.get()
            }
        }
    }
    buildTypes {
        debug {
            if (isSigningConfigured) {
                signingConfig = signingConfigs.getByName("momento")
            }
        }
        release {
            if (isSigningConfigured) {
                signingConfig = signingConfigs.getByName("momento")
            }
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.shapes)
    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose.core)
    implementation(libs.backdrop)
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").inputStream().use(::load)
}
val sdkDirectory = file(localProperties.getProperty("sdk.dir").replace("\\:", ":"))
val nativeOutputDirectory = layout.buildDirectory.dir("generated/rust-jniLibs")
val buildClassicNative = tasks.register<Exec>("buildClassicNative") {
    val ndkDirectory = sdkDirectory.resolve("ndk/$classicNdkVersion")
    workingDir(rootProject.file("classic"))
    inputs.file(rootProject.file("classic/Cargo.toml"))
    inputs.file(rootProject.file("classic/Cargo.lock"))
    inputs.dir(rootProject.file("classic/src"))
    outputs.dir(nativeOutputDirectory)
    environment("ANDROID_NDK_HOME", ndkDirectory.absolutePath)
    commandLine(
        "cargo",
        "ndk",
        "-t",
        "arm64-v8a",
        "-o",
        nativeOutputDirectory.get().asFile.absolutePath,
        "build",
        "--release",
    )
}

android.sourceSets["main"].jniLibs.directories.add(
    nativeOutputDirectory.get().asFile.absolutePath,
)
tasks.named("preBuild").configure { dependsOn(buildClassicNative) }

aboutLibraries {
    collect {
        configPath = file("config")
    }
}