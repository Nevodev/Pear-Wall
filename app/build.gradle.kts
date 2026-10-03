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

// Git-derived version. The newest `v<major>.<minor>[.<patch>]` tag names the
// release and numbers it (`v1.1` -> 10100); the total commit count is added on
// top so every single commit gets a strictly larger version code, tag or not.
// VERSION_NAME / VERSION_CODE override both.
val gitDescribe = providers.exec {
    commandLine("git", "describe", "--long", "--dirty", "--tags", "--match", "v[0-9]*")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim() }

val gitCommitCount = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().toIntOrNull() ?: 1 }

// "v1.1-3-gabc123-dirty" -> name "1.1", code 10100, 3 commits past the tag.
fun parseGitVersion(describe: String, commits: Int): Triple<String, Int, Int> {
    val match = Regex("""^v(\d+)\.(\d+)(?:\.(\d+))?-(\d+)-g""").find(describe)
        ?: return Triple("1.0", 10000, 0)
    val (major, minor, patch) = match.destructured
    val name = listOf(major, minor, patch).filter { it.isNotEmpty() }.joinToString(".")
    val code = major.toInt() * 10_000 + minor.toInt() * 100 + patch.ifEmpty { "0" }.toInt()
    return Triple(name, code, match.groupValues[4].toInt().coerceAtLeast(0))
}

val gitVersion = gitDescribe.zip(gitCommitCount) { describe, commits ->
    parseGitVersion(describe, commits)
}

val appVersionName = providers.gradleProperty("VERSION_NAME")
    .orElse(providers.environmentVariable("VERSION_NAME"))
    .orElse(gitVersion.map { (name, _, sinceTag) ->
        if (sinceTag > 0) "$name-dev.$sinceTag" else name
    })
    .orElse("1.0")

val appVersionCode = providers.gradleProperty("VERSION_CODE")
    .orElse(providers.environmentVariable("VERSION_CODE"))
    .map { it.toIntOrNull() ?: 1 }
    .orElse(gitVersion.map { (_, code, _) -> code + gitCommitCount.get() })
    .orElse(1001)
    .map { it.coerceAtLeast(1) }

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
        versionCode = appVersionCode.get()
        versionName = appVersionName.get()
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

val localSdkDirectory = rootProject.file("local.properties")
    .takeIf { it.exists() }
    ?.let { propertiesFile ->
        Properties().apply { propertiesFile.inputStream().use(::load) }
            .getProperty("sdk.dir")
    }
val sdkDirectory = file(
    (localSdkDirectory
        ?: System.getenv("ANDROID_HOME")
        ?: System.getenv("ANDROID_SDK_ROOT")
        ?: error("Android SDK not found: set sdk.dir in local.properties or ANDROID_HOME"))
        .replace("\\:", ":")
)
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