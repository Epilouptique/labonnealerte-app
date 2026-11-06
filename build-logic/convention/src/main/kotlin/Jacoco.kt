import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.testing.jacoco.tasks.JacocoReport

/** Classes exclues du calcul : code genere (Hilt/Dagger, kotlinx.serialization), BuildConfig, R, modules DI. */
internal val coverageExcludes = listOf(
    "**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*",
    "**/*_Factory*", "**/*_HiltModules*", "**/Hilt_*", "**/*_MembersInjector*",
    "**/*Module*", "**/*\$\$serializer*", "**/hilt_aggregated_deps/**", "**/dagger/**",
)

/**
 * Couverture de lignes JaCoCo.
 * - module JVM : tache standard `jacocoTestReport` (apres `test`) ;
 * - module Android : `jacocoTestReport` sur `testDebugUnitTest` (enableUnitTestCoverage du debug).
 */
class JacocoConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("jacoco")
        pluginManager.withPlugin("com.android.library") {
            extensions.configure<LibraryExtension> {
                buildTypes.getByName("debug").enableUnitTestCoverage = true
            }
            tasks.withType(Test::class.java).configureEach {
                // Robolectric charge des classes JDK : ne pas les instrumenter.
                extensions.findByType(org.gradle.testing.jacoco.plugins.JacocoTaskExtension::class.java)?.apply {
                    isIncludeNoLocationClasses = true
                    excludes = listOf("jdk.internal.*")
                }
            }
            tasks.register<JacocoReport>("jacocoTestReport") {
                group = "verification"
                description = "Rapport de couverture des tests unitaires debug."
                dependsOn("testDebugUnitTest")
                val build = layout.buildDirectory
                classDirectories.setFrom(
                    build.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")
                        .map { fileTree(it) { exclude(coverageExcludes) } },
                    build.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes")
                        .map { fileTree(it) { exclude(coverageExcludes) } },
                )
                sourceDirectories.setFrom(files("src/main/kotlin", "src/main/java"))
                executionData.setFrom(
                    build.dir("outputs/unit_test_code_coverage/debugUnitTest")
                        .map { fileTree(it) { include("*.exec") } },
                )
                reports {
                    xml.required.set(true)
                    html.required.set(true)
                }
            }
        }
        pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            tasks.named<JacocoReport>("jacocoTestReport") {
                dependsOn("test")
                classDirectories.setFrom(
                    classDirectories.files.map { fileTree(it) { exclude(coverageExcludes) } },
                )
                reports {
                    xml.required.set(true)
                    html.required.set(true)
                }
            }
        }
    }
}
