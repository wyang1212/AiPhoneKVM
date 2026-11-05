// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
import java.util.Properties
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

fun serviceOrigin(property: String, fallback: String, optional: Boolean = false): String {
    val value = providers.gradleProperty(property).orElse(fallback).get().trim().removeSuffix("/")
    if (optional && value.isEmpty()) return ""
    val uri = URI(value)
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
        !uri.rawAuthority.endsWith(":") && uri.rawPath.isNullOrEmpty() && uri.rawQuery == null && uri.rawFragment == null &&
        (uri.port == -1 || uri.port in 1..65535)) { "$property must be an HTTPS origin" }
    return URI("https", null, uri.host.lowercase(), if (uri.port == 443) -1 else uri.port, null, null, null).toASCIIString()
}
// Self-instrumenting audit APK avoids installing a second test package on restrictive devices.
val embedAuditTests = providers.gradleProperty("aiphonekvm.embedAuditTests").orElse("false").get().toBoolean()
val auditApplicationId = providers.gradleProperty("aiphonekvm.applicationId").orElse("vip.wyang.aiphonekvm").get()
require(!embedAuditTests || auditApplicationId == "org.example.aiphonekvm.audit") {
    "Embedded screenshot tests are allowed only in the independent audit application"
}

val websiteOrigin = serviceOrigin("aiphonekvm.websiteOrigin", "https://controller.example.invalid")
val updateOrigin = serviceOrigin("aiphonekvm.updateOrigin", "https://downloads.example.invalid", optional = true)

android {
    namespace = "com.atharok.btremote"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = providers.gradleProperty("aiphonekvm.applicationId").orElse("vip.wyang.aiphonekvm").get()
        minSdk = 28
        targetSdk = 36
        versionCode = providers.gradleProperty("aiphonekvm.versionCode").get().toInt()
        versionName = providers.gradleProperty("aiphonekvm.versionName").get()
        buildConfigField("String", "WEBSITE_ORIGIN", "\"$websiteOrigin\"")
        buildConfigField("String", "UPDATE_ORIGIN", "\"$updateOrigin\"")
        manifestPlaceholders["websiteHost"] = URI(websiteOrigin).host

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    if (embedAuditTests) {
        sourceSets.getByName("debug").kotlin.srcDir(layout.buildDirectory.dir("generated/audit-sources").get().asFile)
        sourceSets.getByName("debug").manifest.srcFile("src/audit/AndroidManifest.xml")
    }

    signingConfigs {
        create("release") {
            localProps.getProperty("storeFile")?.let { storeFile = file(it) }
            localProps.getProperty("storePassword")?.let { storePassword = it }
            localProps.getProperty("keyAlias")?.let { keyAlias = it }
            localProps.getProperty("keyPassword")?.let { keyPassword = it }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".debug"
        }
        release {
            isDebuggable = false
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    flavorDimensions += "version"
    productFlavors {
        create("default") {
            dimension = "version"
            signingConfig = signingConfigs.getByName("release")
        }
        create("gplay") {
            dimension = "version"
            applicationIdSuffix = ".gplay"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    lint {
        // Existing upstream translations and Compose resource lookups, kept outside this feature.
        baseline = file("lint-baseline.xml")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            keepDebugSymbols += "**/*.so"
        }
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }
}

if (embedAuditTests) {
    val prepareAuditSources = tasks.register<Copy>("preparePublicAuditSources") {
        from("src/androidTest/java/com/atharok/btremote/PublicPageScreenshotTest.kt")
        into(layout.buildDirectory.dir("generated/audit-sources"))
    }
    tasks.matching { it.name == "preDefaultDebugBuild" }.configureEach { dependsOn(prepareAuditSources) }
}

dependencies {
    if (embedAuditTests) {
        debugImplementation(libs.androidx.junit)
        debugImplementation(libs.androidx.espresso.core)
        debugImplementation(libs.androidx.ui.test.junit4)
        debugImplementation("androidx.test:runner:1.7.0")
    }
    implementation("sh.calvin.reorderable:reorderable:3.1.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.koin.androidx.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
