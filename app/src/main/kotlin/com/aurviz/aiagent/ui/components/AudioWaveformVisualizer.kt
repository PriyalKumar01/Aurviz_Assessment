package com.aurviz.aiagent.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AudioWaveformVisualizer(
    rmsLevel: Float,
    isListening: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 9,
    activeColor: Color = MaterialTheme.colorScheme.primary
) {
    val transition = rememberInfiniteTransition(label = "WaveformAnimation")
    val animatedPhase by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "WavePhase"
    )

    Row(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val centerOffset = Math.abs(i - (barCount / 2)).toFloat() / (barCount / 2)
            val baseScale = 1.0f - (centerOffset * 0.4f)

            val heightFactor = if (isListening) {
                val dynamic = (rmsLevel * 2f).coerceIn(0.15f, 1.0f)
                val variation = if (i % 2 == 0) animatedPhase else (1.2f - animatedPhase * 0.5f)
                (dynamic * baseScale * variation).coerceIn(0.1f, 1.0f)
            } else {
                0.12f
            }

            val targetHeight = (heightFactor * 36.dp.value).coerceAtLeast(6f).dp

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(targetHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (isListening) activeColor else activeColor.copy(alpha = 0.3f)
                    )
            )
        }
    }
}
