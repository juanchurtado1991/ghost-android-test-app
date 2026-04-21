package com.ghost.android.test.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghost.android.test.data.RickAndMortyRepository
import com.ghost.android.test.ui.model.NetworkStack
import com.ghost.android.test.ui.model.UiState
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    private val repository = RickAndMortyRepository()

    var uiState by mutableStateOf(UiState())
        private set

    private var currentBenchmarkingEngine: String? = null
    private var currentJankAccumulator = 0

    fun showStackDialog(show: Boolean) {
        uiState = uiState.copy(isStackDialogVisible = show)
    }

    fun selectStack(stack: NetworkStack) {
        uiState = uiState.copy(selectedStack = stack, isStackDialogVisible = false)
    }

    fun updatePageCount(count: Float) {
        uiState = uiState.copy(pageCount = count)
    }

    fun onJankDetected() {
        if (currentBenchmarkingEngine != null) {
            currentJankAccumulator++
        }
    }

    fun runBenchmark() {
        if (uiState.isLoading) return

        uiState = uiState.copy(
            isLoading = true,
            errorMessage = null,
            loadingStatus = "Initiating..."
        )

        viewModelScope.launch {
            try {
                // 1. Run the comparison
                val engineResults = repository.runBenchmark(uiState.pageCount.toInt()) { status ->
                    uiState = uiState.copy(loadingStatus = status)
                }

                // 2. Generate detailed log entry
                val runIndex = uiState.sessionHistory.size + 1
                val logEntry = buildString {
                    appendLine("--- NATIVE ANDROID RUN #$runIndex ---")
                    appendLine("Stress Load: ${uiState.pageCount.toInt()} pages")
                    engineResults.forEach { res ->
                        appendLine("${res.name}: ${res.timeMs}ms | ${res.memoryBytes / 1024}KB")
                    }
                    appendLine("------------------------------\n")
                }

                uiState = uiState.copy(
                    isLoading = false,
                    results = engineResults,
                    sessionHistory = uiState.sessionHistory + logEntry
                )
            } catch (e: Exception) {
                uiState =
                    uiState.copy(isLoading = false, errorMessage = e.message ?: "Unknown Error")
            }
        }
    }

    fun getExportLogs(): String {
        return uiState.sessionHistory.joinToString("\n")
    }
}
