package com.astheral.bikedashboard.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.astheral.bikedashboard.ui.NoInsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onImport: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }, contentWindowInsets = NoInsets) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Button(onClick = onImport) { Text("Import CSV") }
        }
    }
}
