package fr.labonnealerte.core.data

import fr.labonnealerte.core.database.CategoryEntity
import fr.labonnealerte.core.database.SourceEntity
import fr.labonnealerte.core.model.Badge
import fr.labonnealerte.core.model.Category
import fr.labonnealerte.core.model.DayStatus
import fr.labonnealerte.core.model.Departement
import fr.labonnealerte.core.model.EventKind
import fr.labonnealerte.core.model.Geo
import fr.labonnealerte.core.model.GeoItem
import fr.labonnealerte.core.model.HistoryDay
import fr.labonnealerte.core.model.HistoryEvent
import fr.labonnealerte.core.model.LePoint
import fr.labonnealerte.core.model.LePointActive
import fr.labonnealerte.core.model.LePointUpcoming
import fr.labonnealerte.core.model.ParamDescriptor
import fr.labonnealerte.core.model.ParamOption
import fr.labonnealerte.core.model.ParamType
import fr.labonnealerte.core.model.Source
import fr.labonnealerte.core.model.SourceHistory
import fr.labonnealerte.core.model.SourceState
import fr.labonnealerte.core.model.SourceType
import fr.labonnealerte.core.network.LbaJson
import fr.labonnealerte.core.network.dto.CategoryDto
import fr.labonnealerte.core.network.dto.GeoDto
import fr.labonnealerte.core.network.dto.HistoryDto
import fr.labonnealerte.core.network.dto.LePointDto
import fr.labonnealerte.core.network.dto.ParamDto
import fr.labonnealerte.core.network.dto.SourceDto
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

private val paramListSerializer = ListSerializer(ParamDescriptor.serializer())

internal fun ParamDto.toDomain(): ParamDescriptor = ParamDescriptor(
    key = key,
    label = label,
    type = when (type) {
        "enum" -> ParamType.ENUM
        "string" -> ParamType.STRING
        "number" -> ParamType.NUMBER
        "commune" -> ParamType.COMMUNE
        "commune-coords" -> ParamType.COMMUNE_COORDS
        "dynamic-enum" -> ParamType.DYNAMIC_ENUM
        else -> ParamType.UNKNOWN
    },
    options = values.orEmpty().map { ParamOption(it.value, it.label) },
    multiple = multiple,
    required = required,
    default = when (val d = default) {
        null, JsonNull -> null
        is JsonPrimitive -> d.content
        is JsonArray -> d.joinToString(",") { (it as? JsonPrimitive)?.content.orEmpty() }
        else -> null
    },
    min = min,
    max = max,
    maxLength = maxLength,
    pattern = pattern,
    lowercase = lowercase,
    placeholder = placeholder,
    hint = hint
)

internal fun SourceDto.toEntity(position: Int) = SourceEntity(
    id = id,
    name = name,
    subtitle = subtitle,
    description = description,
    descriptionLong = descriptionLong,
    badge = badge,
    type = type,
    linkUrl = linkUrl,
    categories = categories.joinToString(","),
    paramsJson = LbaJson.encodeToString(paramListSerializer, paramsSchema.orEmpty().map { it.toDomain() }),
    likesCount = likesCount,
    createdAt = createdAt,
    forumSlug = forumSlug,
    state = state,
    subscriberCount = subscriberCount,
    lastActivatedAt = lastActivatedAt,
    topicCount = topicCount,
    position = position
)

internal fun serverState(state: String?, type: String?): SourceState = when {
    type == "linked" || state == null -> SourceState.NONE
    state == "active" -> SourceState.ACTIVE
    else -> SourceState.CALM // inactive, ou pending (pas encore confirme)
}

internal fun SourceEntity.toDomain() = Source(
    id = id,
    name = name,
    subtitle = subtitle,
    description = description,
    descriptionLong = descriptionLong,
    badge = when (badge) {
        "official" -> Badge.OFFICIAL
        "verified" -> Badge.VERIFIED
        "community" -> Badge.COMMUNITY
        else -> Badge.UNKNOWN
    },
    type = when (type) {
        "internal" -> SourceType.INTERNAL
        "external" -> SourceType.EXTERNAL
        "linked" -> SourceType.LINKED
        "user-task" -> SourceType.USER_TASK
        "community" -> SourceType.COMMUNITY
        else -> SourceType.UNKNOWN
    },
    linkUrl = linkUrl,
    categories = categories.split(',').filter { it.isNotBlank() },
    params = runCatching { LbaJson.decodeFromString(paramListSerializer, paramsJson) }.getOrDefault(emptyList()),
    likesCount = likesCount,
    createdAt = createdAt,
    forumSlug = forumSlug,
    state = serverState(state, type),
    subscriberCount = subscriberCount,
    lastActivatedAt = lastActivatedAt,
    topicCount = topicCount
)

internal fun CategoryDto.toEntity(position: Int) = CategoryEntity(slug, label, group, position)

internal fun CategoryEntity.toDomain() = Category(slug, label, group)

internal fun eventKind(e: String) = when (e) {
    "activated" -> EventKind.ACTIVATED
    "deactivated" -> EventKind.DEACTIVATED
    "failed" -> EventKind.FAILED
    else -> EventKind.UNKNOWN
}

internal fun HistoryDto.toDomain() = SourceHistory(
    events = events.map { HistoryEvent(eventKind(it.event), it.message, it.createdAt) },
    days = days.map {
        HistoryDay(
            it.date,
            when (it.status) {
                "active" -> DayStatus.ACTIVE
                "failed" -> DayStatus.FAILED
                "calm" -> DayStatus.CALM
                else -> DayStatus.NODATA
            }
        )
    }
)

internal fun LePointDto.toDomain() = LePoint(
    generatedAt = generatedAt,
    active = active.map {
        LePointActive(
            it.id,
            it.name,
            it.message,
            it.url,
            it.since,
            if (it.kind ==
                "param"
            ) {
                it.count
            } else {
                null
            },
            it.labels
        )
    },
    upcoming = upcoming.map { LePointUpcoming(it.id, it.start, it.message, it.url) }
)

internal fun GeoDto.toDomain() = Geo(
    countries = countries.map { GeoItem(it.code, it.name) },
    departements = departements.map { Departement(it.code, it.name, it.region) },
    regions = regions.map { GeoItem(it.code, it.name) }
)
