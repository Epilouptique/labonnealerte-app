import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun Project.lib(alias: String) = libs.findLibrary(alias).get()

internal object Sdk {
    const val COMPILE = 37
    const val TARGET = 37
    const val MIN = 26
}

internal fun Project.configureAndroidCommon(ext: CommonExtension) {
    ext.compileSdk = Sdk.COMPILE
    ext.defaultConfig.minSdk = Sdk.MIN
    ext.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    ext.compileOptions.targetCompatibility = JavaVersion.VERSION_17
    ext.lint.abortOnError = true
    ext.lint.checkDependencies = false
    // local.properties est genere par la machine, jamais livre ; versions epinglees volontairement (catalogue).
    ext.lint.disable += setOf("PropertyEscape", "NewerVersionAvailable", "GradleDependency", "AndroidGradlePluginVersion")
    ext.testOptions.unitTests.isIncludeAndroidResources = true
    ext.testOptions.unitTests.isReturnDefaultValues = true
    configureKotlin()
}

internal fun Project.configureKotlin() {
    // Certains modules n'ont pas encore de tests : ne pas echouer.
    tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach { failOnNoDiscoveredTests.set(false) }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }
}
