package com.example.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.AppContainer
import com.example.ui.categories.CategoriesScreen
import com.example.ui.channels.ChannelsScreen
import com.example.ui.components.TopNavTab
import com.example.ui.components.TvTopBar
import com.example.ui.events.EventDetailScreen
import com.example.ui.favorites.FavoritesScreen
import com.example.ui.home.CatalogUiState
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.player.PlayerScreen
import com.example.ui.player.PlayerViewModel
import com.example.ui.search.SearchScreen
import com.example.ui.theme.TvBackground

@Composable
fun PpvTvNavHost(
    appContainer: AppContainer,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(appContainer.repository, appContainer.preferences),
    )
    val catalogState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val selectedCategoryId by homeViewModel.selectedCategoryId.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> homeViewModel.startPeriodicRefresh()
                Lifecycle.Event.ON_STOP -> homeViewModel.stopPeriodicRefresh()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            homeViewModel.stopPeriodicRefresh()
        }
    }

    var activeTab by rememberSaveable { mutableStateOf(TopNavTab.HOME) }

    NavHost(
        navController = navController,
        startDestination = AppDestination.Browse,
        enterTransition = { fadeIn(tween(120)) },
        exitTransition = { fadeOut(tween(120)) },
        popEnterTransition = { fadeIn(tween(120)) },
        popExitTransition = { fadeOut(tween(120)) },
        modifier = modifier
            .fillMaxSize()
            .background(TvBackground),
    ) {
        composable<AppDestination.Browse> {
            // Back on secondary browse tabs returns cleanly to HOME before exiting
            BackHandler(enabled = activeTab != TopNavTab.HOME) {
                activeTab = TopNavTab.HOME
            }

            val isRefreshing = (catalogState as? CatalogUiState.Ready)?.isRefreshing == true

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
            ) {
                TvTopBar(
                    selectedTab = activeTab,
                    isRefreshing = isRefreshing,
                    onSelectTab = { tab -> activeTab = tab },
                    onRefresh = { homeViewModel.loadCatalog(forceRefresh = true) },
                )

                when (activeTab) {
                    TopNavTab.HOME -> {
                        HomeScreen(
                            uiState = catalogState,
                            onRetry = { homeViewModel.loadCatalog(forceRefresh = true) },
                            onOpenEvent = { event ->
                                navController.navigate(AppDestination.EventDetail(event.id))
                            },
                            onQuickWatch = { event ->
                                if (event.sources.isNotEmpty()) {
                                    navController.navigate(AppDestination.Player(event.id, 0))
                                } else {
                                    navController.navigate(AppDestination.EventDetail(event.id))
                                }
                            },
                            onOpenCategory = { category ->
                                homeViewModel.selectCategory(category.id)
                                activeTab = TopNavTab.CATEGORIES
                            },
                        )
                    }

                    TopNavTab.CATEGORIES -> {
                        CategoriesScreen(
                            uiState = catalogState,
                            selectedCategoryId = selectedCategoryId,
                            onSelectCategory = homeViewModel::selectCategory,
                            onOpenEvent = { event ->
                                navController.navigate(AppDestination.EventDetail(event.id))
                            },
                        )
                    }

                    TopNavTab.CHANNELS -> {
                        ChannelsScreen(
                            uiState = catalogState,
                            onOpenEvent = { event ->
                                navController.navigate(AppDestination.EventDetail(event.id))
                            },
                        )
                    }

                    TopNavTab.FAVORITES -> {
                        FavoritesScreen(
                            uiState = catalogState,
                            onOpenEvent = { event ->
                                navController.navigate(AppDestination.EventDetail(event.id))
                            },
                            onRemoveMissingFavorite = homeViewModel::removeFavoriteById,
                        )
                    }

                    TopNavTab.SEARCH -> {
                        val searchQuery by homeViewModel.searchQuery.collectAsStateWithLifecycle()
                        val searchResults by homeViewModel.searchResults.collectAsStateWithLifecycle()
                        val recentSearches by homeViewModel.recentSearches.collectAsStateWithLifecycle()
                        val ready = catalogState as? CatalogUiState.Ready
                        val quickTags = ready?.categories
                            ?.map { it.name }
                            ?.take(6)
                            .orEmpty()
                        SearchScreen(
                            query = searchQuery,
                            results = searchResults,
                            favoriteIds = ready?.favoriteIds ?: emptySet(),
                            recentSearches = recentSearches,
                            quickTags = quickTags,
                            onQueryChange = homeViewModel::updateSearchQuery,
                            onCommitQuery = homeViewModel::commitSearchQuery,
                            onOpenEvent = { event ->
                                navController.navigate(AppDestination.EventDetail(event.id))
                            },
                        )
                    }
                }
            }
        }

        composable<AppDestination.EventDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<AppDestination.EventDetail>()
            val ready = catalogState as? CatalogUiState.Ready
            val event = ready?.snapshot?.allEvents?.firstOrNull { it.id == route.eventId }
            val isFavorite = ready?.favoriteIds?.contains(route.eventId) == true

            val lastSourceId by produceState<String?>(initialValue = null, route.eventId) {
                value = homeViewModel.getLastSelectedSourceId(route.eventId)
            }

            EventDetailScreen(
                event = event,
                isFavorite = isFavorite,
                lastSelectedSourceId = lastSourceId,
                onBack = { navController.popBackStack() },
                onToggleFavorite = homeViewModel::toggleFavorite,
                onPlaySource = { eventId, sourceIndex ->
                    navController.navigate(AppDestination.Player(eventId, sourceIndex))
                },
                modifier = Modifier.safeDrawingPadding(),
            )
        }

        composable<AppDestination.Player> { backStackEntry ->
            val route = backStackEntry.toRoute<AppDestination.Player>()
            val playerViewModel: PlayerViewModel = viewModel(
                factory = PlayerViewModel.Factory(appContainer.preferences),
            )
            val ready = catalogState as? CatalogUiState.Ready
            val event = ready?.snapshot?.allEvents?.firstOrNull { it.id == route.eventId }

            LaunchedEffect(route.eventId, route.sourceIndex, event?.id) {
                playerViewModel.initialize(event, route.sourceIndex)
            }

            PlayerScreen(
                viewModel = playerViewModel,
                onBackToEvent = { navController.popBackStack() },
            )
        }
    }
}
