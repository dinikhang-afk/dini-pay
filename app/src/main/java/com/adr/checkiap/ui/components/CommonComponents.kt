package com.adr.checkiap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adr.checkiap.data.model.OfferClassification
import com.adr.checkiap.data.model.ScanStatus
import com.adr.checkiap.ui.theme.AccentBlue
import com.adr.checkiap.ui.theme.DarkBorder
import com.adr.checkiap.ui.theme.DarkBackground
import com.adr.checkiap.ui.theme.DarkCard
import com.adr.checkiap.ui.theme.DarkSurface
import com.adr.checkiap.ui.theme.ErrorRed
import com.adr.checkiap.ui.theme.PurpleTrial
import com.adr.checkiap.ui.theme.SuccessGreen
import com.adr.checkiap.ui.theme.TextMuted
import com.adr.checkiap.ui.theme.TextPrimary
import com.adr.checkiap.ui.theme.TextSecondary
import com.adr.checkiap.ui.theme.WarningYellow

@Composable
fun StatusBadge(status: ScanStatus) {
    val (bgColor, textColor, label) = when (status) {
        ScanStatus.FOUND -> Triple(SuccessGreen.copy(alpha = 0.15f), SuccessGreen, "FOUND")
        ScanStatus.NOT_FOUND -> Triple(WarningYellow.copy(alpha = 0.15f), WarningYellow, "NOT FOUND")
        ScanStatus.ERROR -> Triple(ErrorRed.copy(alpha = 0.15f), ErrorRed, "ERROR")
        ScanStatus.UNSUPPORTED -> Triple(TextMuted.copy(alpha = 0.15f), TextMuted, "UNSUPPORTED")
        ScanStatus.NO_ELIGIBLE_OFFER -> Triple(WarningYellow.copy(alpha = 0.15f), WarningYellow, "NO ELIGIBLE OFFER")
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .border(1.dp, textColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun OfferBadge(classification: OfferClassification) {
    val (bgColor, textColor, label) = when (classification) {
        OfferClassification.FREE_TRIAL -> Triple(PurpleTrial.copy(alpha = 0.15f), PurpleTrial, "FREE TRIAL")
        OfferClassification.INTRO_DISCOUNT -> Triple(AccentBlue.copy(alpha = 0.15f), AccentBlue, "DISCOUNT")
        OfferClassification.REGULAR -> Triple(DarkCard, TextSecondary, "REGULAR")
        OfferClassification.UNKNOWN -> Triple(DarkCard, TextMuted, "UNKNOWN")
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .border(1.dp, textColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector = Icons.Default.Info,
    accentColor: Color = AccentBlue,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 22.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
    }
}

@Composable
fun ConsoleBox(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(DarkBackground)
            .border(1.dp, DarkBorder, RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = TextSecondary,
            lineHeight = 16.sp
        )
    }
}
