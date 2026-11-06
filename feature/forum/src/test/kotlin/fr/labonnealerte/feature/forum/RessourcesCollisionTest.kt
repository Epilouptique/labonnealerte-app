package fr.labonnealerte.feature.forum

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * APP-10 : deux modules qui definissent la meme chaine avec des textes differents se marchent
 * dessus a la fusion des ressources (le module app gagne). Cas trouves : « suivre » (bouton du
 * kiosque devenu « Suivre cette alerte ») et « partage_texte » (partage d'une fiche remplace par
 * le texte du partage recu). Ce controle parcourt tous les strings.xml du depot.
 */
class RessourcesCollisionTest {
    private val entry = Regex("<string name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)

    private fun root(): File {
        var f = File(System.getProperty("user.dir")).absoluteFile
        while (!File(f, "settings.gradle.kts").exists()) f = f.parentFile
        return f
    }

    @Test
    fun `aucun nom de chaine defini differemment dans deux modules`() {
        val byName = HashMap<String, MutableMap<String, String>>()
        root().walkTopDown()
            .onEnter { it.name !in setOf("build", ".gradle", ".git", "node_modules") }
            .filter { it.isFile && it.name == "strings.xml" && it.parentFile.name == "values" }
            .forEach { f ->
                val module = f.path.substringBefore("${File.separator}src${File.separator}")
                entry.findAll(f.readText()).forEach { m -> byName.getOrPut(m.groupValues[1]) { HashMap() }[module] = m.groupValues[2] }
            }
        val conflicts = byName.filterValues { it.values.toSet().size > 1 }.keys.sorted()
        assertEquals(emptyList<String>(), conflicts)
    }
}
