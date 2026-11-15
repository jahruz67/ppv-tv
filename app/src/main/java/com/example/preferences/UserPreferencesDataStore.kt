package com.example.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import com.example.data.api.NetworkModule
import com.example.domain.models.EventStatus
import com.example.domain.models.SportEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

private val Context.ppvTvDataStore: DataStore<Preferences> by preferencesDataStore(name = "ppv_tv_prefs")

@Serializable
data class FavoriteEventSnapshot(
    val id: Int,
    val title: String,
    val categoryName: String,
    val leagueTag: String? = null,
    val posterUrl: String? = null,
    val alwaysLive: Boolean = false,
)

class UserPreferencesDataStore(private val context: Context) {

    private val favoriteSnapshotsKey = stringPreferencesKey("favorite_snapshots_json")
    private val lastSourceMapKey = stringPreferencesKey("last_selected_sources_json")
    private val recentSearchesKey = stringSetPreferencesKey("recent_searches")
    private val customBaseUrlKey = stringPreferencesKey("api_base_url")

    val favoriteSnapshotsFlow: Flow<List<FavoriteEventSnapshot>> = context.ppvTvDataStore.data.map { prefs ->
        val raw = prefs[favoriteSnapshotsKey] ?: return@map emptyList()
        runCatching {
            NetworkModule.json.decodeFromString<List<FavoriteEventSnapshot>>(raw)
        }.getOrDefault(emptyList())
    }

    val favoriteIdsFlow: Flow<Set<Int>> = favoriteSnapshotsFlow.map { list ->
        list.map { it.id }.toSet()
    }

    val apiBaseUrlFlow: Flow<String> = context.ppvTvDataStore.data.map { prefs ->
        prefs[customBaseUrlKey]?.takeIf { it.isNotBlank() } ?: BuildConfig.PPV_API_BASE_URL
    }

    val recentSearchesFlow: Flow<List<String>> = context.ppvTvDataStore.data.map { prefs ->
        prefs[recentSearchesKey]?.toList()?.sorted() ?: emptyList()
    }

    suspend fun toggleFavorite(event: SportEvent) {
        context.ppvTvDataStore.edit { prefs ->
            val current = decodeFavorites(prefs[favoriteSnapshotsKey])
            val exists = current.any { it.id == event.id }
            val updated = if (exists) {
                current.filterNot { it.id == event.id }
            } else {
                listOf(
                    FavoriteEventSnapshot(
                        id = event.id,
                        title = event.title,
                        categoryName = event.categoryName,
                        leagueTag = event.leagueTag,
                        posterUrl = event.posterUrl,
                        alwaysLive = event.alwaysLive || event.status == EventStatus.ALWAYS_LIVE,
                    )
                ) + current
            }
            prefs[favoriteSnapshotsKey] = NetworkModule.json.encodeToString(updated)
        }
    }

    suspend fun removeFavoriteById(eventId: Int) {
        context.ppvTvDataStore.edit { prefs ->
            val current = decodeFavorites(prefs[favoriteSnapshotsKey])
            val updated = current.filterNot { it.id == eventId }
            prefs[favoriteSnapshotsKey] = NetworkModule.json.encodeToString(updated)
        }
    }

    suspend fun saveLastSelectedSource(eventId: Int, sourceId: String) {
        context.ppvTvDataStore.edit { prefs ->
            val current = decodeSourceMap(prefs[lastSourceMapKey]).toMutableMap()
            current[eventId.toString()] = sourceId
            // Cap stored history to avoid unbounded preference growth
            if (current.size > 100) {
                val oldestKey = current.keys.firstOrNull()
                if (oldestKey != null) current.remove(oldestKey)
            }
            prefs[lastSourceMapKey] = NetworkModule.json.encodeToString(current)
        }
    }

    suspend fun getLastSelectedSource(eventId: Int): String? {
        val prefs = context.ppvTvDataStore.data.first()
        return decodeSourceMap(prefs[lastSourceMapKey])[eventId.toString()]
    }

    suspend fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return
        context.ppvTvDataStore.edit { prefs ->
            val current = prefs[recentSearchesKey]?.toMutableSet() ?: mutableSetOf()
            current.add(trimmed)
            if (current.size > 8) {
                current.remove(current.first())
            }
            prefs[recentSearchesKey] = current
        }
    }

    suspend fun setApiBaseUrl(url: String) {
        val trimmed = url.trim()
        context.ppvTvDataStore.edit { prefs ->
            if (trimmed.isEmpty()) {
                prefs.remove(customBaseUrlKey)
            } else {
                prefs[customBaseUrlKey] = trimmed
            }
        }
    }

    private fun decodeFavorites(raw: String?): List<FavoriteEventSnapshot> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            NetworkModule.json.decodeFromString<List<FavoriteEventSnapshot>>(raw)
        }.getOrDefault(emptyList())
    }

    private fun decodeSourceMap(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            NetworkModule.json.decodeFromString<Map<String, String>>(raw)
        }.getOrDefault(emptyMap())
    }
}
