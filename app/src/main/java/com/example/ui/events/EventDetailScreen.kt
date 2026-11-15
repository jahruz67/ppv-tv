package com.example.ui.events

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
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
import com.example.domain.models.PlaybackMode
import com.example.domain.models.SportEvent
import com.example.domain.models.StreamSource
import com.example.ui.components.StatusBadge
import com.example.ui.components.TimeFormatters
import com.example.ui.theme.TvBackground
import com.example.ui.theme.TvBorderSubtle
import com.example.ui.theme.TvFocusRing
import com.example.ui.theme.TvPrimary
import com.example.ui.theme.TvSecondary
import com.example.ui.theme.TvSurface
import com.example.ui.theme.TvSurfaceElevated
import com.example.ui.theme.TvTextPrimary
import com.example.ui.theme.TvTextSecondary

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EventDetailScreen(
    event: SportEvent?,
    isFavorite: Boolean,
    lastSelectedSourceId: String?,
    onBack: () -> Unit,
    onToggleFavorite: (SportEvent) -> Unit,
    onPlaySource: (eventId: Int, sourceIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    if (event == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(TvBackground),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Event is no longer available in the catalog.",
                    style = MaterialTheme.typography.titleLarge,
                    color = TvTextPrimary,
                )
                Button(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
                    Text("Back to Browse")
                }
            }
        }
        return
    }

    val primaryPlayFocus = remember { FocusRequester() }
    LaunchedEffect(event.id) {
        runCatching { primaryPlayFocus.requestFocus() }
    }

    val preferredSourceIndex = remember(event.id, lastSelectedSourceId, event.sources) {
        val idx = event.sources.indexOfFirst { it.id == lastSelectedSourceId }
        if (idx >= 0) idx else 0
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvBackground)
            .testTag("event_detail_screen"),
    ) {
        AsyncImage(
            model = event.posterUrl,
            contentDescription = event.title,
            placeholder = painterResource(id = R.drawable.img_sports_fallback),
            error = painterResource(id = R.drawable.img_sports_fallback),
            fallback = painterResource(id = R.drawable.img_sports_fallback),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            TvBackground.copy(alpha = 0.55f),
                            TvBackground.copy(alpha = 0.92f),
                            TvBackground,
                        ),
                    ),
                ),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "header_actions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("detail_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Back")
                    }

                    OutlinedButton(
                        onClick = { onToggleFavorite(event) },
                        modifier = Modifier.testTag("detail_favorite_button"),
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) TvSecondary else TvTextPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isFavorite) "Favorited" else "Add to Favorites")
                    }
                }
            }

            item(key = "event_summary") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatusBadge(status = event.status)
                        Text(
                            text = listOfNotNull(event.categoryName, event.leagueTag).joinToString(" • "),
                            style = MaterialTheme.typography.titleMedium,
                            color = TvSecondary,
                        )
                        if (event.viewers > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Visibility,
                                    contentDescription = null,
                                    tint = TvTextSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "${event.viewers} watching",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TvTextSecondary,
                                )
                            }
                        }
                    }

                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.displaySmall,
                        color = TvTextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    val scheduleText = when (event.status) {
                        EventStatus.ALWAYS_LIVE -> "Continuous 24/7 Broadcast"
                        else -> TimeFormatters.formatLocalTimeRange(event.startsAtEpochSec, event.endsAtEpochSec)
                    }
                    Text(
                        text = scheduleText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TvTextSecondary,
                    )

                    if (event.sources.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = { onPlaySource(event.id, preferredSourceIndex) },
                            modifier = Modifier
                                .focusRequester(primaryPlayFocus)
                                .testTag("detail_watch_primary_button"),
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
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val targetSource = event.sources[preferredSourceIndex]
                            Text("Watch (${targetSource.label})")
                        }
                    }
                }
            }

            item(key = "sources_heading") {
                Text(
                    text = "Available Broadcast Sources (${event.sources.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = TvTextPrimary,
                )
            }

            if (event.sources.isEmpty()) {
                item(key = "no_sources") {
                    Text(
                        text = "No stream sources are currently published for this event. Refresh later near kickoff.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TvTextSecondary,
                    )
                }
            } else {
                itemsIndexed(
                    items = event.sources,
                    key = { _, src -> src.id },
                ) { index, source ->
                    SourceOptionRow(
                        source = source,
                        index = index,
                        isPreferred = source.id == lastSelectedSourceId,
                        onClick = { onPlaySource(event.id, index) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SourceOptionRow(
    source: StreamSource,
    index: Int,
    isPreferred: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("source_option_$index"),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TvSurfaceElevated,
            focusedContainerColor = TvPrimary,
            contentColor = TvTextPrimary,
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, if (isPreferred) TvSecondary else TvBorderSubtle)),
            focusedBorder = Border(BorderStroke(3.dp, TvFocusRing)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = if (source.playbackMode == PlaybackMode.NATIVE_MEDIA3) Icons.Filled.Tv else Icons.Filled.Public,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = source.label,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = buildString {
                            append(source.broadcaster)
                            source.language?.let { append(" • Language: $it") }
                            if (isPreferred) append(" • Last Used")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TvTextSecondary,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                source.quality?.let { q ->
                    Text(
                        text = q,
                        style = MaterialTheme.typography.labelMedium,
                        color = TvTextPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(TvSurface)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
                val modeBadge = when (source.playbackMode) {
                    PlaybackMode.NATIVE_MEDIA3 -> "Media3"
                    PlaybackMode.WEB_EMBED -> "Web Embed"
                    PlaybackMode.UNSUPPORTED -> "Unsupported"
                }
                Text(
                    text = modeBadge,
                    style = MaterialTheme.typography.labelMedium,
                    color = TvBackground,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(TvSecondary)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}
