package fr.labonnealerte.core.model

/**
 * Validation locale des parametres, alignee sur `validateParams` (server/params.js l.13-70),
 * pour signaler les erreurs avant l'envoi. Le serveur reste juge (commune → INSEE, etc.).
 */
object ParamValidator {
    sealed interface Issue {
        val key: String

        data class Required(override val key: String) : Issue
        data class NotAllowed(override val key: String) : Issue
        data class NotANumber(override val key: String) : Issue
        data class TooSmall(override val key: String, val min: Double) : Issue
        data class TooLarge(override val key: String, val max: Double) : Issue
        data class BadFormat(override val key: String) : Issue
    }

    /** Valeurs pretes a envoyer (nombres en Double, le reste en texte), ou la liste des problemes. */
    data class Result(val values: Map<String, Any>, val issues: List<Issue>) {
        val ok: Boolean get() = issues.isEmpty() && values.isNotEmpty()
    }

    fun validate(schema: List<ParamDescriptor>, raw: Map<String, String>): Result {
        val out = LinkedHashMap<String, Any>()
        val issues = mutableListOf<Issue>()
        for (d in schema) {
            val v = raw[d.key]?.trim().orEmpty()
            if (v.isEmpty()) {
                if (d.required) issues += Issue.Required(d.key)
                continue
            }
            when (d.type) {
                ParamType.ENUM -> if (d.options.none { it.value == v }) issues += Issue.NotAllowed(d.key) else out[d.key] = v
                ParamType.NUMBER -> {
                    val n = v.replace(',', '.').toDoubleOrNull()
                    when {
                        n == null -> issues += Issue.NotANumber(d.key)
                        d.min != null && n < d.min -> issues += Issue.TooSmall(d.key, d.min)
                        d.max != null && n > d.max -> issues += Issue.TooLarge(d.key, d.max)
                        else -> out[d.key] = n
                    }
                }
                // Le serveur resout le nom de commune (INSEE ou coordonnees) : texte libre ici.
                ParamType.COMMUNE, ParamType.COMMUNE_COORDS -> out[d.key] = v.take(120)
                else -> {
                    val cap = d.maxLength?.takeIf { it > 0 }?.coerceAtMost(500) ?: 120
                    var s = v.take(cap)
                    if (d.lowercase) s = s.lowercase()
                    val re = d.pattern?.let { runCatching { Regex(it, RegexOption.IGNORE_CASE) }.getOrNull() }
                    if (re != null && !re.containsMatchIn(s)) issues += Issue.BadFormat(d.key) else out[d.key] = s
                }
            }
        }
        return Result(out, issues)
    }
}
