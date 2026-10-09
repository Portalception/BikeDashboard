package com.astheral.bikedashboard.ui.edit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.astheral.bikedashboard.calc.totalCostSgd
import com.astheral.bikedashboard.calc.totalLitres
import com.astheral.bikedashboard.data.AppDatabase
import com.astheral.bikedashboard.data.Country
import com.astheral.bikedashboard.data.DEFAULT_TANK_LITRES
import com.astheral.bikedashboard.data.FillUp
import com.astheral.bikedashboard.data.Profile
import com.astheral.bikedashboard.data.Profiles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Live figures shown while answering. */
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
    val step: Step = Step.WHERE,
    /** +1 when moving forward through the cards, -1 when going back; picks the slide direction. */
    val direction: Int = 1,
    /** Message for the current card's answer, shown after trying to go on. */
    val error: String? = null,
    /** Things that look wrong, shown on the final card; saving is still allowed. */
    val warnings: List<String> = emptyList(),
    val preview: FormPreview = FormPreview(null, null, null, null, null),
    val previous: FillUp? = null,
    val commonRmPaid: List<Double> = emptyList(),
    val done: Boolean = false,
    /** The id of a newly added fill-up once it's saved, to show its summary. */
    val savedId: Long? = null,
) {
    val steps: List<Step> get() = stepsFor(form)
    val index: Int get() = steps.indexOf(step).coerceAtLeast(0)
}

class EditViewModel(application: Application, private val fillUpId: Long) : AndroidViewModel(application) {
    private val dao = AppDatabase.get(application).fillUpDao()
    private val profiles = Profiles.get(application)
    private var profile: Profile? = null
    private var base: FillUp? = null
    private var others: List<FillUp> = emptyList()

    private val _state = MutableStateFlow(EditState())
    val state: StateFlow<EditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = dao.getById(fillUpId)
            // An existing fill-up stays on its own bike; a new one goes on the bike being shown.
            profile = existing?.let { profiles.get(it.profileId) } ?: profiles.activeNow()
            val all = profile?.let { dao.getAll(it.id) }.orEmpty()
            others = all.filter { it.id != fillUpId }
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
            _state.value = refresh(
                EditState(
                    loading = false,
                    isNew = existing == null,
                    rateFromLast = existing == null && form.rate.isNotEmpty(),
                    form = form,
                    // Editing opens on the final check, where any answer can be tapped to change it.
                    step = if (existing == null) Step.WHERE else Step.REVIEW,
                    commonRmPaid = commonRmPaid(all),
                ),
            )
        }
    }

    fun edit(change: (FillUpForm) -> FillUpForm) = _state.update { refresh(it.copy(form = change(it.form), error = null)) }

    /** Picks where you filled up and moves straight on, since that's all the first card asks. */
    fun chooseCountry(country: Country) {
        edit { it.withCountry(country) }
        next()
    }

    /** Goes to the next card, unless this card's answer is missing or not a number. */
    fun next() {
        val s = _state.value
        val error = s.form.errorFor(s.step, base)
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        val nextStep = s.steps.getOrNull(s.index + 1) ?: return
        _state.update { refresh(it.copy(step = nextStep, direction = 1, error = null)) }
    }

    /** Goes back a card; returns false on the first card so the screen can close instead. */
    fun back(): Boolean {
        val s = _state.value
        if (s.index == 0) return false
        _state.update { it.copy(step = s.steps[s.index - 1], direction = -1, error = null) }
        return true
    }

    fun goTo(step: Step) = _state.update {
        refresh(it.copy(step = step, direction = if (it.steps.indexOf(step) < it.index) -1 else 1, error = null))
    }

    /** Saves from the final card. Any warnings are already showing there, so this always saves. */
    fun save() {
        val s = _state.value
        when (val result = s.form.toFillUp(base)) {
            is FormResult.Invalid -> s.form.firstStepWithError(base)?.let { step ->
                _state.update { refresh(it.copy(step = step, direction = -1, error = s.form.errorFor(step, base))) }
            }
            is FormResult.Ok -> viewModelScope.launch {
                val newId = if (base == null) {
                    dao.insert(result.fillUp.copy(profileId = profile?.id ?: 1))
                } else {
                    dao.update(result.fillUp)
                    null
                }
                _state.update { it.copy(done = true, savedId = newId) }
            }
        }
    }

    private fun refresh(s: EditState): EditState {
        val fill = (s.form.toFillUp(base) as? FormResult.Ok)?.fillUp
        return s.copy(
            preview = preview(s.form, fill),
            previous = previousFor(s.form),
            warnings = fill?.let {
                warningsFor(it, others, LocalDate.now(), profile?.tankLitres ?: DEFAULT_TANK_LITRES)
            }.orEmpty(),
        )
    }

    private fun previousFor(form: FillUpForm) =
        others.filter { it.date <= form.date }.maxWithOrNull(compareBy({ it.date }, { it.odometerKm }))

    private fun preview(form: FillUpForm, fill: FillUp?): FormPreview {
        val rate = parseNumber(form.rate)
        val charged = parseNumber(form.rmCharged)
        val myCost = if (rate != null && rate > 0 && charged != null) charged / rate else null
        val odometer = form.odometer.trim().toIntOrNull()
        val previous = previousFor(form)
        val distance = if (odometer != null && previous != null) odometer - previous.odometerKm else null
        return FormPreview(
            distanceKm = distance,
            kmPerLitre = if (fill != null && distance != null && distance > 0) distance / fill.totalLitres else null,
            totalCostSgd = fill?.totalCostSgd,
            pricePerLitre = fill?.let { it.totalCostSgd / it.totalLitres },
            myCostSgd = myCost,
        )
    }
}
