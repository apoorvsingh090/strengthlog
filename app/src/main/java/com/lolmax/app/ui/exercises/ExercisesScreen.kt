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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolmax.app.data.local.entities.Exercise
import com.lolmax.app.di.AppViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisesScreen(
    onOpenExercise: (String) -> Unit,
    factory: AppViewModelFactory,
) {
    val vm: ExercisesViewModel = viewModel(factory = factory)
    val state by vm.uiState.collectAsState()
    var showCreate by remember { mutableStateOf(false) }

    if (showCreate) {
        CreateCustomExerciseDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, bp, cat, bw ->
                vm.createCustom(name, bp, cat, bw)
                showCreate = false
            },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Exercises") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Custom exercise")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                placeholder = { Text("Search") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = state.bodyPartFilter == null,
                    onClick = { vm.setBodyPart(null) },
                    label = { Text("All body") },
                )
                vm.bodyParts.forEach { bp ->
                    FilterChip(
                        selected = state.bodyPartFilter == bp,
                        onClick = { vm.setBodyPart(if (state.bodyPartFilter == bp) null else bp) },
                        label = { Text(bp.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = state.categoryFilter == null,
                    onClick = { vm.setCategory(null) },
                    label = { Text("All gear") },
                )
                vm.categories.forEach { cat ->
                    FilterChip(
                        selected = state.categoryFilter == cat,
                        onClick = { vm.setCategory(if (state.categoryFilter == cat) null else cat) },
                        label = { Text(cat.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            if (state.custom.isNotEmpty()) {
                Text(
                    "Custom Exercises",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(state.custom, key = { "c-${it.id}" }) { ex ->
                    ExerciseRow(ex, onOpenExercise)
                }
                if (state.custom.isNotEmpty()) {
                    item { Spacer(Modifier.height(8.dp)) }
                }
                items(state.results, key = { it.id }) { ex ->
                    ExerciseRow(ex, onOpenExercise)
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun ExerciseRow(ex: Exercise, onOpen: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = { onOpen(ex.id) }) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(ex.name, fontWeight = FontWeight.Medium)
                Text(
                    "${ex.bodyPart.lowercase().replaceFirstChar { it.uppercase() }} · ${ex.category.lowercase().replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CreateCustomExerciseDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var bodyPart by remember { mutableStateOf("CHEST") }
    var category by remember { mutableStateOf("BARBELL") }
    var isBodyweight by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Custom Exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Body part:")
                    FilterChip(selected = true, onClick = {}, label = { Text(bodyPart) })
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    listOf("CHEST", "BACK", "LEGS", "SHOULDERS", "BICEPS", "TRICEPS", "CORE", "FOREARMS", "WHOLE_BODY")
                        .forEach { bp ->
                            FilterChip(
                                selected = bodyPart == bp,
                                onClick = { bodyPart = bp },
                                label = { Text(bp.lowercase().replaceFirstChar { it.uppercase() }) },
                            )
                        }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    listOf("BARBELL", "DUMBBELL", "MACHINE", "CABLE", "BODYWEIGHT", "KETTLEBELL")
                        .forEach { c ->
                            FilterChip(
                                selected = category == c,
                                onClick = { category = c },
                                label = { Text(c.lowercase().replaceFirstChar { it.uppercase() }) },
                            )
                        }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bodyweight exercise", Modifier.weight(1f))
                    Switch(checked = isBodyweight, onCheckedChange = { isBodyweight = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name.trim(), bodyPart, category, isBodyweight) },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
