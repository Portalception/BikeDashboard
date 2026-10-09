package com.astheral.bikedashboard.calc

/** The number cards on the dashboard, in their default order. */
enum class DashCard {
    ECONOMY, KM_PER_DAY, COST_PER_DAY, COST_PER_KM, PRICE, SAVINGS;

    /** Which way is good news for this number. How far you ride is neither. */
    val better: Better get() = when (this) {
        ECONOMY, SAVINGS -> Better.HIGHER
        PRICE, COST_PER_KM, COST_PER_DAY -> Better.LOWER
        KM_PER_DAY -> Better.NEITHER
    }

    /** This number for one fill-up, in the app's base unit. */
    fun of(s: FillUpStats): Double? = when (this) {
        ECONOMY -> s.kmPerLitre
        KM_PER_DAY -> s.kmPerDay
        COST_PER_DAY -> s.costPerDay
        COST_PER_KM -> s.costPerKm
        PRICE -> s.pricePerLitre
        SAVINGS -> s.savingsSgd
    }

    /** This number over a period, worked out from totals. */
    fun of(p: PeriodSummary): Double? = when (this) {
        ECONOMY -> p.kmPerLitre
        KM_PER_DAY -> p.kmPerDay
        COST_PER_DAY -> p.costPerDay
        COST_PER_KM -> p.costPerKm
        PRICE -> p.pricePerLitre
        SAVINGS -> p.savingsSgd
    }
}

/**
 * The charts on the dashboard, in their default order. The first three were there before
 * charts could be arranged; the rest start hidden so the dashboard doesn't get too long.
 */
enum class DashChart(val shownByDefault: Boolean) {
    ECONOMY(true), PRICE(true), SPEND(true), KM_PER_DAY(false), COST_PER_DAY(false), COST_PER_KM(false), SAVINGS(false);
}

/**
 * Which dashboard cards show, and in what order. [order] always holds every card exactly once;
 * [hidden] never holds all of them, so at least one card stays on the dashboard.
 */
data class CardLayout(
    val order: List<DashCard> = DashCard.entries.toList(),
    val hidden: Set<DashCard> = emptySet(),
) {
    /** The cards to show, in order. */
    val visible: List<DashCard> get() = order.filter { it !in hidden }

    /** Moves [card] to position [to] (clamped to the list). */
    fun move(card: DashCard, to: Int): CardLayout = copy(order = order.moved(card, to))

    /** Shows or hides [card]; hiding the last visible card does nothing. */
    fun setShown(card: DashCard, shown: Boolean): CardLayout = when {
        shown -> copy(hidden = hidden - card)
        canHide(card) -> copy(hidden = hidden + card)
        else -> this
    }

    /** False for the only card still showing. */
    fun canHide(card: DashCard): Boolean = card in hidden || visible.size > 1

    /** For saving: the order, with "!" in front of hidden cards, e.g. "ECONOMY,!KM_PER_DAY,PRICE". */
    fun format(): String = formatLayout(order, hidden)

    val isDefault: Boolean get() = this == CardLayout()

    companion object {
        /**
         * Reads [format]'s text. Unknown or repeated names are skipped, and cards it doesn't mention
         * (new ones from a later version) are added at the end, shown, in their default order.
         * If every card would be hidden, they're all shown.
         */
        fun parse(text: String?): CardLayout {
            if (text.isNullOrBlank()) return CardLayout()
            val (order, hidden) = parseLayout(text, DashCard.entries) { false }
            return CardLayout(order, if (hidden.size >= order.size) emptySet() else hidden)
        }
    }
}

/**
 * Which dashboard charts show, and in what order. Unlike the cards, every chart may be hidden.
 * [order] always holds every chart exactly once.
 */
data class ChartLayout(
    val order: List<DashChart> = DashChart.entries.toList(),
    val hidden: Set<DashChart> = DashChart.entries.filter { !it.shownByDefault }.toSet(),
) {
    val visible: List<DashChart> get() = order.filter { it !in hidden }

    fun move(chart: DashChart, to: Int): ChartLayout = copy(order = order.moved(chart, to))

    fun setShown(chart: DashChart, shown: Boolean): ChartLayout =
        copy(hidden = if (shown) hidden - chart else hidden + chart)

    /** For saving, in the same form as [CardLayout.format]. */
    fun format(): String = formatLayout(order, hidden)

    val isDefault: Boolean get() = this == ChartLayout()

    companion object {
        /**
         * Reads [format]'s text like [CardLayout.parse]: unknown or repeated names are skipped,
         * and charts it doesn't mention are added at the end in their default order and state.
         */
        fun parse(text: String?): ChartLayout {
            if (text.isNullOrBlank()) return ChartLayout()
            val (order, hidden) = parseLayout(text, DashChart.entries) { !it.shownByDefault }
            return ChartLayout(order, hidden)
        }
    }
}

/** [item] moved to position [to] (clamped to the list); the same list if it's not there. */
private fun <T> List<T>.moved(item: T, to: Int): List<T> {
    val from = indexOf(item)
    if (from < 0) return this
    val target = to.coerceIn(0, lastIndex)
    if (target == from) return this
    val list = toMutableList()
    list.removeAt(from)
    list.add(target, item)
    return list
}

private fun <T : Enum<T>> formatLayout(order: List<T>, hidden: Set<T>): String =
    order.joinToString(",") { if (it in hidden) "!${it.name}" else it.name }

/**
 * The order and hidden set from [formatLayout]'s text. Unknown or repeated names are skipped;
 * entries it doesn't mention are appended in [all]'s order, hidden when [hiddenWhenNew] says so.
 */
private fun <T : Enum<T>> parseLayout(text: String, all: List<T>, hiddenWhenNew: (T) -> Boolean): Pair<List<T>, Set<T>> {
    val order = mutableListOf<T>()
    val hidden = mutableSetOf<T>()
    text.split(',').forEach { raw ->
        val token = raw.trim()
        val off = token.startsWith("!")
        val item = all.firstOrNull { it.name == token.removePrefix("!") } ?: return@forEach
        if (item in order) return@forEach
        order += item
        if (off) hidden += item
    }
    all.filter { it !in order }.forEach {
        order += it
        if (hiddenWhenNew(it)) hidden += it
    }
    return order to hidden
}
