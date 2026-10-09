package com.astheral.bikedashboard.ui.importer

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.astheral.bikedashboard.diagnostics.CrashLogs
import com.astheral.bikedashboard.csv.SheetMismatch
import com.astheral.bikedashboard.csv.SheetProblem
import com.astheral.bikedashboard.csv.checkAgainstSheet
import com.astheral.bikedashboard.csv.looksLikeBackup
import com.astheral.bikedashboard.csv.looksLikeSpreadsheet
import com.astheral.bikedashboard.csv.readBackup
import com.astheral.bikedashboard.csv.readSpreadsheet
import com.astheral.bikedashboard.data.AppDatabase
import com.astheral.bikedashboard.data.Country
import com.astheral.bikedashboard.data.FillUp
import com.astheral.bikedashboard.data.Backups
import com.astheral.bikedashboard.data.FullBackup
import com.astheral.bikedashboard.data.Profiles
import com.astheral.bikedashboard.data.looksLikeFullBackup
import com.astheral.bikedashboard.data.readFullBackup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

sealed interface ImportState {
    data object Idle : ImportState
    data object Reading : ImportState
    data class Preview(
        val fileName: String,
        val fillUps: List<FillUp>,
        val firstDate: LocalDate,
        val lastDate: LocalDate,
        val countByCountry: Map<Country, Int>,
        val problems: List<SheetProblem>,
        val mismatches: List<SheetMismatch>,
        /** Fill-ups already on this bike, which the import will replace. */
        val existingCount: Int,
        val profileName: String = "",
        /** True for the app's own backup format, which needs no check against a spreadsheet. */
        val isBackup: Boolean = false,
    ) : ImportState
    /** A full backup: restoring it replaces every bike and setting. */
    data class FullPreview(
        val fileName: String,
        val backup: FullBackup,
        val fillUpCount: Int,
        val existingBikes: Int,
        val existingFillUps: Int,
    ) : ImportState
    data object Saving : ImportState
    data class Done(val count: Int, val restoredAll: Boolean = false) : ImportState
    data class Failed(val message: String, val problems: List<SheetProblem> = emptyList()) : ImportState
}

class ImportViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.get(application).fillUpDao()
    private val profiles = Profiles.get(application)

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    /** Reads the chosen file and shows a preview. Nothing is saved yet. */
    fun fileChosen(uri: Uri) {
        _state.value = ImportState.Reading
        viewModelScope.launch {
            _state.value = try {
                val (name, text) = withContext(Dispatchers.IO) { fileName(uri) to readText(uri) }
                preview(name, text)
            } catch (e: IOException) {
                CrashLogs.error("Opening a file to import", e)
                ImportState.Failed("Couldn't open the file: ${e.message}")
            }
        }
    }

    private suspend fun preview(fileName: String, text: String): ImportState {
        if (looksLikeFullBackup(text)) return fullPreview(fileName, text)
        if (looksLikeBackup(text)) return backupPreview(fileName, text)
        if (!looksLikeSpreadsheet(text)) {
            return ImportState.Failed(
                "This doesn't look like a Bike Dashboard backup or your fuel spreadsheet. " +
                    "The first row should be the column names.",
            )
        }
        val result = withContext(Dispatchers.Default) { readSpreadsheet(text) }
        if (result.rows.isEmpty()) {
            return ImportState.Failed("No fill-ups found in $fileName.", result.problems)
        }
        val fillUps = result.rows.map { it.fillUp }
        return ImportState.Preview(
            fileName = fileName,
            fillUps = fillUps,
            firstDate = fillUps.minOf { it.date },
            lastDate = fillUps.maxOf { it.date },
            countByCountry = fillUps.groupingBy { it.country }.eachCount(),
            problems = result.problems,
            mismatches = withContext(Dispatchers.Default) { checkAgainstSheet(result.rows) },
            existingCount = profiles.activeNow()?.let { dao.getAll(it.id).size } ?: 0,
            profileName = profiles.activeNow()?.name.orEmpty(),
        )
    }

    private suspend fun backupPreview(fileName: String, text: String): ImportState {
        val result = withContext(Dispatchers.Default) { readBackup(text) }
        val fillUps = result.fillUps
        if (fillUps.isEmpty()) return ImportState.Failed("No fill-ups found in $fileName.", result.problems)
        return ImportState.Preview(
            fileName = fileName,
            fillUps = fillUps,
            firstDate = fillUps.minOf { it.date },
            lastDate = fillUps.maxOf { it.date },
            countByCountry = fillUps.groupingBy { it.country }.eachCount(),
            problems = result.problems,
            mismatches = emptyList(),
            existingCount = profiles.activeNow()?.let { dao.getAll(it.id).size } ?: 0,
            profileName = profiles.activeNow()?.name.orEmpty(),
            isBackup = true,
        )
    }

    private suspend fun fullPreview(fileName: String, text: String): ImportState {
        val backup = try {
            withContext(Dispatchers.Default) { readFullBackup(text) }
        } catch (e: IllegalArgumentException) {
            return ImportState.Failed(e.message ?: "Couldn't read the backup.")
        }
        val bikes = profiles.all.first()
        return ImportState.FullPreview(
            fileName = fileName,
            backup = backup,
            fillUpCount = backup.bikes.sumOf { it.fillUps.size },
            existingBikes = bikes.size,
            existingFillUps = bikes.sumOf { dao.getAll(it.id).size },
        )
    }

    /** Replaces every bike, setting and the ideas notebook with the backup's. */
    fun confirmFull() {
        val preview = _state.value as? ImportState.FullPreview ?: return
        _state.value = ImportState.Saving
        viewModelScope.launch {
            Backups(getApplication()).restore(preview.backup)
            _state.value = ImportState.Done(preview.fillUpCount, restoredAll = true)
        }
    }

    /** Replaces everything in the app with the previewed fill-ups. */
    fun confirm() {
        val preview = _state.value as? ImportState.Preview ?: return
        _state.value = ImportState.Saving
        viewModelScope.launch {
            val profile = profiles.activeNow() ?: return@launch
            dao.replaceAll(profile.id, preview.fillUps)
            _state.value = ImportState.Done(preview.fillUps.size)
        }
    }

    fun reset() {
        _state.value = ImportState.Idle
    }

    private fun readText(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        return resolver.openInputStream(uri)?.use { it.bufferedReader(Charsets.UTF_8).readText() }
            ?: throw IOException("the file is empty or can't be read")
    }

    private fun fileName(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.let { return it }
        }
        return uri.lastPathSegment ?: "the file"
    }
}
