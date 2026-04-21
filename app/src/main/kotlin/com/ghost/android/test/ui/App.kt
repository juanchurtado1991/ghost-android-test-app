package com.ghost.android.test.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ghost.android.test.ui.composable.CharacterCard
import com.ghost.android.test.ui.composable.PerformanceResultsCard
import com.ghost.android.test.ui.composable.SampleText
import com.ghost.android.test.ui.composable.StackSelectorDialog
import com.ghost.android.test.ui.model.UiState
import com.ghost.android.test.ui.viewmodel.MainViewModel

@Composable
fun GhostAndroidApp(viewModel: MainViewModel = viewModel()) {
    val uiState = viewModel.uiState
    val clipboard = LocalClipboard.current
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppDesign.BackgroundGradient)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
    ) {
        item {
            BenchmarkTitle()
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            BenchmarkConfigCard(
                uiState = uiState,
                vm = viewModel
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            RunBenchmarkButton(
                vm = viewModel,
                uiState = uiState
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (uiState.errorMessage != null) {
            item {
                ErrorItem(uiState)
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        if (uiState.results.isNotEmpty()) {
            item {
                PerformanceResultsCard(
                    uiState = uiState,
                    onCopyLogs = {
                        val logs = viewModel.getExportLogs()
                        if (logs.isNotEmpty()) {
                            clipboard.nativeClipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("Ghost Benchmark Logs", logs)
                            )
                            Toast.makeText(context, "✅ Session logs copied!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "No logs to copy yet.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun BenchmarkTitle() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            SampleText(
                text = "GHOST",
                isBold = true,
                fontSize = 32,
                overrideColor = AppDesign.AccentGlow
            )
            SampleText(
                text = "SERIALIZATION",
                isBold = true,
                fontSize = 32,
                overrideColor = AppDesign.AccentGlow
            )
        }
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(AppDesign.AccentGlow.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(AppDesign.AccentGlow.copy(alpha = 0.2f), CircleShape)
            )
        }
    }
    SampleText(
        text = "Native Android Performance Lab",
        fontSize = 14,
        isSecondary = true,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun BenchmarkConfigCard(
    uiState: UiState,
    vm: MainViewModel
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = AppDesign.SurfaceColor,
        border = BorderStroke(1.dp, AppDesign.GlassBorder)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SampleText(text = "STRESS LOAD", isBold = true, fontSize = 12)
                SampleText(
                    text = "${uiState.pageCount.toInt()} PAGES (x500)",
                    overrideColor = AppDesign.AccentGlow,
                    isBold = true,
                    fontSize = 12
                )
            }

            Slider(
                value = uiState.pageCount,
                onValueChange = { vm.updatePageCount(it) },
                valueRange = 1f..20f,
                steps = 19,
                modifier = Modifier.padding(top = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = AppDesign.AccentGlow,
                    activeTrackColor = AppDesign.AccentGlow,
                    inactiveTrackColor = AppDesign.GlassBorder
                )
            )
        }
    }
}

@Composable
private fun RunBenchmarkButton(
    vm: MainViewModel,
    uiState: UiState
) {
    Button(
        onClick = { vm.runBenchmark() },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AppDesign.SurfaceColor),
        border = BorderStroke(1.dp, AppDesign.AccentGlow)
    ) {
        if (uiState.isLoading) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = AppDesign.AccentGlow,
                    strokeWidth = 2.dp
                )
                SampleText(
                    text = uiState.loadingStatus,
                    fontSize = 10,
                    overrideColor = AppDesign.AccentGlow
                )
            }
        } else {
            SampleText(
                text = "RUN STRESS COMPARISON",
                isBold = true,
                fontSize = 14,
                overrideColor = AppDesign.AccentGlow
            )
        }
    }
}

@Composable
private fun ErrorItem(uiState: UiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFEF4444).copy(alpha = 0.1f),
        border = BorderStroke(1.dp, Color(0xFFEF4444))
    ) {
        SampleText(
            text = "ERROR: ${uiState.errorMessage}",
            overrideColor = Color(0xFFEF4444),
            fontSize = 12,
            modifier = Modifier.padding(16.dp),
            textAlign = TextAlign.Center
        )
    }
}
