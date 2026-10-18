package com.lolmax.app.ui.workouts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolmax.app.di.AppViewModelFactory
import com.lolmax.app.ui.util.Units
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogWorkoutScreen(
    workoutId: String?,
    templateId: String?,
    onDone: (String?) -> Unit,
    onBack: () -> Unit,
    factory: AppViewModelFactory,
) {
    val vm: LogWorkoutViewModel = viewModel(factory = factory)
    val state by vm.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(workoutId, templateId) { vm.init(workoutId, templateId) }
    LaunchedEffect(state.savedId) {
        if (state.savedId != null) onDone(state.savedId)
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date
                .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val picked = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneOffset.UTC)
                            .toLocalDate()
                        vm.setDate(picked)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (state.pickerOpen) {
        ExercisePickerDialog(
            query = state.searchQuery,
            results = state.searchResults,
            onQuery = vm::search,
            onSelect = vm::addExercise,
            onDismiss = vm::closePicker,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (workoutId != null) "Edit Workout" else "Log Workout") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = vm::save, enabled = !state.saving) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (state.saving) "Saving…" else "Save")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = vm::setTitle,
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.date.toString(),
                        onValueChange = {},
                        label = { Text("Date (YYYY-MM-DD)") },
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showDatePicker = true },
                        singleLine = true,
                        readOnly = true,
                        trailingIcon = {
                            Icon(Icons.Filled.DateRange, contentDescription = "Pick date")
                        },
                    )
                    OutlinedTextField(
                        value = state.bodyweightText,
                        onValueChange = vm::setBodyweight,
                        label = { Text("Bodyweight (${state.unit})") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = vm::setEndNow) { Text("Set end time = now") }
                    if (state.endMinute != null) {
                        val h = state.endMinute!! / 60
                        val m = state.endMinute!! % 60
                        val h12 = when {
                            h == 0 -> 12
                            h > 12 -> h - 12
                            else -> h
                        }
                        Text(
                            String.format(
                                "%d:%02d %s",
                                h12,
                                m,
                                if (h >= 12) "PM" else "AM",
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            itemsIndexed(state.exercises, key = { i, ex -> "${ex.exerciseId}-$i" }) { exIndex, ex ->
                ExerciseLogCard(
                    ex = ex,
                    exIndex = exIndex,
                    unit = state.unit,
                    bestOneRm = vm.bestOneRmForExercise(exIndex),
                    onRemoveExercise = { vm.removeExercise(exIndex) },
                    onAddSet = { vm.addSet(exIndex) },
                    onRemoveSet = { si -> vm.removeSet(exIndex, si) },
                    onUpdateSet = { si, t -> vm.updateSet(exIndex, si, t) },
                )
            }

            item {
                OutlinedButton(
                    onClick = vm::openPicker,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Exercise")
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseLogCard(
    ex: ExerciseInput,
    exIndex: Int,
    unit: String,
    bestOneRm: Double,
    onRemoveExercise: () -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (Int) -> Unit,
    onUpdateSet: (Int, (SetInput) -> SetInput) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ex.name, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium)
                if (bestOneRm > 0) {
                    Text(
                        "Est. 1RM ${Units.kgToDisplay(bestOneRm, unit)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = onRemoveExercise) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove exercise")
            }
        }
        ex.sets.forEachIndexed { setIndex, set ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("${setIndex + 1}.", style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(24.dp))
                OutlinedTextField(
                    value = set.weightText,
                    onValueChange = { v -> onUpdateSet(setIndex) { it.copy(weightText = v) } },
                    label = { Text(unit) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !ex.isBodyweight,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = set.repsText,
                    onValueChange = { v -> onUpdateSet(setIndex) { it.copy(repsText = v) } },
                    label = { Text("reps") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Checkbox(
                        checked = set.isWarmup,
                        onCheckedChange = { c -> onUpdateSet(setIndex) { it.copy(isWarmup = c) } },
                    )
                    Text("warm", style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = { onRemoveSet(setIndex) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove set")
                }
            }
            if (ex.isBodyweight) {
                Text(
                    "Bodyweight exercise — leave weight blank (or add added weight)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 32.dp),
                )
            }
        }
        TextButton(onClick = onAddSet) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Add set")
        }
        Spacer(Modifier.height(6.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExercisePickerDialog(
    query: String,
    results: List<com.lolmax.app.data.local.entities.Exercise>,
    onQuery: (String) -> Unit,
    onSelect: (com.lolmax.app.data.local.entities.Exercise) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .height(560.dp)
                .padding(16.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    placeholder = { Text("Search exercises") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn {
                    items(results, key = { it.id }) { ex ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(ex.name, fontWeight = FontWeight.Medium)
                                Text(
                                    "${ex.bodyPart.replace('_', ' ')} · ${ex.category}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            FilterChip(selected = false, onClick = { onSelect(ex) }, label = { Text("Add") })
                        }
                    }
                }
            }
        }
    }
}
