package com.astheral.bikedashboard.ui.edit

import com.astheral.bikedashboard.data.Country
import com.astheral.bikedashboard.data.FillUp

/** One question card in the add/edit flow. [fields] are the form fields it fills in. */
enum class Step(vararg val fields: Field) {
    DATE,
    WHERE,
    RM_PAID(Field.RM_PAID),
    RATE(Field.RATE),
    RM_CHARGED(Field.RM_CHARGED),
    MY_LITRES(Field.MY_LITRES),
    LEGACY_MY_COST(Field.LEGACY_MY_COST),
    UNSPLIT_LITRES(Field.UNSPLIT_LITRES),
    UNSPLIT_COST(Field.UNSPLIT_COST),
    SG_PAID(Field.SG_COST),
    SG_LITRES(Field.SG_LITRES),
    ODOMETER(Field.ODOMETER),
    FUEL_LEFT(Field.BARS_LEFT, Field.RESERVE_KM),
    REVIEW,
}

/**
 * The cards to show for this form, in pump order: when and where, then for Malaysia what you paid,
 * the rate, what the pump charged and the litres, then Singapore's part, then the odometer,
 * fuel left and a final check. (Only old fill-ups can have both countries.)
 */
fun stepsFor(form: FillUpForm): List<Step> = buildList {
    add(Step.DATE)
    add(Step.WHERE)
    if (form.country == Country.MIXED && form.legacyMixed) {
        add(Step.UNSPLIT_LITRES)
        add(Step.UNSPLIT_COST)
    }
    if (form.hasMyPart) {
        if (form.country == Country.MY && form.legacyMy) {
            add(Step.MY_LITRES)
            add(Step.LEGACY_MY_COST)
        } else {
            addAll(listOf(Step.RM_PAID, Step.RATE, Step.RM_CHARGED, Step.MY_LITRES))
        }
    }
    if (form.hasSgPart) {
        add(Step.SG_PAID)
        add(Step.SG_LITRES)
    }
    addAll(listOf(Step.ODOMETER, Step.FUEL_LEFT, Step.REVIEW))
}

/** The card that asks "Full Tank?": the last one asking for litres. */
fun fullTankStep(form: FillUpForm): Step? =
    stepsFor(form).lastOrNull { it == Step.MY_LITRES || it == Step.SG_LITRES || it == Step.UNSPLIT_LITRES }

/** What's wrong with this card's answer, if anything, so the flow can stop on it. */
fun FillUpForm.errorFor(step: Step, base: FillUp?): String? {
    if (step.fields.isEmpty()) return null
    val errors = (toFillUp(base) as? FormResult.Invalid)?.errors ?: return null
    return step.fields.firstNotNullOfOrNull { errors[it] }
}

/** The first card whose answer is missing or wrong, e.g. to jump back to from the final check. */
fun FillUpForm.firstStepWithError(base: FillUp?): Step? =
    stepsFor(this).firstOrNull { errorFor(it, base) != null }

/** RM amounts you pay most often (larger first on a tie), for one-tap buttons on the "How much did you pay?" card. */
fun commonRmPaid(fillUps: List<FillUp>, count: Int = 4): List<Double> =
    fillUps.mapNotNull { it.rmPaid }
        .groupingBy { it }.eachCount()
        .entries.sortedWith(compareByDescending<Map.Entry<Double, Int>> { it.value }.thenByDescending { it.key })
        .take(count)
        .map { it.key }
        .sorted()
