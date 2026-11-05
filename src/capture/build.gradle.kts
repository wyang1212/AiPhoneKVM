// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
plugins { alias(libs.plugins.android.application) }
android {
    namespace = "vip.wyang.aiphonekvm.capture"
    compileSdk { version = release(36) }
    defaultConfig {
        applicationId = providers.gradleProperty("aiphonekvm.captureApplicationId").orElse("vip.wyang.aiphonekvm.capture").get()
        minSdk = 28
        targetSdk = 36
        versionCode = providers.gradleProperty("aiphonekvm.versionCode").get().toInt()
        versionName = providers.gradleProperty("aiphonekvm.versionName").get()
    }
    buildTypes { debug { applicationIdSuffix = ".debug" } }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_1_8; targetCompatibility = JavaVersion.VERSION_1_8 }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    testImplementation(libs.junit)
}
