package com.nuvio.tv.data.local

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nuvio.tv.core.profile.ProfileManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Restyle 3 (2026-11-19): the user's own rating of one title. */
enum class NotflixUserRating { DISLIKE, LIKE, LOVE }

/**
 * Restyle 3: one title the user rated (👎 👍 ❤️) or whose trailer he
 * watched — stored with its own snapshot (name/poster/backdrop) so My
 * Space can render the row without resolving the title again. Per
 * profile, like the search history. `rating` is null for trailer entries.
 */
data class NotflixTasteEntry(
    val key: String,
    val type: String,
    val name: String,
    val poster: String?,
    val backdrop: String?,
    val rating: String?,
    val at: Long
)

/**
 * Restyle 3: the per-profile taste store — ratings (👎 👍 ❤️, set on the
 * detail screen) and the trailers the user watched (logged when a trailer
 * starts playing on the home cards or the detail screen). Feeds the
 * «أعجبتك» and «المقدمات التي شاهدتها» sections of My Space. Local only:
 * nothing leaves the device, and each profile keeps its own.
 */
@Singleton
class NotflixTasteDataStore @Inject constructor(
    private val factory: ProfileDataStoreFactory,
    private val profileManager: ProfileManager
) {
    companion object {
        private const val FEATURE = "notflix_taste"
        private const val MAX_RATED = 50
        private const val MAX_TRAILERS = 20
    }

    private fun store(profileId: Int = profileManager.activeProfileId.value) =
        factory.get(profileId, FEATURE)

    private val gson = Gson()
    private val ratedKey = stringPreferencesKey("rated_titles")
    private val trailersKey = stringPreferencesKey("trailer_history")
    private val entryListType = object : TypeToken<List<NotflixTasteEntry>>() {}.type

    private fun parse(raw: String?): List<NotflixTasteEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { gson.fromJson<List<NotflixTasteEntry>>(raw, entryListType) ?: emptyList() }
            .getOrDefault(emptyList())
    }

    val ratedTitles: Flow<List<NotflixTasteEntry>> = profileManager.activeProfileId.flatMapLatest { profileId ->
        factory.get(profileId, FEATURE).data.map { prefs -> parse(prefs[ratedKey]) }
    }

    val trailerHistory: Flow<List<NotflixTasteEntry>> = profileManager.activeProfileId.flatMapLatest { profileId ->
        factory.get(profileId, FEATURE).data.map { prefs -> parse(prefs[trailersKey]) }
    }

    /** The user's rating for one title key ("type:id"), or null. */
    suspend fun ratingFor(key: String): NotflixUserRating? {
        val raw = ratedTitles.first().firstOrNull { it.key == key }?.rating ?: return null
        return runCatching { NotflixUserRating.valueOf(raw) }.getOrNull()
    }

    /** Set (or replace) the rating for one title; newest first. */
    suspend fun setRating(entry: NotflixTasteEntry) {
        val current = ratedTitles.first()
        val updated = (listOf(entry) + current.filterNot { it.key == entry.key }).take(MAX_RATED)
        store().edit { prefs -> prefs[ratedKey] = gson.toJson(updated) }
    }

    /** Clear the rating for one title (pressing the active rating again). */
    suspend fun clearRating(key: String) {
        val current = ratedTitles.first()
        if (current.none { it.key == key }) return
        store().edit { prefs ->
            prefs[ratedKey] = gson.toJson(current.filterNot { it.key == key })
        }
    }

    /** Log a watched trailer; newest first, deduped by title. */
    suspend fun recordTrailer(entry: NotflixTasteEntry) {
        val current = trailerHistory.first()
        val updated = (listOf(entry.copy(rating = null)) + current.filterNot { it.key == entry.key })
            .take(MAX_TRAILERS)
        store().edit { prefs -> prefs[trailersKey] = gson.toJson(updated) }
    }
}
