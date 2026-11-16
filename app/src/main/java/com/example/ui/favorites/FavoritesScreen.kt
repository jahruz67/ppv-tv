package com.example.ui.favorites

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.R
import com.example.domain.models.SportEvent
import com.example.preferences.FavoriteEventSnapshot
import com.example.ui.components.TvEventCard
import com.example.ui.home.CatalogUiState
import com.example.ui.theme.TvBorderSubtle
import com.example.ui.theme.TvFocusRing
import com.example.ui.theme.TvSurface
import com.example.ui.theme.TvTextPrimary
import com.example.ui.theme.TvTextSecondary

@Composable
fun FavoritesScreen(
    uiState: CatalogUiState,
    onOpenEvent: (SportEvent) -> Unit,
    onRemoveMissingFavorite: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState !is CatalogUiState.Ready) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading favorites...", color = TvTextSecondary)
        }
        return
    }

    val liveById = remember(uiState.snapshot.allEvents) {
        uiState.snapshot.allEvents.associateBy { it.id }
    }
    val snapshots = uiState.favoriteSnapshots

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp)
            .testTag("favorites_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "${stringResource(R.string.section_favorites)} (${snapshots.size})",
            style = MaterialTheme.typography.headlineMedium,
            color = TvTextPrimary,
        )

        if (snapshots.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.BookmarkBorder,
                        contentDescription = null,
                        tint = TvTextSecondary,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        text = stringResource(R.string.empty_favorites_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = TvTextPrimary,
                    )
                    Text(
                        text = stringResource(R.string.empty_favorites_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TvTextSecondary,
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 260.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 48.dp, top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(items = snapshots, key = { it.id }) { saved ->
                    val activeEvent = liveById[saved.id]
                    if (activeEvent != null) {
                        TvEventCard(
                            event = activeEvent,
                            isFavorite = true,
                            onClick = { onOpenEvent(activeEvent) },
                            cardWidth = 280.dp,
                        )
                    } else {
                        UnavailableFavoriteCard(
                            snapshot = saved,
                            onRemove = { onRemoveMissingFavorite(saved.id) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun UnavailableFavoriteCard(
    snapshot: FavoriteEventSnapshot,
    onRemove: () -> Unit,
) {
    Card(
        onClick = onRemove,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("unavailable_favorite_${snapshot.id}"),
        shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = CardDefaults.colors(containerColor = TvSurface),
        border = CardDefaults.border(
            border = Border(BorderStroke(1.dp, TvBorderSubtle)),
            focusedBorder = Border(BorderStroke(3.dp, TvFocusRing)),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = snapshot.title,
                style = MaterialTheme.typography.titleMedium,
                color = TvTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${snapshot.categoryName} • Currently not in live catalog",
                style = MaterialTheme.typography.bodyMedium,
                color = TvTextSecondary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Button(onClick = onRemove) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("Remove Favorite")
                }
            }
        }
    }
}
