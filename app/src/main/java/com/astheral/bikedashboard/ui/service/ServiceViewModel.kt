package com.astheral.bikedashboard.ui.service

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.astheral.bikedashboard.diagnostics.CrashLogs
import com.astheral.bikedashboard.csv.SheetProblem
import com.astheral.bikedashboard.ui.count
import com.astheral.bikedashboard.csv.looksLikeServiceSheet
import com.astheral.bikedashboard.csv.readServiceSheet
import com.astheral.bikedashboard.data.AppDatabase
import com.astheral.bikedashboard.data.Currency
import com.astheral.bikedashboard.data.DueLevel
import com.astheral.bikedashboard.data.IntervalStatus
import com.astheral.bikedashboard.data.ServiceInterval
import com.astheral.bikedashboard.data.currentOdometer
import com.astheral.bikedashboard.data.status
import com.astheral.bikedashboard.data.withDefaults
import java.time.LocalDate
import com.astheral.bikedashboard.data.Profile
import com.astheral.bikedashboard.data.Profiles
import com.astheral.bikedashboard.data.ServiceRecord
import com.astheral.bikedashboard.data.ServiceCategory
import com.astheral.bikedashboard.data.nameOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class ServicingState(
    val profile: Profile,
    /** Newest first. */
    val records: List<ServiceRecord>,
    /** The highest odometer reading known, from fill-ups or services. */
    val odometerKm: Int?,
    /** The last exchange rate entered, for adding ringgit costs to SGD totals. */
    val rmPerSgd: Double?,
    /** Each switched-on interval and how close it is to being due, most urgent first. */
    val due: List<IntervalStatus> = emptyList(),
    /** Every category, in order. */
    val categories: List<ServiceCategory> = emptyList(),
) {
    fun last(type: String): ServiceRecord? = records.firstOrNull { it.type == type }

    /** The name of category [id]. */
    fun name(id: String): String = categories.nameOf(id)

    /** A cost in SGD, converting ringgit at the last rate (or leaving it out without one). */
    fun sgd(record: ServiceRecord): Double = when (record.currency) {
        Currency.SGD -> record.cost ?: 0.0
        Currency.MYR -> rmPerSgd?.let { (record.cost ?: 0.0) / it } ?: 0.0
    }
}

/** Switched-on intervals, most urgent first; ones that can't be worked out yet go last. */
fun dueList(
    intervals: List<ServiceInterval>,
    records: List<ServiceRecord>,
    odometer: Int?,
    categories: List<ServiceCategory>,
    today: LocalDate = LocalDate.now(),
) =
    intervals.filter { it.enabled }
        .map { status(it, records, odometer, today, categories.nameOf(it.type)) }
        .sortedWith(compareBy({ it.level == DueLevel.UNKNOWN }, { -it.used }))

/** A spreadsheet import waiting for you to confirm. */
data class PendingServiceImport(
    val fileName: String,
    val records: List<ServiceRecord>,
    val problems: List<SheetProblem>,
    val existing: Int,
    /** Kinds of work in the sheet that will be added as new categories. */
    val newCategories: List<ServiceCategory> = emptyList(),
)

class ServicingViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.get(application)
    private val services = db.serviceDao()
    private val categories = db.categoryDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ServicingState?> = Profiles.get(application).active.flatMapLatest { profile ->
        if (profile == null) {
            flowOf(null)
        } else {
            combine(
                services.observe(profile.id),
                db.fillUpDao().observeAll(profile.id),
                db.intervalDao().observe(profile.id),
                categories.observe(),
            ) { records, fills, saved, cats ->
                val odometer = currentOdometer(fills, records)
                ServicingState(
                    profile = profile,
                    records = records,
                    odometerKm = odometer,
                    rmPerSgd = fills.lastOrNull { it.rmPerSgd != null }?.rmPerSgd,
                    due = dueList(withDefaults(profile.id, saved, cats), records, odometer, cats),
                    categories = cats,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _pending = MutableStateFlow<PendingServiceImport?>(null)
    val pending: StateFlow<PendingServiceImport?> = _pending.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    /** Reads the chosen "Servicing Record" CSV and asks before replacing anything. */
    fun importFile(uri: Uri) {
        val profile = state.value?.profile ?: return
        viewModelScope.launch {
            try {
                val resolver = getApplication<Application>().contentResolver
                val (name, text) = withContext(Dispatchers.IO) {
                    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    } ?: "the file"
                    name to (resolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: throw IOException("can't read it"))
                }
                if (!looksLikeServiceSheet(text)) {
                    _message.value = "That isn't the servicing sheet. Its first row should have Date, Mileage (Km), Type, Description, Cost and Currency."
                    return@launch
                }
                val known = categories.getAll()
                val result = withContext(Dispatchers.Default) { readServiceSheet(text, profile.id, known) }
                if (result.records.isEmpty()) {
                    _message.value = "No services found in $name."
                    return@launch
                }
                _pending.value = PendingServiceImport(name, result.records, result.problems, services.getAll(profile.id).size, result.newCategories)
            } catch (e: IOException) {
                CrashLogs.error("Opening a servicing sheet to import", e)
                _message.value = "Couldn't open the file: ${e.message}"
            }
        }
    }

    fun confirmImport() {
        val p = _pending.value ?: return
        val profile = state.value?.profile ?: return
        _pending.value = null
        viewModelScope.launch {
            categories.insertAll(p.newCategories)
            services.replaceAll(profile.id, p.records)
            _message.value = "Imported ${count(p.records.size, "service")}."
        }
    }

    fun cancelImport() {
        _pending.value = null
    }
}
