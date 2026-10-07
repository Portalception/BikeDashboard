package com.astheral.bikedashboard.ui.edit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.astheral.bikedashboard.calc.totalCostSgd
import com.astheral.bikedashboard.calc.totalLitres
import com.astheral.bikedashboard.data.AppDatabase
import com.astheral.bikedashboard.data.Country
import com.astheral.bikedashboard.data.FillUp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Live figures shown under the form while typing. */
data class FormPreview(
    val distanceKm: Int?,
    val kmPerLitre: Double?,
    val totalCostSgd: Double?,
    val pricePerLitre: Double?,
    /** The Malaysian part's cost in SGD (pump charge ÷ rate). */
    val myCostSgd: Double?,
)

data class EditState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    /** The rate was filled in from the last fill-up that had one. */
    val rateFromLast: Boolean = false,
    val form: FillUpForm = FillUpForm(date = LocalDate.now(), country = Country.MY),
    val errors: Map<Field, String> = emptyMap(),
    /** Set when saving found things that look wrong; the screen asks whether to save anyway. */
    val pendingWarnings: List<String> = emptyList(),
    val preview: FormPreview = FormPreview(null, null, null, null, null),
    val done: Boolean = false,
)

class EditViewModel(application: Application, private val fillUpId: Long) : AndroidViewModel(application) {
    private val dao = AppDatabase.get(application).fillUpDao()
    private var base: FillUp? = null
    private var others: List<FillUp> = emptyList()

    private val _state = MutableStateFlow(EditState())
    val state: StateFlow<EditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val all = dao.getAll()
            others = all.filter { it.id != fillUpId }
            val existing = all.firstOrNull { it.id == fillUpId }
            base = existing
            val form = if (existing != null) {
                FillUpForm.from(existing)
            } else {
                // New fill-up: today, same country as last time, last exchange rate.
                FillUpForm(
                    date = LocalDate.now(),
                    country = all.lastOrNull()?.country ?: Country.MY,
                    rate = FillUpForm.rateText(dao.latestRmPerSgd()),
                )
            }
            _state.value = EditState(
                loading = false,
                isNew = existing == null,
                rateFromLast = existing == null && form.rate.isNotEmpty(),
                form = form,
                preview = preview(form),
            )
        }
    }

    fun edit(change: (FillUpForm) -> FillUpForm) {
        _state.update {
            val form = change(it.form)
            // Clear an error once its field is being fixed.
            it.copy(form = form, errors = it.errors.filterKeys { field -> fieldText(it.form, field) == fieldText(form, field) }, preview = preview(form))
        }
    }

    /** Saves, unless something looks wrong and [anyway] is false: then the warnings are shown first. */
    fun save(anyway: Boolean = false) {
        val current = _state.value
        when (val result = current.form.toFillUp(base)) {
            is FormResult.Invalid -> _state.update { it.copy(errors = result.errors, pendingWarnings = emptyList()) }
            is FormResult.Ok -> {
                val warnings = warningsFor(result.fillUp, others, LocalDate.now())
                if (warnings.isNotEmpty() && !anyway) {
                    _state.update { it.copy(errors = emptyMap(), pendingWarnings = warnings) }
                    return
                }
                viewModelScope.launch {
                    if (base == null) dao.insert(result.fillUp) else dao.update(result.fillUp)
                    _state.update { it.copy(pendingWarnings = emptyList(), done = true) }
                }
            }
        }
    }

    fun dismissWarnings() = _state.update { it.copy(pendingWarnings = emptyList()) }

    fun delete() {
        val existing = base ?: return
        viewModelScope.launch {
            dao.delete(existing)
            _state.update { it.copy(done = true) }
        }
    }

    private fun preview(form: FillUpForm): FormPreview {
        val fill = (form.toFillUp(base) as? FormResult.Ok)?.fillUp
        val rate = parseNumber(form.rate)
        val charged = parseNumber(form.rmCharged)
        val myCost = if (rate != null && rate > 0 && charged != null) charged / rate else null
        val odometer = form.odometer.trim().toIntOrNull()
        val previous = others.filter { it.date <= form.date }.maxWithOrNull(compareBy({ it.date }, { it.odometerKm }))
        val distance = if (odometer != null && previous != null) odometer - previous.odometerKm else null
        return FormPreview(
            distanceKm = distance,
            kmPerLitre = if (fill != null && distance != null && distance > 0) distance / fill.totalLitres else null,
            totalCostSgd = fill?.totalCostSgd,
            pricePerLitre = fill?.let { it.totalCostSgd / it.totalLitres },
            myCostSgd = myCost,
        )
    }

    private fun fieldText(form: FillUpForm, field: Field): String = when (field) {
        Field.ODOMETER -> form.odometer
        Field.SG_LITRES -> form.sgLitres
        Field.SG_COST -> form.sgCost
        Field.MY_LITRES -> form.myLitres
        Field.RM_PAID -> form.rmPaid
        Field.RATE -> form.rate
        Field.RM_CHARGED -> form.rmCharged
        Field.LEGACY_MY_COST -> form.legacyMyCost
        Field.UNSPLIT_LITRES -> form.unsplitLitres
        Field.UNSPLIT_COST -> form.unsplitCost
        Field.BARS_LEFT -> form.barsLeft
        Field.RESERVE_KM -> form.reserveKm
    }
}
