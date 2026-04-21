package com.ghost.android.test.ui.composable

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ghost.android.test.ui.AppDesign
import com.ghost.android.test.ui.model.UiState
import com.ghost.android.test.ui.model.EngineResult

private val GhostColor = AppDesign.AccentGlow
private val KserColor = Color(0xFF60A5FA)   // Blue 400
private val MoshiColor = Color(0xFF818CF8)  // Indigo 400
private val GsonColor = Color(0xFFFBBF24)   // Amber 400

@SuppressLint("DefaultLocale")
@Composable
fun PerformanceResultsCard(
    uiState: UiState,
    onCopyLogs: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = AppDesign.SurfaceColor.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, AppDesign.GlassBorder)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SampleText(
                text = "PERFORMANCE INSIGHT",
                isBold = true,
                fontSize = 12,
                overrideColor = AppDesign.AccentGlow
            )

            SampleText(
                text = "Native Android benchmark across major engines.",
                fontSize = 13,
                isSecondary = true,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            HorizontalDivider(
                color = AppDesign.GlassBorder,
                thickness = 1.dp,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Group results by category
            val categories = listOf(
                "REAL-WORLD" to "[NETWORK]",
                "PARSE · STRING" to "[PARSE_STRING]",
                "PARSE · BYTES" to "[PARSE_BYTES]",
                "PARSE · STREAMING" to "[PARSE_STREAM]",
                "WRITE · STRING" to "[WRITE_STRING]",
                "WRITE · BYTES" to "[WRITE_BYTES]",
                "WRITE · BUFFER" to "[WRITE_BUFFER]"
            )

            categories.forEach { (title, prefix) ->
                val group = uiState.results.filter { it.name.startsWith(prefix) }
                if (group.isNotEmpty()) {
                    ResultSection(title, group)
                }
            }

            TextButton(
                onClick = onCopyLogs,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                SampleText(
                    text = "COPY SESSION LOGS",
                    overrideColor = AppDesign.AccentGlow,
                    isBold = true,
                    fontSize = 11
                )
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
private fun ResultSection(title: String, results: List<EngineResult>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        // Section header
        SampleText(
            text = title,
            isBold = true,
            fontSize = 11,
            isSecondary = true,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Sort results by time (fastest first)
        val sorted = results.sortedBy { if (it.timeMs <= 0.0) Double.MAX_VALUE else it.timeMs }
        val fastestTime = sorted.firstOrNull()?.timeMs ?: 0.0

        sorted.forEachIndexed { index, res ->
            val cleanName = res.name.substringAfter("] ").trim()
            val engineColor = resolveEngineColor(cleanName)
            val isWinner = index == 0 && res.timeMs > 0

            EngineRow(
                name = cleanName,
                timeMs = res.timeMs,
                memoryKb = res.memoryBytes / 1024,
                color = engineColor,
                isWinner = isWinner,
                fastestTimeMs = fastestTime
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

@SuppressLint("DefaultLocale")
@Composable
private fun EngineRow(
    name: String,
    timeMs: Double,
    memoryKb: Long,
    color: Color,
    isWinner: Boolean,
    fastestTimeMs: Double
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(
                if (isWinner) color.copy(alpha = 0.08f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )

        Spacer(modifier = Modifier.width(10.dp))

        // Engine name
        SampleText(
            text = name,
            fontSize = 13,
            isBold = isWinner,
            overrideColor = if (isWinner) color else AppDesign.TextPrimary,
            modifier = Modifier.weight(1f)
        )

        // Time
        val timeText = if (timeMs > 0) String.format("%.2fms", timeMs) else "N/A"
        SampleText(
            text = timeText,
            fontSize = 13,
            isBold = true,
            overrideColor = if (isWinner) color else AppDesign.TextPrimary
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Memory
        SampleText(
            text = "${memoryKb}KB",
            fontSize = 11,
            isSecondary = true
        )

        // Slowdown badge (for non-winners)
        if (!isWinner && timeMs > 0 && fastestTimeMs > 0) {
            val slowdown = ((timeMs / fastestTimeMs) - 1.0) * 100.0
            if (slowdown > 1.0) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .background(Color(0xFFEF4444).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    SampleText(
                        text = String.format("+%.0f%%", slowdown),
                        fontSize = 9,
                        overrideColor = Color(0xFFEF4444)
                    )
                }
            }
        }
    }
}

private fun resolveEngineColor(name: String): Color {
    val upper = name.uppercase()
    return when {
        upper.contains("GHOST") -> GhostColor
        upper.contains("KSER") -> KserColor
        upper.contains("MOSHI") -> MoshiColor
        upper.contains("GSON") -> GsonColor
        else -> AppDesign.TextSecondary
    }
}
