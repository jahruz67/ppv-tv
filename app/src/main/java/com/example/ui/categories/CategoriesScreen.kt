package com.example.ui.categories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.domain.models.SportEvent
import com.example.ui.components.TvEventCard
import com.example.ui.home.CatalogUiState
import com.example.ui.theme.TvBorderSubtle
import com.example.ui.theme.TvFocusRing
import com.example.ui.theme.TvPrimary
import com.example.ui.theme.TvSurface
import com.example.ui.theme.TvSurfaceHighlight
import com.example.ui.theme.TvTextPrimary
import com.example.ui.theme.TvTextSecondary

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CategoriesScreen(
    uiState: CatalogUiState,
    selectedCategoryId: Int?,
    onSelectCategory: (Int) -> Unit,
    onOpenEvent: (SportEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState !is CatalogUiState.Ready) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Catalog is loading...", color = TvTextSecondary)
        }
        return
    }

    val categories = uiState.categories
    val activeCategory = categories.firstOrNull { it.id == selectedCategoryId } ?: categories.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("categories_screen"),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("category_chip_row"),
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items = categories, key = { it.id }) { category ->
                val selected = category.id == activeCategory?.id
                Surface(
                    onClick = { onSelectCategory(category.id) },
                    modifier = Modifier.testTag("category_chip_${category.id}"),
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(20.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (selected) TvSurfaceHighlight else TvSurface,
                        focusedContainerColor = TvPrimary,
                        contentColor = if (selected) TvTextPrimary else TvTextSecondary,
                        focusedContentColor = Color.White,
                    ),
                    border = ClickableSurfaceDefaults.border(
                        border = Border(BorderStroke(1.dp, if (selected) TvPrimary else TvBorderSubtle)),
                        focusedBorder = Border(BorderStroke(2.dp, TvFocusRing)),
                    ),
                ) {
                    Text(
                        text = "${category.name} (${category.events.size})",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }

        if (activeCategory == null || activeCategory.events.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No events currently listed in this category.",
                    style = MaterialTheme.typography.titleMedium,
                    color = TvTextSecondary,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 260.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 40.dp),
                contentPadding = PaddingValues(bottom = 48.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(items = activeCategory.events, key = { it.id }) { event ->
                    TvEventCard(
                        event = event,
                        isFavorite = uiState.favoriteIds.contains(event.id),
                        onClick = { onOpenEvent(event) },
                        cardWidth = 280.dp,
                    )
                }
            }
        }
    }
}
