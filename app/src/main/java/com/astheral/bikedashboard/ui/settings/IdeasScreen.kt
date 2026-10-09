package com.astheral.bikedashboard.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astheral.bikedashboard.data.ideasFile
import com.astheral.bikedashboard.ui.components.AccentChip
import com.astheral.bikedashboard.ui.components.ConfirmDialog
import com.astheral.bikedashboard.ui.components.NumberFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The notebook is a plain text file in the app's own storage, saved shortly after you stop typing. */
class IdeasViewModel(application: Application) : AndroidViewModel(application) {
    private val file = ideasFile(application)
    private var pendingSave: Job? = null

    private val _text = MutableStateFlow<String?>(null)
    val text: StateFlow<String?> = _text.asStateFlow()

    init {
        viewModelScope.launch {
            _text.value = withContext(Dispatchers.IO) { if (file.exists()) file.readText() else "" }
        }
    }

    fun edit(text: String) {
        _text.value = text
        pendingSave?.cancel()
        pendingSave = viewModelScope.launch {
            delay(400)
            save(text)
        }
    }

    /** Empties the notebook and saves straight away. */
    fun eraseAll() {
        pendingSave?.cancel()
        _text.value = ""
        viewModelScope.launch { save("") }
    }

    private suspend fun save(text: String) = withContext(Dispatchers.IO) { file.writeText(text) }

    override fun onCleared() {
        // Leaving the page saves straight away rather than waiting.
        _text.value?.let { file.writeText(it) }
    }
}

/** The hidden ideas notebook: a place to jot feature ideas in plain text. */
@Composable
fun IdeasScreen(onBack: () -> Unit, viewModel: IdeasViewModel = viewModel()) {
    val text by viewModel.text.collectAsState()
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var confirmErase by remember { mutableStateOf(false) }
    val current = text ?: return

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Ideas", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { clipboard.setText(AnnotatedString(current)); copied = true }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy all")
            }
            IconButton(onClick = { confirmErase = true }, enabled = current.isNotEmpty()) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = "Erase all")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (copied) "Copied. Paste it anywhere you like." else "Just for you. Saved as you type.",
                Modifier.weight(1f).padding(start = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
            val lines = current.lines().count { it.isNotBlank() }
            AccentChip(if (lines == 1) "1 line" else "$lines lines")
        }
        OutlinedTextField(
            value = current,
            onValueChange = { viewModel.edit(it); copied = false },
            modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 12.dp),
            placeholder = { Text("- A widget for the home screen\n- Remind me when it's usually time to fill up\n- …") },
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = NumberFont),
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.tertiary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }

    if (confirmErase) {
        ConfirmDialog(
            title = "Erase all ideas?",
            message = "This clears the whole notebook. Copy it first if you might want it. This can't be undone.",
            confirm = "Erase all",
            onDismiss = { confirmErase = false },
            onConfirm = { confirmErase = false; copied = false; viewModel.eraseAll() },
        )
    }
}
