package com.lolmax.app.ui.profile

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lolmax.app.domain.OneRepMax
import com.lolmax.app.ui.util.Units
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    onBack: () -> Unit,
    @Suppress("unused") factory: com.lolmax.app.di.AppViewModelFactory,
) {
    var weightText by remember { mutableStateOf("100") }
    var repsText by remember { mutableStateOf("5") }
    var unit by remember { mutableStateOf("lb") }

    val weight = weightText.toDoubleOrNull() ?: 0.0
    val reps = (repsText.toIntOrNull() ?: 0).coerceAtLeast(1)
    val kg = Units.displayToKg(weight, unit)
    val oneRm = OneRepMax.estimate(kg, reps)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("1RM Calculator") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = { Text("Weight ($unit)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = repsText,
                    onValueChange = { repsText = it },
                    label = { Text("Reps") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.FilterChip(
                    selected = unit == "kg",
                    onClick = { unit = "kg" },
                    label = { Text("kg") },
                )
                androidx.compose.material3.FilterChip(
                    selected = unit == "lb",
                    onClick = { unit = "lb" },
                    label = { Text("lb") },
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Estimated 1-Rep Max", style = MaterialTheme.typography.labelLarge)
                    Text(
                        Units.kgToDisplay(oneRm, unit),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Text("Working weights (% of estimated 1RM)", fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp)) {
                    listOf(95, 90, 85, 80, 75, 70, 65, 60, 55, 50).forEach { pct ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text("$pct%", Modifier.weight(1f))
                            Text(Units.kgToDisplay(oneRm * pct / 100.0, unit))
                        }
                    }
                }
            }

            Text("Rep max table (from your 1RM)", fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp)) {
                    for (r in 1..15) {
                        val w = oneRm * OneRepMax.percentOf1rm(r) / 100.0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text("$r rep" + if (r > 1) "s" else "", Modifier.weight(1f))
                            Text(
                                String.format(Locale.US, "%.1f%% ", OneRepMax.percentOf1rm(r)) +
                                    Units.kgToDisplay(w, unit),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
