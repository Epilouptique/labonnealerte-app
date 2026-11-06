import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        extensions.configure<ApplicationExtension> {
            configureAndroidCommon(this)
            defaultConfig.targetSdk = Sdk.TARGET
        }
    }
}

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            configureAndroidCommon(this)
            defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        dependencies {
            add("testImplementation", lib("junit"))
            add("testImplementation", lib("kotlinx-coroutines-test"))
            add("testImplementation", lib("turbine"))
        }
    }
}

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        val android = extensions.getByName("android") as CommonExtension
        android.buildFeatures.compose = true
        dependencies {
            val bom = platform(lib("compose-bom"))
            add("implementation", bom)
            add("testImplementation", bom)
            add("implementation", lib("compose-ui"))
            add("implementation", lib("compose-foundation"))
            add("implementation", lib("compose-material3"))
            add("implementation", lib("compose-material-icons"))
            add("implementation", lib("compose-ui-tooling-preview"))
            add("debugImplementation", lib("compose-ui-tooling"))
        }
    }
}

/** Module d'ecran : bibliotheque Android + Compose + Hilt + dependances communes. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("lba.android.library")
        pluginManager.apply("lba.android.compose")
        pluginManager.apply("lba.hilt")
        dependencies {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:data"))
            add("implementation", project(":core:ui"))
            add("implementation", lib("androidx-lifecycle-runtime-compose"))
            add("implementation", lib("androidx-lifecycle-viewmodel-compose"))
            add("implementation", lib("androidx-hilt-navigation-compose"))
            add("implementation", lib("androidx-navigation-compose"))
            add("implementation", lib("kotlinx-coroutines-android"))
            add("implementation", lib("timber"))
        }
    }
}

class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")
        dependencies {
            add("implementation", lib("hilt-android"))
            add("ksp", lib("hilt-compiler"))
        }
    }
}

/** Module Kotlin pur (aucune dependance Android) : portable vers iOS/KMP plus tard. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        configureKotlin()
        dependencies {
            add("testImplementation", lib("junit"))
            add("testImplementation", lib("kotlinx-coroutines-test"))
        }
    }
}
