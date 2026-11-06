package fr.labonnealerte.feature.forum

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * APP-10 : les rubriques du forum affichaient des signes etranges sur le telephone. Les emojis
 * du code avaient ete doublement encodes (UTF-8 relu en Windows-1252 puis reecrit en UTF-8) par
 * un script d'edition (commit 614bb26). Ce controle parcourt TOUT le depot (code, ressources,
 * Gradle, documentation) : aucune sequence typique de double encodage, et aucun BOM.
 */
class EncodageSourcesTest {
    // Debuts typiques : emoji, accent, point median / guillemets, tiret / apostrophe, symbole.
    private val mojibake = Regex("\u00f0\u0178|\u00c3[\u0080-\u00bf]|\u00c2[\u00a0-\u00bf]|\u00e2\u20ac|\u00e2\u0153")
    private val extensions = setOf("kt", "kts", "xml", "md", "toml", "properties", "json", "js")

    private fun root(): File {
        var f = File(System.getProperty("user.dir")).absoluteFile
        while (!File(f, "settings.gradle.kts").exists()) f = f.parentFile
        return f
    }

    private fun files(): List<File> = root().walkTopDown()
        .onEnter { it.name !in setOf("build", ".gradle", ".git", "node_modules", ".idea") }
        .filter { it.isFile && it.extension in extensions }
        .toList()

    @Test
    fun `aucun texte doublement encode dans le depot`() {
        val all = files()
        assertTrue("fichiers trouves", all.size > 100)
        val bad = all.flatMap { f ->
            f.readLines(Charsets.UTF_8).mapIndexedNotNull { i, l -> if (mojibake.containsMatchIn(l)) "${f.name}:${i + 1}" else null }
        }
        assertEquals(emptyList<String>(), bad)
    }

    @Test
    fun `fichiers en UTF-8 sans BOM`() {
        val bom = files().filter { f ->
            val b = f.inputStream().use { s -> ByteArray(3).also { s.read(it) } }
            b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()
        }.map { it.name }
        assertEquals(emptyList<String>(), bom)
    }

    @Test
    fun `chaque rubrique a une icone, distincte`() {
        val slugs = listOf("discussion", "propositions", "dev", "entraide", "suggestions")
        assertEquals(slugs.size, slugs.map { categoryIcon(it) }.toSet().size)
    }
}
