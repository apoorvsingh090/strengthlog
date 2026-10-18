package com.lolmax.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.lolmax.app.di.AppViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PowerliftingScreen(
    onBack: () -> Unit,
    factory: AppViewModelFactory,
) {
    val vm: PowerliftingViewModel = viewModel(factory = factory)
    val state by vm.uiState.collectAsState()

    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Powerlifting Level") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = vm::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Recalculate")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(
                    Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(state.levelDisplay, style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold)
                    if (state.percentileLabel.isNotBlank()) {
                        Text(state.percentileLabel, style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Wilks ${state.wilksLabel}", style = MaterialTheme.typography.titleLarge)
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Best lifts (last 12 weeks)", fontWeight = FontWeight.SemiBold)
                    Row {
                        Text("Squat", Modifier.weight(1f))
                        Text(state.squatLabel)
                    }
                    Row {
                        Text("Bench Press", Modifier.weight(1f))
                        Text(state.benchLabel)
                    }
                    Row {
                        Text("Deadlift", Modifier.weight(1f))
                        Text(state.deadliftLabel)
                    }
                    Row {
                        Text("Total", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text(state.totalLabel, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text(
                "Powerlifting Level recalculates when you log a squat, bench or deadlift that is your highest 1RM in the last 12 weeks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
