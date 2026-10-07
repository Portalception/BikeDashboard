package com.astheral.bikedashboard.ui.edit

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(fillUpId: Long, onDone: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(if (fillUpId == 0L) "Add fill-up" else "Edit fill-up") }) }) { padding ->
        Text("Coming soon", Modifier.padding(padding).padding(16.dp))
    }
}
