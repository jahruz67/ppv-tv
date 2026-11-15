package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.domain.models.EventStatus
import com.example.ui.theme.TvAlwaysLiveCyan
import com.example.ui.theme.TvBackground
import com.example.ui.theme.TvEndedSlate
import com.example.ui.theme.TvLiveRed
import com.example.ui.theme.TvTextPrimary
import com.example.ui.theme.TvUpcomingAmber

@Composable
fun StatusBadge(
    status: EventStatus,
    modifier: Modifier = Modifier,
) {
    val (bgColor, textColor, label, showDot) = when (status) {
        EventStatus.LIVE -> BadgeStyle(TvLiveRed, TvTextPrimary, "LIVE", true)
        EventStatus.UPCOMING -> BadgeStyle(TvUpcomingAmber, TvBackground, "UPCOMING", false)
        EventStatus.ALWAYS_LIVE -> BadgeStyle(TvAlwaysLiveCyan, TvBackground, "24/7", true)
        EventStatus.ENDED -> BadgeStyle(TvEndedSlate, TvTextPrimary, "ENDED", false)
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (showDot) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(textColor),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
        )
    }
}

private data class BadgeStyle(
    val bg: Color,
    val fg: Color,
    val label: String,
    val showDot: Boolean,
)
