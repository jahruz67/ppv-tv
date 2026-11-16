package com.example.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.StreamRepository
import com.example.domain.models.CatalogSnapshot
import com.example.domain.models.EventStatus
import com.example.domain.models.SportCategory
import com.example.domain.models.SportEvent
import com.example.domain.usecase.EventCatalogMapper
import com.example.domain.usecase.SearchEventsUseCase
import com.example.preferences.FavoriteEventSnapshot
import com.example.preferences.UserPreferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface CatalogUiState {
    data object Loading : CatalogUiState

    data class Ready(
        val snapshot: CatalogSnapshot,
        val featuredEvent: SportEvent?,
        val liveEvents: List<SportEvent>,
        val upcomingEvents: List<SportEvent>,
        val alwaysLiveChannels: List<SportEvent>,
        val categories: List<SportCategory>,
        val favoriteIds: Set<Int>,
        val favoriteSnapshots: List<FavoriteEventSnapshot>,
        val isRefreshing: Boolean,
    ) : CatalogUiState

    data class Error(val message: String) : CatalogUiState
}

class HomeViewModel(
    private val repository: StreamRepository,
    private val preferences: UserPreferencesDataStore,
) : ViewModel() {

    private val snapshotFlow = MutableStateFlow<CatalogSnapshot?>(null)
    private val refreshingFlow = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<String?>(null)
    private var periodicRefreshJob: Job? = null
    private var activeLoadJob: Job? = null

    val searchQuery = MutableStateFlow("")
    val selectedCategoryId = MutableStateFlow<Int?>(null)
    val recentSearches: StateFlow<List<String>> = preferences.recentSearchesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Rebuild catalog sections only when the schedule changes, away from UI work.
    private val catalogContentFlow = snapshotFlow.map { snapshot ->
        snapshot?.let {
            val all = snapshot.allEvents
            val liveEvents = all.filter { it.status == EventStatus.LIVE }
                .sortedWith(EventCatalogMapper.eventComparator)
            val upcomingEvents = all.filter { it.status == EventStatus.UPCOMING }
                .sortedWith(EventCatalogMapper.eventComparator)
            val channels = all.filter { it.status == EventStatus.ALWAYS_LIVE }
                .sortedByDescending { it.viewers }
            val featured = liveEvents.firstOrNull()
                ?: upcomingEvents.firstOrNull()
                ?: channels.firstOrNull()

            CatalogUiState.Ready(
                snapshot = snapshot,
                featuredEvent = featured,
                liveEvents = liveEvents,
                upcomingEvents = upcomingEvents,
                alwaysLiveChannels = channels,
                categories = snapshot.categories,
                favoriteIds = emptySet(),
                favoriteSnapshots = emptyList(),
                isRefreshing = false,
            )
        }
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<CatalogUiState> = combine(
        catalogContentFlow,
        refreshingFlow,
        errorFlow,
        preferences.favoriteIdsFlow,
        preferences.favoriteSnapshotsFlow,
    ) { catalog, isRefreshing, errorMsg, favIds, favSnapshots ->
        catalog?.copy(
            favoriteIds = favIds,
            favoriteSnapshots = favSnapshots,
            isRefreshing = isRefreshing,
        ) ?: errorMsg?.let { CatalogUiState.Error(it) } ?: CatalogUiState.Loading
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogUiState.Loading)

    val searchResults: StateFlow<List<SportEvent>> = combine(
        snapshotFlow,
        searchQuery,
    ) { snapshot, query ->
        val events = snapshot?.allEvents.orEmpty()
        SearchEventsUseCase.search(events, query)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        loadCatalog(forceRefresh = false)
    }

    fun loadCatalog(forceRefresh: Boolean = true) {
        if (activeLoadJob?.isActive == true && !forceRefresh) return
        activeLoadJob?.cancel()
        activeLoadJob = viewModelScope.launch {
            refreshingFlow.value = true
            errorFlow.value = null
            val result = repository.getCatalog(forceRefresh = forceRefresh)
            result.onSuccess { snapshot ->
                snapshotFlow.value = snapshot
                if (selectedCategoryId.value == null) {
                    selectedCategoryId.value = snapshot.categories.firstOrNull()?.id
                }
            }.onFailure { err ->
                if (snapshotFlow.value == null) {
                    errorFlow.value = err.localizedMessage ?: "Failed to load PPV.st stream catalog"
                }
            }
            refreshingFlow.value = false
        }
    }

    fun startPeriodicRefresh(intervalMs: Long = 90_000L) {
        if (periodicRefreshJob?.isActive == true) return
        periodicRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(intervalMs)
                loadCatalog(forceRefresh = true)
            }
        }
    }

    fun stopPeriodicRefresh() {
        periodicRefreshJob?.cancel()
        periodicRefreshJob = null
    }

    fun selectCategory(categoryId: Int) {
        selectedCategoryId.value = categoryId
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun commitSearchQuery(query: String) {
        searchQuery.value = query
        viewModelScope.launch {
            preferences.addRecentSearch(query)
        }
    }

    fun toggleFavorite(event: SportEvent) {
        viewModelScope.launch {
            preferences.toggleFavorite(event)
        }
    }

    fun removeFavoriteById(eventId: Int) {
        viewModelScope.launch {
            preferences.removeFavoriteById(eventId)
        }
    }

    fun findEventById(eventId: Int): SportEvent? {
        return snapshotFlow.value?.allEvents?.firstOrNull { it.id == eventId }
    }

    suspend fun getLastSelectedSourceId(eventId: Int): String? {
        return preferences.getLastSelectedSource(eventId)
    }

    fun saveLastSelectedSource(eventId: Int, sourceId: String) {
        viewModelScope.launch {
            preferences.saveLastSelectedSource(eventId, sourceId)
        }
    }

    class Factory(
        private val repository: StreamRepository,
        private val preferences: UserPreferencesDataStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository, preferences) as T
        }
    }
}
