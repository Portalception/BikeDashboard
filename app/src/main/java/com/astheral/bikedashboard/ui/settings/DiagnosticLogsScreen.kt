package com.astheral.bikedashboard.ui.settings

import android.app.Application
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astheral.bikedashboard.diagnostics.CrashLogs
import com.astheral.bikedashboard.diagnostics.LogKind
import com.astheral.bikedashboard.diagnostics.SavedLog
import com.astheral.bikedashboard.ui.components.AccentChip
import com.astheral.bikedashboard.ui.components.ConfirmDialog
import com.astheral.bikedashboard.ui.components.GroupCard
import com.astheral.bikedashboard.ui.components.NumberFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DiagnosticLogsViewModel(application: Application) : AndroidViewModel(application) {
    private val _logs = MutableStateFlow<List<SavedLog>?>(null)
    val logs: StateFlow<List<SavedLog>?> = _logs.asStateFlow()

    init {
        reload()
    }

    private fun reload() {
        viewModelScope.launch {
            _logs.value = withContext(Dispatchers.IO) { CrashLogs.all(getApplication()) }
        }
    }

    fun delete(log: SavedLog) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { CrashLogs.delete(log) }
            reload()
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { CrashLogs.deleteAll(getApplication()) }
            reload()
        }
    }
}

/** The hidden diagnostic logs page: saved crash reports to copy or share when asking for a fix. */
@Composable
fun DiagnosticLogsScreen(onBack: () -> Unit, viewModel: DiagnosticLogsViewModel = viewModel()) {
    val logs by viewModel.logs.collectAsState()
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Diagnostic Logs", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { confirmDeleteAll = true }, enabled = !logs.isNullOrEmpty()) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = "Delete All")
            }
        }
        Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Saved on this phone only. Share a log and paste it into Claude to get the bug fixed.",
                Modifier.weight(1f).padding(start = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
            logs?.let { Spacer(Modifier.width(8.dp)); AccentChip(if (it.size == 1) "1 log" else "${it.size} logs") }
        }
        val list = logs ?: return@Column
        if (list.isEmpty()) {
            GroupCard(contentPadding = 20.dp) {
                Text("No Logs Yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "If the app ever closes unexpectedly or hits an error, a report shows up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(list, key = { it.file.name }) { log ->
                val open = expanded == log.file.name
                LogCard(
                    log = log,
                    open = open,
                    onToggle = { expanded = if (open) null else log.file.name },
                    onDelete = { viewModel.delete(log) },
                )
            }
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }

    if (confirmDeleteAll) {
        ConfirmDialog(
            title = "Delete all logs?",
            message = "Every saved crash and error report will be removed. This can't be undone.",
            confirm = "Delete All",
            onDismiss = { confirmDeleteAll = false },
            onConfirm = { confirmDeleteAll = false; viewModel.deleteAll() },
        )
    }
}

@Composable
private fun LogCard(log: SavedLog, open: Boolean, onToggle: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val crash = log.summary.kind == LogKind.CRASH
    GroupCard(onClick = onToggle, edge = if (crash) MaterialTheme.colorScheme.error.copy(alpha = 0.6f) else null) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (crash) Icons.Filled.BugReport else Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = if (crash) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    if (crash) "Crash" else "Error",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    log.summary.time,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = NumberFont,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                log.summary.summary,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = NumberFont,
                maxLines = if (open) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AnimatedVisibility(open) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectionContainer {
                        Text(
                            log.text,
                            Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = NumberFont,
                            softWrap = false,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { clipboard.setText(AnnotatedString(log.text)); copied = true }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (copied) "Copied" else "Copy")
                        }
                        TextButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_SUBJECT, "Bike Dashboard diagnostic log")
                                .putExtra(Intent.EXTRA_TEXT, log.text)
                            context.startActivity(Intent.createChooser(send, "Share Log"))
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Share")
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete Log", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
