package com.example.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.models.EventStatus
import com.example.domain.models.SportCategory
import com.example.domain.models.SportEvent
import com.example.ui.components.StatusBadge
import com.example.ui.components.TimeFormatters
import com.example.ui.components.TvEventCard
import com.example.ui.theme.TvBackground
import com.example.ui.theme.TvBorderSubtle
import com.example.ui.theme.TvFocusRing
import com.example.ui.theme.TvPrimary
import com.example.ui.theme.TvSecondary
import com.example.ui.theme.TvSurface
import com.example.ui.theme.TvSurfaceElevated
import com.example.ui.theme.TvTextPrimary
import com.example.ui.theme.TvTextSecondary
import com.example.ui.theme.TvUpcomingAmber

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: CatalogUiState,
    onRetry: () -> Unit,
    onOpenEvent: (SportEvent) -> Unit,
    onQuickWatch: (SportEvent) -> Unit,
    onOpenCategory: (SportCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        is CatalogUiState.Loading -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .testTag("home_loading"),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = TvPrimary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading live sports & channels from PPV.st...",
                        style = MaterialTheme.typography.titleMedium,
                        color = TvTextSecondary,
                    )
                }
            }
        }

        is CatalogUiState.Error -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .testTag("home_error"),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Error",
                        tint = TvPrimary,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        text = uiState.message,
                        style = MaterialTheme.typography.titleLarge,
                        color = TvTextPrimary,
                    )
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.testTag("home_retry_button"),
                    ) {
                        Text(text = stringResource(R.string.action_retry))
                    }
                }
            }
        }

        is CatalogUiState.Ready -> {
            val initialFocusRequester = remember { FocusRequester() }

            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .testTag("home_content"),
                contentPadding = PaddingValues(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                uiState.snapshot.warningMessage?.let { warning ->
                    item(key = "warning_banner") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(TvUpcomingAmber.copy(alpha = 0.16f))
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "Catalog notice",
                                tint = TvUpcomingAmber,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = warning,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TvTextPrimary,
                            )
                        }
                    }
                }

                uiState.featuredEvent?.let { featured ->
                    item(key = "featured_hero") {
                        FeaturedHeroBanner(
                            event = featured,
                            watchFocusRequester = initialFocusRequester,
                            onWatchNow = { onQuickWatch(featured) },
                            onViewDetails = { onOpenEvent(featured) },
                            modifier = Modifier.padding(horizontal = 40.dp),
                        )
                    }
                }

                if (uiState.liveEvents.isNotEmpty()) {
                    item(key = "row_live_now") {
                        EventCarouselRow(
                            title = "${stringResource(R.string.section_live_now)} (${uiState.liveEvents.size})",
                            events = uiState.liveEvents,
                            favoriteIds = uiState.favoriteIds,
                            onOpenEvent = onOpenEvent,
                            rowTag = "live_now_row",
                        )
                    }
                }

                if (uiState.upcomingEvents.isNotEmpty()) {
                    item(key = "row_upcoming") {
                        EventCarouselRow(
                            title = "${stringResource(R.string.section_upcoming)} (${uiState.upcomingEvents.size})",
                            events = uiState.upcomingEvents,
                            favoriteIds = uiState.favoriteIds,
                            onOpenEvent = onOpenEvent,
                            rowTag = "upcoming_events_row",
                        )
                    }
                }

                if (uiState.alwaysLiveChannels.isNotEmpty()) {
                    item(key = "row_channels") {
                        EventCarouselRow(
                            title = "${stringResource(R.string.section_channels)} (${uiState.alwaysLiveChannels.size})",
                            events = uiState.alwaysLiveChannels,
                            favoriteIds = uiState.favoriteIds,
                            onOpenEvent = onOpenEvent,
                            rowTag = "channels_247_row",
                        )
                    }
                }

                if (uiState.categories.isNotEmpty()) {
                    item(key = "row_categories") {
                        CategoryTilesRow(
                            title = stringResource(R.string.section_categories),
                            categories = uiState.categories,
                            onSelectCategory = onOpenCategory,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FeaturedHeroBanner(
    event: SportEvent,
    watchFocusRequester: FocusRequester,
    onWatchNow: () -> Unit,
    onViewDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(event.id) {
        runCatching { watchFocusRequester.requestFocus() }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(276.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(TvSurface),
    ) {
        AsyncImage(
            model = event.posterUrl,
            contentDescription = event.title,
            placeholder = painterResource(id = R.drawable.img_sports_fallback),
            error = painterResource(id = R.drawable.img_sports_fallback),
            fallback = painterResource(id = R.drawable.img_sports_fallback),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            TvBackground.copy(alpha = 0.96f),
                            TvBackground.copy(alpha = 0.80f),
                            TvBackground.copy(alpha = 0.28f),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.68f)
                .padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatusBadge(status = event.status)
                Text(
                    text = listOfNotNull(event.categoryName, event.leagueTag).joinToString(" • "),
                    style = MaterialTheme.typography.labelLarge,
                    color = TvSecondary,
                )
            }

            Text(
                text = event.title,
                style = MaterialTheme.typography.displaySmall,
                color = TvTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            val metaLine = buildString {
                if (event.status == EventStatus.UPCOMING) {
                    append(TimeFormatters.formatLocalTimeRange(event.startsAtEpochSec, event.endsAtEpochSec))
                } else {
                    append(event.primarySource?.label ?: "Broadcast Available")
                    if (event.sources.size > 1) {
                        append(" • ${event.sources.size} sources")
                    }
                    if (event.viewers > 0) {
                        append(" • ${event.viewers} watching")
                    }
                }
            }
            Text(
                text = metaLine,
                style = MaterialTheme.typography.bodyLarge,
                color = TvTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Button(
                    onClick = onWatchNow,
                    modifier = Modifier
                        .focusRequester(watchFocusRequester)
                        .testTag("hero_watch_button"),
                    colors = ButtonDefaults.colors(
                        containerColor = TvPrimary,
                        focusedContainerColor = TvFocusRing,
                        contentColor = Color.White,
                        focusedContentColor = TvBackground,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.action_watch_now))
                }

                OutlinedButton(
                    onClick = onViewDetails,
                    modifier = Modifier.testTag("hero_details_button"),
                ) {
                    Text(text = "Sources & Info")
                }
            }
        }
    }
}

@Composable
private fun EventCarouselRow(
    title: String,
    events: List<SportEvent>,
    favoriteIds: Set<Int>,
    onOpenEvent: (SportEvent) -> Unit,
    rowTag: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TvTextPrimary,
            modifier = Modifier.padding(horizontal = 40.dp),
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(rowTag),
            contentPadding = PaddingValues(horizontal = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(items = events, key = { "${rowTag}_${it.id}" }) { event ->
                TvEventCard(
                    event = event,
                    isFavorite = favoriteIds.contains(event.id),
                    onClick = { onOpenEvent(event) },
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryTilesRow(
    title: String,
    categories: List<SportCategory>,
    onSelectCategory: (SportCategory) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TvTextPrimary,
            modifier = Modifier.padding(horizontal = 40.dp),
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("categories_home_row"),
            contentPadding = PaddingValues(horizontal = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(items = categories, key = { it.id }) { category ->
                Surface(
                    onClick = { onSelectCategory(category) },
                    modifier = Modifier
                        .width(210.dp)
                        .height(92.dp)
                        .testTag("category_tile_${category.id}"),
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = TvSurfaceElevated,
                        focusedContainerColor = TvPrimary,
                        contentColor = TvTextPrimary,
                        focusedContentColor = Color.White,
                    ),
                    border = ClickableSurfaceDefaults.border(
                        border = Border(BorderStroke(1.dp, TvBorderSubtle)),
                        focusedBorder = Border(BorderStroke(3.dp, TvFocusRing)),
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Sports,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = "${category.events.size} streams • ${category.liveCount} live",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TvTextSecondary,
                        )
                    }
                }
            }
        }
    }
}
