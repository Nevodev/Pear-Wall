plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.aboutLibraries)
}

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

aboutLibraries {
    collect {
        configPath = file("config")
    }
}