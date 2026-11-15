package com.example

import android.content.Context
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import com.example.data.models.FixtureCatalog
import com.example.domain.models.EventStatus
import com.example.domain.models.PlaybackMode
import com.example.domain.models.SportEvent
import com.example.domain.models.StreamSource
import com.example.domain.usecase.EventCatalogMapper
import com.example.preferences.UserPreferencesDataStore
import com.example.ui.components.TopNavTab
import com.example.ui.components.TvTopBar
import com.example.ui.events.EventDetailScreen
import com.example.ui.home.CatalogUiState
import com.example.ui.home.HomeScreen
import com.example.ui.player.PlayerScreen
import com.example.ui.player.PlayerViewModel
import com.example.ui.theme.PpvTvTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h720dp-land")
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `app name matches PPV_st TV`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("PPV.st TV", context.getString(R.string.app_name))
    }

    @Test
    fun `datastore persists favorites and last selected source across reads`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = UserPreferencesDataStore(context)
        val now = 1794721000L
        val catalog = EventCatalogMapper.mapToCatalog(FixtureCatalog.createOfflineFallback(now), now)
        val sampleEvent = catalog.allEvents.first()

        prefs.toggleFavorite(sampleEvent)
        val savedIds = prefs.favoriteIdsFlow.first()
        assertTrue(savedIds.contains(sampleEvent.id))

        prefs.saveLastSelectedSource(sampleEvent.id, sampleEvent.sources.last().id)
        assertEquals(sampleEvent.sources.last().id, prefs.getLastSelectedSource(sampleEvent.id))

        prefs.toggleFavorite(sampleEvent)
        val updatedIds = prefs.favoriteIdsFlow.first()
        assertFalse(updatedIds.contains(sampleEvent.id))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `home screen renders live now upcoming and 24_7 sections and responds to dpad selection`() {
        val now = 1794721000L
        val catalog = EventCatalogMapper.mapToCatalog(FixtureCatalog.createOfflineFallback(now), now)
        val state = CatalogUiState.Ready(
            snapshot = catalog,
            featuredEvent = catalog.allEvents.first(),
            liveEvents = catalog.allEvents.filter { it.status == EventStatus.LIVE },
            upcomingEvents = catalog.allEvents.filter { it.status == EventStatus.UPCOMING },
            alwaysLiveChannels = catalog.allEvents.filter { it.status == EventStatus.ALWAYS_LIVE },
            categories = catalog.categories,
            favoriteIds = emptySet(),
            favoriteSnapshots = emptyList(),
            isRefreshing = false,
        )
        var openedEventId = -1

        composeTestRule.setContent {
            PpvTvTheme {
                HomeScreen(
                    uiState = state,
                    onRetry = {},
                    onOpenEvent = { openedEventId = it.id },
                    onQuickWatch = {},
                    onOpenCategory = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("home_content").assertIsDisplayed()
        composeTestRule.onNodeWithTag("live_now_row").assertIsDisplayed()
        // Initial focus lands on Watch Now; D-pad Right moves to Sources & Info, then DirectionCenter selects it
        composeTestRule.onNodeWithTag("hero_watch_button").assertIsFocused()
        composeTestRule.onNodeWithTag("hero_watch_button").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("hero_details_button").assertIsFocused()
        composeTestRule.onNodeWithTag("hero_details_button").performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeTestRule.waitForIdle()
        assertEquals(catalog.allEvents.first().id, openedEventId)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `top bar supports dpad navigation between browse tabs`() {
        var activeTab = TopNavTab.HOME
        composeTestRule.setContent {
            PpvTvTheme {
                TvTopBar(
                    selectedTab = activeTab,
                    isRefreshing = false,
                    onSelectTab = { activeTab = it },
                    onRefresh = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("nav_tab_home").performSemanticsAction(SemanticsActions.RequestFocus)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_tab_home").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_tab_categories").assertIsFocused()
        composeTestRule.onNodeWithTag("nav_tab_categories").performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeTestRule.waitForIdle()
        assertEquals(TopNavTab.CATEGORIES, activeTab)

        composeTestRule.onNodeWithTag("nav_tab_search").performSemanticsAction(SemanticsActions.RequestFocus)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_tab_search").performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeTestRule.waitForIdle()
        assertEquals(TopNavTab.SEARCH, activeTab)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `event detail displays dynamic sources and triggers source playback via dpad`() {
        val now = 1794721000L
        val catalog = EventCatalogMapper.mapToCatalog(FixtureCatalog.createOfflineFallback(now), now)
        val event = catalog.allEvents.first { it.sources.size >= 2 }
        var playedSourceIdx = -1

        composeTestRule.setContent {
            PpvTvTheme {
                EventDetailScreen(
                    event = event,
                    isFavorite = true,
                    lastSelectedSourceId = event.sources[1].id,
                    onBack = {},
                    onToggleFavorite = {},
                    onPlaySource = { _, idx -> playedSourceIdx = idx },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("source_option_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("source_option_1").performSemanticsAction(SemanticsActions.RequestFocus)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("source_option_1").performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeTestRule.waitForIdle()
        assertEquals(1, playedSourceIdx)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `player screen surfaces error recovery controls and source switcher without auto cycling`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = UserPreferencesDataStore(context)
        val playerVm = PlayerViewModel(prefs)
        val event = SportEvent(
            id = 555,
            title = "Test Match",
            leagueTag = "NBA",
            categoryId = 37,
            categoryName = "Basketball",
            posterUrl = null,
            accentColors = emptyList(),
            uriName = null,
            startsAtEpochSec = 1794720000L,
            endsAtEpochSec = 1794730000L,
            alwaysLive = false,
            status = EventStatus.LIVE,
            viewers = 10,
            sources = listOf(
                StreamSource(
                    id = "s1",
                    label = "HD | Home",
                    broadcaster = "Home",
                    quality = "HD",
                    language = "EN",
                    tag = "NBA",
                    url = "",
                    playbackMode = PlaybackMode.UNSUPPORTED,
                    isPrimary = true,
                ),
                StreamSource(
                    id = "s2",
                    label = "HD | Away",
                    broadcaster = "Away",
                    quality = "HD",
                    language = "EN",
                    tag = "NBA",
                    url = "https://taifood-blog.asia/embed/away",
                    playbackMode = PlaybackMode.WEB_EMBED,
                    isPrimary = false,
                ),
            ),
        )
        playerVm.initialize(event, 0)
        var exited = false

        composeTestRule.setContent {
            PpvTvTheme {
                PlayerScreen(
                    viewModel = playerVm,
                    onBackToEvent = { exited = true },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("player_error_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithText("Source Unavailable").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_change_source_button").assertIsFocused()
        composeTestRule.onNodeWithTag("player_change_source_button").performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("source_switcher_overlay").assertIsDisplayed()

        composeTestRule.onNodeWithTag("player_back_to_event_button").performSemanticsAction(SemanticsActions.RequestFocus)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("player_back_to_event_button").performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeTestRule.waitForIdle()
        assertTrue(exited)
    }

    @Test
    fun `player screen exposes play and mute controls for web embed streams`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = UserPreferencesDataStore(context)
        val playerVm = PlayerViewModel(prefs)
        val event = SportEvent(
            id = 777,
            title = "NFL Network",
            leagueTag = "24/7",
            categoryId = 14,
            categoryName = "24/7 Streams",
            posterUrl = null,
            accentColors = emptyList(),
            uriName = "nfl-network",
            startsAtEpochSec = null,
            endsAtEpochSec = null,
            alwaysLive = true,
            status = EventStatus.ALWAYS_LIVE,
            viewers = 100,
            sources = listOf(
                StreamSource(
                    id = "s_embed",
                    label = "HD | NFL Network",
                    broadcaster = "NFL Network",
                    quality = "HD",
                    language = "EN",
                    tag = "24/7",
                    url = "https://taifood-blog.asia/embed/nfl-network",
                    playbackMode = PlaybackMode.WEB_EMBED,
                    isPrimary = true,
                ),
            ),
        )
        playerVm.initialize(event, 0)

        composeTestRule.setContent {
            PpvTvTheme {
                PlayerScreen(
                    viewModel = playerVm,
                    onBackToEvent = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("player_play_pause_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_play_pause_button").assertIsFocused()
        composeTestRule.onNodeWithTag("player_mute_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mute").assertIsDisplayed()
        assertFalse(playerVm.uiState.value.isMuted)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `player screen hides overlay without top right pill and wakes overlay on dpad`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = UserPreferencesDataStore(context)
        val playerVm = PlayerViewModel(prefs)
        val event = SportEvent(
            id = 888,
            title = "Live Championship",
            leagueTag = "UFC",
            categoryId = 20,
            categoryName = "MMA",
            posterUrl = null,
            accentColors = emptyList(),
            uriName = "live-champ",
            startsAtEpochSec = 1794720000L,
            endsAtEpochSec = 1794730000L,
            alwaysLive = false,
            status = EventStatus.LIVE,
            viewers = 250,
            sources = listOf(
                StreamSource(
                    id = "s_embed",
                    label = "HD | Main Feed",
                    broadcaster = "Main Feed",
                    quality = "HD",
                    language = "EN",
                    tag = "UFC",
                    url = "https://taifood-blog.asia/embed/live-champ",
                    playbackMode = PlaybackMode.WEB_EMBED,
                    isPrimary = true,
                ),
            ),
        )
        playerVm.initialize(event, 0)
        playerVm.onPlaying()

        composeTestRule.setContent {
            PpvTvTheme {
                PlayerScreen(
                    viewModel = playerVm,
                    onBackToEvent = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        // Re-initializing with the same event/source (e.g. periodic catalog refresh during buffering)
        // must not reset playback or increment reloadNonce, and buffering must not show the embed overlay.
        playerVm.onBuffering()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Loading broadcast embed...").assertDoesNotExist()
        val nonceBeforeRefresh = playerVm.uiState.value.reloadNonce
        playerVm.initialize(event, 0)
        assertEquals(nonceBeforeRefresh, playerVm.uiState.value.reloadNonce)
        assertEquals(com.example.ui.player.PlaybackStatus.Buffering, playerVm.uiState.value.playbackStatus)

        playerVm.onPlaying()
        playerVm.toggleControls()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("player_collapsed_menu_pill").assertDoesNotExist()
        composeTestRule.onNodeWithTag("player_hidden_focus_anchor").assertIsFocused()

        composeTestRule.onNodeWithTag("player_hidden_focus_anchor").performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeTestRule.waitForIdle()

        assertTrue(playerVm.uiState.value.controlsVisible)
        composeTestRule.onNodeWithTag("player_play_pause_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_play_pause_button").assertIsFocused()
    }
}
