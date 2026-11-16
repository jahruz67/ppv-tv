package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.data.api.NetworkModule
import com.example.data.repository.PpvStreamRepository
import com.example.data.repository.StreamRepository
import com.example.preferences.UserPreferencesDataStore
import kotlinx.coroutines.flow.first

class AppContainer(private val application: Application) {
    val preferences: UserPreferencesDataStore by lazy {
        UserPreferencesDataStore(application.applicationContext)
    }

    private val okHttpClient by lazy {
        NetworkModule.createOkHttpClient()
    }

    val repository: StreamRepository by lazy {
        PpvStreamRepository(
            context = application.applicationContext,
            apiServiceProvider = {
                val baseUrl = preferences.apiBaseUrlFlow.first()
                NetworkModule.createPpvApiService(
                    baseUrl = baseUrl,
                    okHttpClient = okHttpClient,
                )
            },
        )
    }
}

class PpvTvApplication : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(32L * 1024L * 1024L)
                    .build()
            }
            .crossfade(false)
            .build()
    }
}
