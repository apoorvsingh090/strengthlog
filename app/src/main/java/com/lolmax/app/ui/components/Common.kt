package com.lolmax.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lolmax.app.domain.LiftingLevel
import com.lolmax.app.ui.theme.LevelAdvanced
import com.lolmax.app.ui.theme.LevelBeginner
import com.lolmax.app.ui.theme.LevelElite
import com.lolmax.app.ui.theme.LevelIntermediate
import com.lolmax.app.ui.theme.LevelNovice
import com.lolmax.app.ui.theme.StarGold
import com.lolmax.app.ui.util.starString
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val LiftingLevel.displayColor: Color
    get() = when (this) {
        LiftingLevel.BEGINNER -> LevelBeginner
        LiftingLevel.NOVICE -> LevelNovice
        LiftingLevel.INTERMEDIATE -> LevelIntermediate
        LiftingLevel.ADVANCED -> LevelAdvanced
        LiftingLevel.ELITE -> LevelElite
    }

@Composable
fun StarRating(stars: Int, modifier: Modifier = Modifier) {
    Text(
        text = starString(stars),
        color = StarGold,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier,
    )
}

@Composable
fun LevelBadge(
    stars: Int,
    percentile: Double?,
    levelName: String?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        StarRating(stars)
        if (percentile != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${percentile.toInt()}%",
                color = color,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (levelName != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = levelName,
                color = color,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

data class ChartPoint(val x: Long, val y: Double, val label: String = "")

/**
 * Minimal dependency-free line chart for time series (e.g. 1RM over workouts).
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    yLabel: (Double) -> String = { String.format("%.0f", it) },
) {
    if (points.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                "Not enough data yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    androidx.compose.foundation.Canvas(modifier = modifier.height(200.dp).fillMaxWidth()) {
        val padLeft = 48.dp.toPx()
        val padBottom = 28.dp.toPx()
        val padTop = 12.dp.toPx()
        val padRight = 12.dp.toPx()
        val w = size.width - padLeft - padRight
        val h = size.height - padTop - padBottom
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val rangeY = if (maxY - minY < 1e-9) 1.0 else maxY - minY
        val minX = points.minOf { it.x }.toFloat()
        val maxX = points.maxOf { it.x }.toFloat()
        val rangeX = if (maxX - minX < 1e-9) 1f else maxX - minX

        fun toOffset(p: ChartPoint): Offset {
            val x = padLeft + (p.x - minX) / rangeX * w
            val y = padTop + h - ((p.y - minY) / rangeY * h).toFloat()
            return Offset(x.toFloat(), y)
        }

        // Grid lines + y labels
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 11.sp.toPx()
            isAntiAlias = true
        }
        for (i in 0..3) {
            val frac = i / 3f
            val yVal = minY + (maxY - minY) * (1 - frac)
            val yPx = padTop + h * frac
            drawLine(
                color = Color.LightGray.copy(alpha = 0.5f),
                start = Offset(padLeft, yPx),
                end = Offset(padLeft + w, yPx),
                strokeWidth = 1.dp.toPx(),
            )
            drawContext.canvas.nativeCanvas.drawText(
                yLabel(yVal), 4.dp.toPx(), yPx + 4.dp.toPx(), paint
            )
        }

        // Line path
        val path = Path()
        points.forEachIndexed { i, p ->
            val o = toOffset(p)
            if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

        // Dots
        for (p in points) {
            drawCircle(color = lineColor, radius = 3.dp.toPx(), center = toOffset(p))
        }
    }
}
