# PPV.st Android TV Application

Native Android TV application built with **Kotlin**, **Jetpack Compose for TV (`androidx.tv:tv-material`)**, **AndroidX Media3 ExoPlayer**, **Android WebView**, **Retrofit + Kotlinx Serialization**, **DataStore**, and **Coil**.

## Features Implemented

- **Direct PPV.st API Integration (`https://api.ppv.st/api/streams`)**:
  - Strongly typed Kotlinx Serialization DTOs with resilient deserializers (`FlexibleBooleanSerializer`, `FlexibleIntSerializer`) for API field variations.
  - Memory + disk JSON caching (`ppv_streams_cache.json`) with configurable base URL (`BuildConfig.PPV_API_BASE_URL` / DataStore override), controlled 90s periodic refresh, and offline verification fixture fallback (`FixtureCatalog`) clearly badged in the UI.
- **Television-First Home & Browse Screens**:
  - **Featured Live Hero Banner**, **Live Now**, **Upcoming Events** (converted to device local timezone), **24/7 Channels**, **Dynamic Sports Categories**, **Favorites**, and **Remote-Friendly Search**.
- **Event Status Engine**:
  - Computes `LIVE`, `UPCOMING`, `ENDED`, and `24/7` (`ALWAYS_LIVE`) using `starts_at`, `ends_at`, and `always_live` without blindly marking unscheduled items live.
  - Sorts live events first (by viewer count), upcoming events chronologically, and keeps 24/7 channels accessible separately.
- **Dual Playback Architecture**:
  - **Native Media3 ExoPlayer (`NativeMediaPlayer`)**: Supports HLS (`.m3u8`), DASH (`.mpd`), and MP4 streams with audio focus, lifecycle pause/resume, bounded automatic reconnects, and audio track selection.
  - **Fullscreen WebView Embed Player (`EmbeddedWebPlayer`)**: Supports HTML iframe streams via `WebChromeClient` HTML5 fullscreen video, HTTP/network error interception, safe WebView disposal, and TV-remote accessible overlay controls (preventing focus traps).
- **Source Switching & Recovery**:
  - Discovers primary + `substreams` variants (broadcaster, quality, language) dynamically.
  - Remembers last successfully played source per event in DataStore.
  - Surfaces **Retry**, **Change Source**, and **Back to Event** without auto-cycling through failed sources.
- **Android TV Compatibility & D-Pad Navigation**:
  - Declares `LEANBACK_LAUNCHER`, `android:banner="@drawable/tv_banner"`, `android.hardware.touchscreen` `required="false"`, landscape orientation, and full D-pad/Play-Pause/Menu/Back key handling.

## Project Structure

- `data/api/` — `PpvApiService`, `NetworkModule`
- `data/models/` — `PpvApiDtos`, `FixtureCatalog`
- `data/repository/` — `StreamRepository` / `PpvStreamRepository`
- `domain/models/` — `SportEvent`, `StreamSource`, `SportCategory`, `EventStatus`, `PlaybackMode`, `CatalogSnapshot`
- `domain/playback/` — `PlaybackStrategyResolver`
- `domain/usecase/` — `EventStatusCalculator`, `EventCatalogMapper`, `SearchEventsUseCase`
- `preferences/` — `UserPreferencesDataStore`
- `navigation/` — `AppDestinations`, `PpvTvNavHost`
- `ui/` — `home`, `categories`, `channels`, `events`, `player`, `search`, `favorites`, `components`, `theme`

## Building & Testing

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

Debug APK output path:
`app/build/outputs/apk/debug/app-debug.apk`

See `API_VERIFICATION.md` for the live PPV.st API investigation and verification status.
