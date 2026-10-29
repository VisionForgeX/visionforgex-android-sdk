import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "com.visionforgex.ads"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "VFX_VERSION", "\"${project.version}\"")
    }
    buildFeatures { buildConfig = true }
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
        // apps on Kotlin 2.0 or newer can use it
        apiVersion.set(KotlinVersion.KOTLIN_2_0)
        languageVersion.set(KotlinVersion.KOTLIN_2_0)
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                artifactId = "vfx-core"
                pom {
                    name.set("VisionForgeX SDK core")
                    description.set("Settings, pacing, preloading, floors and events for apps monetized with VisionForgeX.")
                    url.set("https://github.com/VisionForgeX/visionforgex-android-sdk")
                    licenses { license { name.set("Apache-2.0"); url.set("https://www.apache.org/licenses/LICENSE-2.0") } }
                }
            }
        }
    }
}
