package fr.labonnealerte.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KiosqueTest {
    private fun src(
        id: String,
        name: String = id,
        cats: List<String> = emptyList(),
        likes: Int = 0,
        created: String = "2026-01-01T00:00:00Z",
        type: SourceType = SourceType.INTERNAL,
        state: SourceState = SourceState.CALM,
        description: String? = null
    ) = Source(
        id, name, null, description, null, Badge.OFFICIAL, type, null, cats, emptyList(), likes, created, null, state, 0, null, 0
    )

    private val list = listOf(
        src("eau", "Coupures d'eau", listOf("eau", "coupures-eau"), likes = 5, created = "2026-03-01T00:00:00Z"),
        src("meteo", "Vigilance météo", listOf("meteo"), likes = 9, created = "2026-02-01T00:00:00Z", state = SourceState.ACTIVE),
        src("lien", "Partenaire", listOf("autre"), likes = 99, created = "2026-09-01T00:00:00Z", type = SourceType.LINKED),
        src("pollen", "Pollens", listOf("pollens"), likes = 1, created = "2026-04-01T00:00:00Z", description = "Source : Atmo France")
    )

    @Test
    fun `recherche sur le nom avec apostrophe et sans`() {
        assertEquals(listOf("eau"), Kiosque.apply(list, KiosqueFilter(query = "deau")).map { it.id })
        assertEquals(listOf("eau"), Kiosque.apply(list, KiosqueFilter(query = "d’eau")).map { it.id })
    }

    @Test
    fun `recherche dans la description et le libelle de categorie`() {
        assertEquals(listOf("pollen"), Kiosque.apply(list, KiosqueFilter(query = "atmo")).map { it.id })
        val labels = mapOf("meteo" to "Météo")
        assertEquals(listOf("meteo"), Kiosque.apply(list, KiosqueFilter(query = "meteo"), categoryLabel = { labels[it] }).map { it.id })
    }

    @Test
    fun `nouveautes excluent les sources partenaires et trient par date`() {
        val r = Kiosque.apply(list, KiosqueFilter(mode = KiosqueMode.NOUVEAUTES)).map { it.id }
        assertEquals(listOf("pollen", "eau", "meteo"), r)
    }

    @Test
    fun `populaires par likes`() {
        val ids = Kiosque.populaires(list)
        assertTrue("lien" !in ids)
        assertEquals(3, ids.size)
    }

    @Test
    fun `favoris et categorie et actives`() {
        assertEquals(
            listOf("meteo"),
            Kiosque.apply(list, KiosqueFilter(mode = KiosqueMode.FAVORIS), favorites = setOf("meteo")).map {
                it.id
            }
        )
        assertEquals(listOf("eau"), Kiosque.apply(list, KiosqueFilter(category = "coupures-eau")).map { it.id })
        assertEquals(listOf("meteo"), Kiosque.apply(list, KiosqueFilter(onlyActive = true)).map { it.id })
    }

    @Test
    fun `filtre par famille`() {
        val groups = mapOf("eau" to "energie", "coupures-eau" to "energie", "meteo" to "meteo-risques")
        assertEquals(listOf("eau"), Kiosque.apply(list, KiosqueFilter(group = "energie"), groupOf = { groups[it] }).map { it.id })
    }

    @Test
    fun `categories par frequence puis libelle`() {
        val l = listOf(src("a", cats = listOf("x", "y")), src("b", cats = listOf("y")), src("c", cats = listOf("z")))
        assertEquals(listOf("y" to 2, "x" to 1, "z" to 1), Kiosque.categoriesByFrequency(l) { it })
    }

    @Test
    fun `alertes deja suivies en fin de liste, ordre conserve (APP-08)`() {
        val sub = setOf("eau", "lien")
        assertEquals(listOf("meteo", "pollen", "eau", "lien"), Kiosque.apply(list, KiosqueFilter(), subscribed = sub).map { it.id })
        // Avec un tri : le tri s'applique dans chaque partie.
        val parNom = Kiosque.apply(list, KiosqueFilter(sort = KiosqueSort.NOM), subscribed = sub).map { it.id }
        assertEquals(listOf("pollen", "meteo", "eau", "lien"), parNom)
        // Filtre par categorie : meme regle.
        assertEquals(listOf("eau"), Kiosque.apply(list, KiosqueFilter(category = "eau"), subscribed = sub).map { it.id })
        // Sans abonnement : ordre du site inchange.
        assertEquals(listOf("eau", "meteo", "lien", "pollen"), Kiosque.apply(list, KiosqueFilter()).map { it.id })
        // Mode « Mes alertes » : rien a deplacer.
        assertEquals(listOf("eau", "lien"), Kiosque.apply(list, KiosqueFilter(mode = KiosqueMode.MES_ALERTES), subscribed = sub).map { it.id })
    }
}
