package com.lolmax.app.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lolmax.app.di.AppViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onOpenPowerlifting: () -> Unit,
    onOpenCalculator: () -> Unit,
    factory: AppViewModelFactory,
) {
    val vm: ProfileViewModel = viewModel(factory = factory)
    val settingsVm: SettingsViewModel = viewModel(factory = factory)
    val state by vm.uiState.collectAsState()
    val theme by settingsVm.themeMode.collectAsState()
    val exportCsv by settingsVm.exportCsvText.collectAsState()
    val importStatus by settingsVm.importStatus.collectAsState()
    val busy by settingsVm.busy.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("MALE") }
    var age by remember { mutableStateOf("25") }
    var bodyweight by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var loadedForm by remember { mutableStateOf(false) }
    var lastUnit by remember { mutableStateOf(state.unit) }

    LaunchedEffect(state.profile, state.unit) {
        val p = state.profile ?: return@LaunchedEffect
        if (!loadedForm) {
            name = p.name
            gender = p.gender
            age = p.age.toString()
            bodyweight = com.lolmax.app.ui.util.Units.kgToNumber(p.bodyweightKg, state.unit)
            height = p.heightCm.toString()
            lastUnit = state.unit
            loadedForm = true
        } else if (lastUnit != state.unit) {
            // Convert the in-progress bodyweight entry so switching lb <-> kg
            // never re-saves the same number under the wrong unit.
            bodyweight.toDoubleOrNull()?.let { v ->
                val kg = com.lolmax.app.ui.util.Units.displayToKg(v, lastUnit)
                bodyweight = com.lolmax.app.ui.util.Units.kgToNumber(kg, state.unit)
            }
            lastUnit = state.unit
        }
    }

    var pendingExportText by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            val text = pendingExportText ?: exportCsv ?: ""
            scope.launch {
                val ok = try {
                    withContext(Dispatchers.IO) {
                        val os = context.contentResolver.openOutputStream(uri)
                        if (os == null) false else os.use { it.write(text.toByteArray()); true }
                    }
                } catch (e: Exception) {
                    false
                }
                snackbar.showSnackbar(if (ok) "Export saved" else "Export failed: could not write file")
            }
        }
        pendingExportText = null
        settingsVm.consumeExport()
    }

    LaunchedEffect(exportCsv) {
        exportCsv?.let { text ->
            if (pendingExportText == null) {
                pendingExportText = text
                exportLauncher.launch("lolmax-export.csv")
            }
        }
    }
    LaunchedEffect(importStatus) {
        importStatus?.let {
            scope.launch { snackbar.showSnackbar(it) }
            settingsVm.consumeImportStatus()
            vm.refresh()
        }
    }

    LaunchedEffect(state.saveStatus) {
        state.saveStatus?.let {
            snackbar.showSnackbar(it)
            vm.consumeSaveStatus()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { settingsVm.importCsvFromUri(it, context.contentResolver) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Profile") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your info", fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(name, { name = it }, label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(gender == "MALE", { gender = "MALE" }, { Text("Male") })
                        FilterChip(gender == "FEMALE", { gender = "FEMALE" }, { Text("Female") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            age, { age = it }, label = { Text("Age") },
                            modifier = Modifier.weight(1f), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        OutlinedTextField(
                            bodyweight, { bodyweight = it },
                            label = { Text("Bodyweight (${state.unit})") },
                            modifier = Modifier.weight(1f), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                    OutlinedTextField(
                        height, { height = it }, label = { Text("Height (cm)") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Units:")
                        FilterChip(state.unit == "kg", { vm.setUnit("kg") }, { Text("kg") })
                        FilterChip(state.unit == "lb", { vm.setUnit("lb") }, { Text("lb") })
                    }
                    OutlinedButton(
                        onClick = {
                            vm.save(
                                name = name,
                                gender = gender,
                                ageText = age,
                                bodyweightText = bodyweight,
                                heightText = height,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Save Profile") }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Best lifts", fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium)
                    Text("Bench: ${state.bestBenchLabel}", style = MaterialTheme.typography.bodyMedium)
                    Text("Squat: ${state.bestSquatLabel}", style = MaterialTheme.typography.bodyMedium)
                    Text("Deadlift: ${state.bestDeadliftLabel}", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onOpenPowerlifting) {
                            Icon(Icons.Filled.FitnessCenter, null)
                            Spacer(Modifier.height(0.dp))
                            Text("  Powerlifting Level")
                        }
                        OutlinedButton(onClick = onOpenCalculator) {
                            Icon(Icons.Filled.Calculate, null)
                            Text("  1RM Calculator")
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Theme", fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (m, l) ->
                            FilterChip(theme == m, { settingsVm.setTheme(m) }, { Text(l) })
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Data", fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium)
                    Text(
                        "CSV columns: date,title,bodyweight_kg,exercise,set_number,weight_kg,reps,is_warmup,duration_minutes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = !busy,
                            onClick = {
                                settingsVm.exportCsv()
                            },
                        ) {
                            Icon(Icons.Filled.FileDownload, null)
                            Text(" Export")
                        }
                        OutlinedButton(
                            enabled = !busy,
                            onClick = { importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) },
                        ) {
                            Icon(Icons.Filled.FileUpload, null)
                            Text(" Import")
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
