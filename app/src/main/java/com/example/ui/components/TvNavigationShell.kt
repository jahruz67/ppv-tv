package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.ui.theme.TvBackground
import com.example.ui.theme.TvBorderSubtle
import com.example.ui.theme.TvFocusRing
import com.example.ui.theme.TvPrimary
import com.example.ui.theme.TvSecondary
import com.example.ui.theme.TvSurface
import com.example.ui.theme.TvSurfaceElevated
import com.example.ui.theme.TvSurfaceHighlight
import com.example.ui.theme.TvTextPrimary
import com.example.ui.theme.TvTextSecondary

enum class TopNavTab(val label: String, val icon: ImageVector, val testTag: String) {
    HOME("Home", Icons.Filled.Home, "nav_tab_home"),
    CATEGORIES("Categories", Icons.Filled.Category, "nav_tab_categories"),
    CHANNELS("24/7 TV", Icons.Filled.LiveTv, "nav_tab_channels"),
    FAVORITES("Favorites", Icons.Filled.Bookmark, "nav_tab_favorites"),
    SEARCH("Search", Icons.Filled.Search, "nav_tab_search"),
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvTopBar(
    selectedTab: TopNavTab,
    isRefreshing: Boolean,
    onSelectTab: (TopNavTab) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(TvBackground.copy(alpha = 0.96f))
            .padding(horizontal = 40.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(TvPrimary),
            )
            Text(
                text = "PPV.st",
                style = MaterialTheme.typography.titleLarge,
                color = TvTextPrimary,
            )
            Text(
                text = "TV",
                style = MaterialTheme.typography.labelMedium,
                color = TvBackground,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(TvSecondary)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TopNavTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                Surface(
                    onClick = { onSelectTab(tab) },
                    modifier = Modifier.testTag(tab.testTag),
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(24.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isSelected) TvSurfaceHighlight else Color.Transparent,
                        focusedContainerColor = TvPrimary,
                        contentColor = if (isSelected) TvTextPrimary else TvTextSecondary,
                        focusedContentColor = TvTextPrimary,
                    ),
                    border = ClickableSurfaceDefaults.border(
                        border = if (isSelected) Border(BorderStroke(1.dp, TvBorderSubtle)) else Border.None,
                        focusedBorder = Border(BorderStroke(2.dp, TvFocusRing)),
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                onClick = onRefresh,
                modifier = Modifier.testTag("refresh_catalog_button"),
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(24.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = TvSurface,
                    focusedContainerColor = TvSurfaceElevated,
                    contentColor = TvTextPrimary,
                    focusedContentColor = TvSecondary,
                ),
                border = ClickableSurfaceDefaults.border(
                    border = Border(BorderStroke(1.dp, TvBorderSubtle)),
                    focusedBorder = Border(BorderStroke(2.dp, TvFocusRing)),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh catalog",
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = if (isRefreshing) "Syncing..." else "Refresh",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = TimeFormatters.formatCurrentClock(),
                style = MaterialTheme.typography.labelLarge,
                color = TvTextSecondary,
            )
        }
    }
}
