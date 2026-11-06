package fr.labonnealerte.core.ui

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Dates du serveur (ISO 8601 UTC) affichees en heure locale.
 * Motifs et langue des dates viennent des ressources (format_*, locale_dates) : ils suivent
 * la langue des textes de l'app, pas celle du systeme (sinon « 10/8/26 3:19 PM » au milieu
 * de textes francais). Une traduction n'a qu'a fournir ces trois chaines.
 * [init] est appele au demarrage et a chaque changement de configuration.
 */
object Dates {
    private var locale: Locale = Locale.FRENCH
    private var full = DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH:mm", locale)
    private var day = DateTimeFormatter.ofPattern("EEEE d MMMM", locale)
    private var longDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)

    fun init(context: Context) {
        val res = context.resources
        locale = Locale.forLanguageTag(res.getString(R.string.locale_dates))
        full = DateTimeFormatter.ofPattern(res.getString(R.string.format_date_heure), locale)
        day = DateTimeFormatter.ofPattern(res.getString(R.string.format_jour), locale)
        longDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
    }

    fun parse(iso: String?): Instant? = iso?.let { runCatching { Instant.parse(it) }.getOrNull() }

    fun full(iso: String?, zone: ZoneId = ZoneId.systemDefault()): String? = parse(iso)?.atZone(zone)?.format(full)

    fun full(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String = instant.atZone(zone).format(full)

    fun day(iso: String?, zone: ZoneId = ZoneId.systemDefault()): String? = parse(iso)?.atZone(zone)?.format(day)

    fun day(date: LocalDate): String = date.format(day)

    /** Date seule, forme longue de la langue de l'app (ex. « 8 octobre 2026 »). */
    fun long(date: LocalDate): String = date.format(longDate)
}
