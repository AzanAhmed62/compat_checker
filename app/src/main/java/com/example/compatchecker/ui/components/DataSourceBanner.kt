package com.example.compatchecker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.compatchecker.core.config.DataSourceType
import com.example.compatchecker.ui.theme.CompatAmber
import com.example.compatchecker.ui.theme.CompatAmberContainer
import com.example.compatchecker.ui.theme.CompatAmberText
import com.example.compatchecker.ui.theme.CompatGreen
import com.example.compatchecker.ui.theme.CompatGreenContainer
import com.example.compatchecker.ui.theme.CompatGreenText
import com.example.compatchecker.ui.theme.TechCyan

@Composable
fun DataSourceBanner(
    sourceType: DataSourceType,
    onConfigureClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, icon, label, actionText) = when (sourceType) {
        DataSourceType.DEMONSTRATION -> Tuple5(
            CompatAmberContainer,
            CompatAmberText,
            Icons.Default.Info,
            "Demonstration Benchmark Mode",
            "Config"
        )
        DataSourceType.CACHED_OFFLINE -> Tuple5(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Default.OfflinePin,
            "Offline Local Cache Active",
            "Sync"
        )
        DataSourceType.LIVE_SUPABASE -> Tuple5(
            CompatGreenContainer,
            CompatGreenText,
            Icons.Default.CloudDone,
            "Live Supabase Connected",
            "Details"
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onConfigureClick)
            .testTag("data_source_banner")
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = textColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = label,
                    color = textColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = actionText,
                color = textColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

private data class Tuple5<A, B, C, D, E>(
    val a: A, val b: B, val c: C, val d: D, val e: E
)
