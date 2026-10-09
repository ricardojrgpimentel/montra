import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Secrets stay outside version control. Relative storeFile paths are resolved
// against the properties file so a backup can be restored on another computer.
val releasePropertiesFile = rootProject.file(
    providers.environmentVariable("MONTRA_KEYSTORE_PROPERTIES")
        .getOrElse("keystore.properties"),
)
val releaseProperties = Properties().apply {
    if (releasePropertiesFile.isFile) {
        releasePropertiesFile.inputStream().use { load(it) }
    }
}
// CI can still check R8 without having access to the publication key.
val unsignedRelease = providers.gradleProperty("montraUnsignedRelease")
    .map { it.toBooleanStrict() }.getOrElse(false)
val releaseStoreFile = releaseProperties.getProperty("storeFile")
    ?.takeIf { it.isNotBlank() }
    ?.let { releasePropertiesFile.parentFile.resolve(it) }

val validateReleaseCredentials = tasks.register("validateReleaseCredentials") {
    group = "verification"
    description = "Require complete signing credentials before building a release."
    doLast {
        if (!unsignedRelease) {
            check(releasePropertiesFile.isFile) {
                "Release signing requires keystore.properties or MONTRA_KEYSTORE_PROPERTIES. See docs/RELEASE.md."
            }
            val missing = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
                .filter { releaseProperties.getProperty(it).isNullOrBlank() }
            check(missing.isEmpty()) { "Missing release signing fields: ${missing.joinToString()}." }
            check(releaseStoreFile?.isFile == true) { "The release keystore file does not exist." }
        }
    }
}
tasks.configureEach {
    if (name == "preReleaseBuild") dependsOn(validateReleaseCredentials)
}

android {
    namespace = "dev.montra"
    compileSdk = 36
    // Pinned so a build uses the build-tools already installed rather than
    // trying to download a specific version mid-build.
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "dev.montra"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The catalogue. Change this to point at a fork, a mirror or your own
        // index — nothing else in the app is coupled to this host.
        buildConfigField(
            "String",
            "DEFAULT_INDEX_URL",
            "\"https://raw.githubusercontent.com/ricardojrgpimentel/montra-index/main/index.json\"",
        )
        // A bundled snapshot ships in assets/ so the very first launch works offline.
        buildConfigField("String", "BUNDLED_INDEX_ASSET", "\"index.json\"")
    }

    signingConfigs {
        if (!unsignedRelease) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseProperties.getProperty("storePassword")
                keyAlias = releaseProperties.getProperty("keyAlias")
                keyPassword = releaseProperties.getProperty("keyPassword")
                storeType = "JKS"
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            if (!unsignedRelease) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
        )
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }

    testOptions {
        unitTests {
            // A classificação de falhas é lógica pura, mas constrói a descrição técnica
            // que aparece no log — e o log passa pelo `BuildConfig`. Sem isto, testar a
            // mensagem exigia um emulador para verificar uma frase.
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
