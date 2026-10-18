package com.lolmax.app.ui.exercises

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolmax.app.data.repository.TimeWindow
import com.lolmax.app.di.AppViewModelFactory
import com.lolmax.app.ui.components.ChartPoint
import com.lolmax.app.ui.components.LineChart
import com.lolmax.app.ui.components.StarRating
import com.lolmax.app.ui.components.displayColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseReportScreen(
    exerciseId: String,
    onBack: () -> Unit,
    factory: AppViewModelFactory,
) {
    val vm: ExerciseReportViewModel = viewModel(factory = factory)
    val state by vm.uiState.collectAsState()

    LaunchedEffect(exerciseId) { vm.load(exerciseId) }

    val metrics = listOf("Est. 1RM", "Lifter %", "Max Weight", "Max Reps", "Total Reps", "Volume")
    val windows = TimeWindow.entries

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.exerciseName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) return@Scaffold
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                if (state.hasStandards) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "1RM ${state.bestOneRmLabel}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.padding(6.dp))
                        StarRating(state.stars)
                        state.percentile?.let {
                            Text(
                                " ${it.toInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                                color = (state.level
                                    ?: com.lolmax.app.domain.LiftingLevel.BEGINNER).displayColor,
                            )
                        }
                        state.level?.let {
                            Text(
                                " ${it.display}",
                                style = MaterialTheme.typography.titleSmall,
                                color = it.displayColor,
                            )
                        }
                    }
                } else {
                    Text(
                        "Best set ${state.bestOneRmLabel}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "No strength standards for this exercise",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    windows.forEach { w ->
                        FilterChip(
                            selected = state.window == w,
                            onClick = { vm.setWindow(w) },
                            label = { Text(w.display) },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                TabRow(selectedTabIndex = metrics.indexOf(state.chartMetric).coerceAtLeast(0)) {
                    metrics.forEach { m ->
                        Tab(
                            selected = state.chartMetric == m,
                            onClick = { vm.setMetric(m) },
                            text = { Text(m, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                LineChart(
                    points = state.chartPoints,
                    yLabel = { y ->
                        when (state.chartMetric) {
                            "Lifter %" -> "${y.toInt()}%"
                            "Max Reps", "Total Reps" -> "${y.toInt()}"
                            "Volume" -> if (y >= 1000) "${(y / 1000).toInt()}k" else "${y.toInt()}"
                            else -> if (state.unit == "lb") "${(y / 0.45359237).toInt()}" else "${y.toInt()}"
                        }
                    },
                )
            }

            item {
                Text("Workouts", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                HorizontalDivider()
            }

            items(state.history) { h ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(h.dateLabel, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        if (h.stars > 0) {
                            StarRating(h.stars)
                            h.percentile?.let {
                                Text(
                                    " ${it.toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = (h.level
                                        ?: com.lolmax.app.domain.LiftingLevel.BEGINNER).displayColor,
                                )
                            }
                        }
                    }
                    Text(
                        "${h.bestLabel} · ${h.repsLabel} · ${h.volumeLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
