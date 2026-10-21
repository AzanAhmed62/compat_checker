package com.example.compatchecker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.compatchecker.domain.model.CompatibilityStatus
import com.example.compatchecker.domain.model.ConfidenceLevel
import com.example.compatchecker.domain.model.RiskLevel
import com.example.compatchecker.ui.theme.CompatAmber
import com.example.compatchecker.ui.theme.CompatAmberContainer
import com.example.compatchecker.ui.theme.CompatAmberText
import com.example.compatchecker.ui.theme.CompatGreen
import com.example.compatchecker.ui.theme.CompatGreenContainer
import com.example.compatchecker.ui.theme.CompatGreenText
import com.example.compatchecker.ui.theme.CompatRed
import com.example.compatchecker.ui.theme.CompatRedContainer
import com.example.compatchecker.ui.theme.CompatRedText

@Composable
fun CompatibilityBadge(
    status: CompatibilityStatus,
    modifier: Modifier = Modifier,
    isLarge: Boolean = false
) {
    val (bgColor, textColor, icon, label) = when (status) {
        CompatibilityStatus.COMPATIBLE -> Quad(
            CompatGreenContainer,
            CompatGreenText,
            Icons.Default.CheckCircle,
            "Direct Fit"
        )
        CompatibilityStatus.CONDITIONAL -> Quad(
            CompatAmberContainer,
            CompatAmberText,
            Icons.Default.WarningAmber,
            "Conditional Fit"
        )
        CompatibilityStatus.INCOMPATIBLE -> Quad(
            CompatRedContainer,
            CompatRedText,
            Icons.Default.Block,
            "Incompatible"
        )
        CompatibilityStatus.UNVERIFIED -> Quad(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Default.HelpOutline,
            "Unverified"
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(if (isLarge) 8.dp else 4.dp))
            .background(bgColor)
            .padding(
                horizontal = if (isLarge) 12.dp else 8.dp,
                vertical = if (isLarge) 6.dp else 4.dp
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isLarge) 6.dp else 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = textColor,
                modifier = Modifier.size(if (isLarge) 18.dp else 14.dp)
            )
            Text(
                text = label,
                color = textColor,
                style = if (isLarge) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun RiskLevelBadge(
    riskLevel: RiskLevel,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = when (riskLevel) {
        RiskLevel.NONE -> Pair(CompatGreenContainer, CompatGreenText)
        RiskLevel.LOW -> Pair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        RiskLevel.MODERATE -> Pair(CompatAmberContainer, CompatAmberText)
        RiskLevel.HIGH_SERIALIZED_LOCKOUT -> Pair(CompatRedContainer, CompatRedText)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = riskLevel.label,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ConfidenceIndicator(
    confidence: ConfidenceLevel,
    modifier: Modifier = Modifier
) {
    val barColor = when (confidence) {
        ConfidenceLevel.HIGH_VERIFIED -> CompatGreen
        ConfidenceLevel.MEDIUM_CONFIRMED -> CompatAmber
        ConfidenceLevel.LOW_EXPERIMENTAL -> Color(0xFF94A3B8)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 24.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(barColor)
        )
        Text(
            text = "${confidence.label} (${confidence.score}%)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
