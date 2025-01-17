package events.boudicca.publisherhtml.service

import events.boudicca.EventCategory
import events.boudicca.SemanticKeys
import events.boudicca.search.openapi.ApiClient
import events.boudicca.search.openapi.api.SearchResourceApi
import events.boudicca.search.openapi.model.Event
import events.boudicca.search.openapi.model.QueryDTO
import events.boudicca.search.openapi.model.SearchDTO
import events.boudicca.search.openapi.model.SearchResultDTO
import org.springframework.stereotype.Service
import java.net.URLEncoder
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

//we do not want long-running events on our site, so we filter for events short then 30 days
const val DEFAULT_DURATION_SHORTER_VALUE = 24 * 30

@Service
class EventService {
    private val searchApi: SearchResourceApi = createSearchApi()
    private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'", Locale.GERMAN)

    fun search(searchDTO: SearchDTO): List<Map<String, String?>> {
        val name = searchDTO.name
        if (name != null && name.startsWith('!')) {
            return mapEvents(searchApi.queryPost(QueryDTO().query(name.substring(1)).offset(searchDTO.offset)))
        } else {
            setDefaults(searchDTO)
            return mapEvents(searchApi.searchPost(searchDTO))
        }
    }

    fun filters(): Filters {
        val filters = searchApi.filtersGet()
        return Filters(
            filters.categories.map { Pair(it, frontEndName(it)) }.sortedBy { it.second },
            filters.locationNames.sorted().map { Pair(it, it) },
            filters.locationCities.sorted().map { Pair(it, it) },
        )
    }


    private fun mapEvents(result: SearchResultDTO): List<Map<String, String?>> {
        //TODO @patzi: result contains result.totalResults, do something with it
        return result.result.map { mapEvent(it) }
    }

    private fun mapEvent(event: Event): Map<String, String?> {
        return mapOf(
            "name" to event.name,
            "description" to event.data?.get(SemanticKeys.DESCRIPTION),
            "url" to event.data?.get(SemanticKeys.URL),
            "startDate" to formatDate(event.startDate),
            "locationName" to (event.data?.get(SemanticKeys.LOCATION_NAME) ?: ""),
            "city" to event.data?.get(SemanticKeys.LOCATION_CITY),
            "category" to mapType(event.data?.get(SemanticKeys.TYPE)),
            "pictureUrl" to URLEncoder.encode(event.data?.get("pictureUrl") ?: "", Charsets.UTF_8),
        )
    }

    private fun mapType(type: String?): String? {
        val category = EventCategory.getForType(type)
        if (category != null) {
            return frontEndCategoryName(category)
        }
        return null
    }

    private fun formatDate(startDate: OffsetDateTime): String {
        return formatter.format(startDate.atZoneSameInstant(ZoneId.of("Europe/Vienna")))
    }

    private fun createSearchApi(): SearchResourceApi {
        val apiClient = ApiClient()
        apiClient.updateBaseUri(autoDetectUrl())
        return SearchResourceApi(apiClient)
    }

    private fun autoDetectUrl(): String {
        var url = System.getenv("BOUDICCA_URL")
        if (url != null && url.isNotBlank()) {
            return url
        }
        url = System.getProperty("boudiccaUrl")
        if (url != null && url.isNotBlank()) {
            return url
        }
        return "http://localhost:8082"
    }

    private fun frontEndCategoryName(type: EventCategory): String {
        return when (type) {
            EventCategory.MUSIC -> "music"
            EventCategory.ART -> "miscArt"
            EventCategory.TECH -> "tech"
        }
    }

    private fun frontEndName(type: String): String {
        return when (type) {
            "MUSIC" -> "Musik"
            "ART" -> "Kunst"
            "TECH" -> "Technologie"
            "ALL" -> "Alle"
            "OTHER" -> "Andere"
            else -> "???"
        }
    }

    private fun setDefaults(searchDTO: SearchDTO) {
        if (searchDTO.fromDate == null) {
            searchDTO.fromDate = LocalDate.now().atStartOfDay().atZone(ZoneId.of("Europe/Vienna")).toOffsetDateTime()
        }
        if (searchDTO.durationShorter == null) {
            searchDTO.durationShorter = DEFAULT_DURATION_SHORTER_VALUE.toDouble()
        }
    }

    data class Filters(
        val categories: List<Pair<String, String>>,
        val locationNames: List<Pair<String, String>>,
        val locationCities: List<Pair<String, String>>,
    )
}

