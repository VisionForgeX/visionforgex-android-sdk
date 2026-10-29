import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.visionforgex.sample"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.visionforgex.sample"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        // Your app's key from its page on apps.visionforgex.com (VisionForgeX SDK panel).
        buildConfigField("String", "VFX_APP_KEY", "\"${(project.findProperty("vfxAppKey") as String?) ?: "vfx_000000000000000000000000"}\"")
        buildConfigField("String", "VFX_BASE_URL", "\"${(project.findProperty("vfxBaseUrl") as String?) ?: "https://apps.visionforgex.com"}\"")
    }
    buildFeatures { buildConfig = true }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { t -> System.getenv("VFX_MAVEN_MIRROR")?.let { t.systemProperty("robolectric.dependency.repo.url", it) }; System.getenv("VFX_SHOTS")?.let { t.systemProperty("vfx.shots", it) } }
        }
    }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":vfx-ads"))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
