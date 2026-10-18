package com.lolmax.app.ui.templates

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolmax.app.data.local.entities.Exercise
import com.lolmax.app.di.AppViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditScreen(
    templateId: String?,
    onDone: () -> Unit,
    onBack: () -> Unit,
    factory: AppViewModelFactory,
) {
    val vm: TemplateEditViewModel = viewModel(factory = factory)
    val state by vm.uiState.collectAsState()

    LaunchedEffect(templateId) { vm.init(templateId) }
    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    if (state.pickerOpen) {
        Dialog(onDismissRequest = vm::closePicker) {
            Surface(
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(560.dp)
                    .padding(16.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = vm::search,
                        placeholder = { Text("Search exercises") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn {
                        itemsIndexed(state.searchResults) { _, ex: Exercise ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(ex.name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                FilterChip(
                                    selected = false,
                                    onClick = { vm.addExercise(ex) },
                                    label = { Text("Add") },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (templateId == null) "New Template" else "Edit Template") },
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = vm::setName,
                    label = { Text("Template name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            itemsIndexed(state.exercises) { exIndex, ex ->
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            ex.name,
                            Modifier.weight(1f),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        IconButton(onClick = { vm.removeExercise(exIndex) }) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                        }
                    }
                    ex.sets.forEachIndexed { setIndex, set ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${setIndex + 1}.", Modifier.width(24.dp),
                                style = MaterialTheme.typography.labelLarge)
                            OutlinedTextField(
                                value = set.weightText,
                                onValueChange = { v ->
                                    vm.updateSet(exIndex, setIndex) {
                                        it.copy(weightText = v)
                                    }
                                },
                                label = { Text("kg (opt)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            )
                            OutlinedTextField(
                                value = set.repsText,
                                onValueChange = { v ->
                                    vm.updateSet(exIndex, setIndex) {
                                        it.copy(repsText = v)
                                    }
                                },
                                label = { Text("reps") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                        }
                    }
                    TextButton(onClick = { vm.addSet(exIndex) }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add set")
                    }
                }
            }
            item {
                state.error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedButton(onClick = vm::openPicker, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Exercise")
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
