package com.example.ui.channels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.domain.models.SportEvent
import com.example.ui.components.TvEventCard
import com.example.ui.home.CatalogUiState
import com.example.ui.theme.TvTextPrimary
import com.example.ui.theme.TvTextSecondary

@Composable
fun ChannelsScreen(
    uiState: CatalogUiState,
    onOpenEvent: (SportEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState !is CatalogUiState.Ready) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading 24/7 channels...", color = TvTextSecondary)
        }
        return
    }

    val channels = uiState.alwaysLiveChannels
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp)
            .testTag("channels_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "24/7 Sports & Continuous Channels (${channels.size})",
            style = MaterialTheme.typography.headlineMedium,
            color = TvTextPrimary,
        )

        if (channels.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No 24/7 channels available right now.",
                    style = MaterialTheme.typography.titleMedium,
                    color = TvTextSecondary,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 260.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 48.dp, top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(items = channels, key = { it.id }) { channel ->
                    TvEventCard(
                        event = channel,
                        isFavorite = uiState.favoriteIds.contains(channel.id),
                        onClick = { onOpenEvent(channel) },
                        cardWidth = 280.dp,
                    )
                }
            }
        }
    }
}
