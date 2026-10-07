package com.astheral.bikedashboard.ui.dashboard

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
fun DashboardScreen(onAddFillUp: () -> Unit, onImport: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Dashboard") }) }, contentWindowInsets = NoInsets) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Text("Coming soon")
            Button(onClick = onAddFillUp) { Text("Add fill-up") }
        }
    }
}
