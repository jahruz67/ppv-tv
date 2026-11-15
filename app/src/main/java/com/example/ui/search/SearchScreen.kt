package com.example.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.R
import com.example.domain.models.SportEvent
import com.example.ui.components.TvEventCard
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
fun SearchScreen(
    query: String,
    results: List<SportEvent>,
    favoriteIds: Set<Int>,
    recentSearches: List<String>,
    quickTags: List<String>,
    onQueryChange: (String) -> Unit,
    onCommitQuery: (String) -> Unit,
    onOpenEvent: (SportEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val inputFocusRequester = remember { FocusRequester() }
    var isInputFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp)
            .testTag("search_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TvSurfaceElevated)
                    .border(
                        width = if (isInputFocused) 2.dp else 1.dp,
                        color = if (isInputFocused) TvFocusRing else TvBorderSubtle,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search",
                    tint = TvSecondary,
                    modifier = Modifier.size(22.dp),
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = TvTextSecondary,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        cursorBrush = SolidColor(TvSecondary),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = TvTextPrimary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                onCommitQuery(query)
                                keyboardController?.hide()
                            },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(inputFocusRequester)
                            .onFocusChanged { isInputFocused = it.isFocused }
                            .testTag("search_input"),
                    )
                }
            }

            if (query.isNotEmpty()) {
                Surface(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.testTag("search_clear_button"),
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = TvSurface,
                        focusedContainerColor = TvPrimary,
                        contentColor = TvTextPrimary,
                    ),
                    border = ClickableSurfaceDefaults.border(
                        border = Border(BorderStroke(1.dp, TvBorderSubtle)),
                        focusedBorder = Border(BorderStroke(2.dp, TvFocusRing)),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                        Text("Clear", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        val chips = (recentSearches + quickTags).distinct().take(10)
        if (chips.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
            ) {
                items(items = chips, key = { it }) { chip ->
                    Surface(
                        onClick = {
                            onQueryChange(chip)
                            onCommitQuery(chip)
                        },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(18.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = TvSurface,
                            focusedContainerColor = TvPrimary,
                            contentColor = TvTextSecondary,
                            focusedContentColor = Color.White,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(BorderStroke(1.dp, TvBorderSubtle)),
                            focusedBorder = Border(BorderStroke(2.dp, TvFocusRing)),
                        ),
                    ) {
                        Text(
                            text = chip,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        if (results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.empty_search_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = TvTextPrimary,
                    )
                    Text(
                        text = stringResource(R.string.empty_search_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TvTextSecondary,
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 260.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("search_results_grid"),
                contentPadding = PaddingValues(bottom = 48.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(items = results, key = { it.id }) { event ->
                    TvEventCard(
                        event = event,
                        isFavorite = favoriteIds.contains(event.id),
                        onClick = {
                            if (query.isNotBlank()) onCommitQuery(query)
                            onOpenEvent(event)
                        },
                        cardWidth = 280.dp,
                    )
                }
            }
        }
    }
}
