import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "com.visionforgex.ads.gma"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { t ->
                // where Robolectric downloads Android itself (a mirror, when set), and the platform the contract test talks to
                System.getenv("VFX_MAVEN_MIRROR")?.let { t.systemProperty("robolectric.dependency.repo.url", it) }
                System.getenv("VFX_PLATFORM")?.let { t.systemProperty("vfx.platform", it) }
                System.getenv("VFX_TEST_KEY")?.let { t.systemProperty("vfx.key", it) }
            }
        }
    }
    publishing { singleVariant("release") { withSourcesJar() } }
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        apiVersion.set(KotlinVersion.KOTLIN_2_0)
        languageVersion.set(KotlinVersion.KOTLIN_2_0)
    }
}

dependencies {
    api(project(":vfx-core"))
    api(libs.gma)
    api(libs.ump)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                artifactId = "vfx-ads"
                pom {
                    name.set("VisionForgeX SDK")
                    description.set("The VisionForgeX SDK for Android, on Google Mobile Ads: add this one.")
                    url.set("https://github.com/VisionForgeX/visionforgex-android-sdk")
                    licenses { license { name.set("Apache-2.0"); url.set("https://www.apache.org/licenses/LICENSE-2.0") } }
                }
            }
        }
    }
}
