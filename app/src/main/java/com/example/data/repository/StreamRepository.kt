package com.example.data.repository

import android.content.Context
import com.example.BuildConfig
import com.example.data.api.NetworkModule
import com.example.data.api.PpvApiService
import com.example.data.models.FixtureCatalog
import com.example.data.models.PpvStreamsResponse
import com.example.domain.models.CatalogSnapshot
import com.example.domain.usecase.EventCatalogMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.File

interface StreamRepository {
    suspend fun getCatalog(forceRefresh: Boolean = false): Result<CatalogSnapshot>
}

class PpvStreamRepository(
    private val context: Context,
    private val apiServiceProvider: suspend () -> PpvApiService = {
        NetworkModule.createPpvApiService(BuildConfig.PPV_API_BASE_URL)
    },
    private val cacheTtlMs: Long = 60_000L,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) : StreamRepository {

    private val mutex = Mutex()
    private var memorySnapshot: CatalogSnapshot? = null
    private val cacheFile: File by lazy { File(context.cacheDir, "ppv_streams_cache.json") }

    override suspend fun getCatalog(forceRefresh: Boolean): Result<CatalogSnapshot> =
        withContext(Dispatchers.Default) { loadCatalog(forceRefresh) }

    private suspend fun loadCatalog(forceRefresh: Boolean): Result<CatalogSnapshot> {
        return mutex.withLock {
            val nowMs = clockMs()
            val cachedInMemory = memorySnapshot
            if (!forceRefresh && cachedInMemory != null && (nowMs - cachedInMemory.fetchedAtEpochMs) < cacheTtlMs) {
                return@withLock Result.success(cachedInMemory)
            }

            try {
                val apiService = apiServiceProvider()
                val response = withContext(Dispatchers.IO) { apiService.getStreams() }
                if (!response.success && response.streams.isEmpty()) {
                    throw IllegalStateException("PPV.st API returned success=false with empty streams")
                }

                persistCache(response)
                val snapshot = EventCatalogMapper.mapToCatalog(
                    response = response,
                    fetchedAtEpochMs = nowMs,
                    isFromCache = false,
                    isFixtureFallback = false,
                )
                memorySnapshot = snapshot
                Result.success(snapshot)
            } catch (ce: CancellationException) {
                throw ce
            } catch (networkError: Exception) {
                val diskResponse = readCache()
                if (diskResponse != null) {
                    val snapshot = EventCatalogMapper.mapToCatalog(
                        response = diskResponse,
                        nowEpochSec = nowMs / 1000L,
                        fetchedAtEpochMs = cacheFile.lastModified().takeIf { it > 0L } ?: nowMs,
                        isFromCache = true,
                        isFixtureFallback = false,
                        warningMessage = "Showing cached schedule (${networkError.localizedMessage ?: "network error"})",
                    )
                    memorySnapshot = snapshot
                    return@withLock Result.success(snapshot)
                }

                val fixture = FixtureCatalog.createOfflineFallback(nowSec = nowMs / 1000L)
                val fallbackSnapshot = EventCatalogMapper.mapToCatalog(
                    response = fixture,
                    nowEpochSec = nowMs / 1000L,
                    fetchedAtEpochMs = nowMs,
                    isFromCache = false,
                    isFixtureFallback = true,
                    warningMessage = "API unreachable (${networkError.localizedMessage ?: "offline"}). Showing offline verification fixtures.",
                )
                Result.success(fallbackSnapshot)
            }
        }
    }

    private suspend fun persistCache(response: PpvStreamsResponse) {
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = NetworkModule.json.encodeToString(response)
                cacheFile.writeText(encoded)
            }
        }
    }

    private suspend fun readCache(): PpvStreamsResponse? {
        return withContext(Dispatchers.IO) {
            runCatching {
                if (!cacheFile.exists()) return@runCatching null
                val raw = cacheFile.readText()
                if (raw.isBlank()) return@runCatching null
                NetworkModule.json.decodeFromString<PpvStreamsResponse>(raw)
            }.getOrNull()
        }
    }
}
